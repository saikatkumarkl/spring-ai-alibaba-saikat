/*
 * Copyright 2025-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.core.rag.index;

import com.alibaba.cloud.ai.studio.core.rag.OpenSearchUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Factory that creates OpenSearch indices with enforced schemas.
 *
 * <p>This is the <strong>only sanctioned way</strong> to create knowledge base indices.
 * All connectors (CMIS, REST API, future connectors) must use this factory to create
 * their indices. The factory:</p>
 * <ol>
 *   <li>Validates the index name follows the naming convention
 *       ({name}_document, {name}_authority, {name}_rag)</li>
 *   <li>Applies the correct schema from {@link KnowledgeIndexSchema}</li>
 *   <li>Creates the index in OpenSearch via REST API</li>
 * </ol>
 *
 * <p><strong>How enforcement works:</strong> Connector implementations do NOT create
 * indices directly. They call these factory methods which validate and apply the schema.
 * Any attempt to create an index with on invalid name is rejected with an exception.</p>
 */
@Slf4j
@Component
public class KnowledgeIndexSchemaFactory {

	private final RestTemplate restTemplate;

	/**
	 * Vector storage mode for RAG indices.
	 * <ul>
	 *   <li>{@code on_disk} — binary-quantised search, auto-rescoring from disk (low RAM)</li>
	 *   <li>{@code in_memory} — full-precision vectors in RAM (fastest, high RAM)</li>
	 *   <li><em>empty</em> — OpenSearch engine default</li>
	 * </ul>
	 * Override via env var {@code RAG_VECTOR_MODE}.
	 */
	@Value("${rag.vector.mode:}")
	private String ragVectorMode;

	/**
	 * Compression level for binary quantisation.
	 * Only meaningful when {@code rag.vector.mode=on_disk}.
	 * Typical values: {@code 32x} (binary), {@code 16x}, {@code 8x}.
	 * Override via env var {@code RAG_COMPRESSION_LEVEL}.
	 */
	@Value("${rag.vector.compression-level:}")
	private String ragCompressionLevel;

	/**
	 * Number of primary shards for knowledge base indices.
	 * Override via env var {@code RAG_OPENSEARCH_SHARDS}.
	 * Default is 1 (suitable for development / single-node).
	 * For production with billions of documents, increase proportionally
	 * (guideline: ~25 GB per shard, max ~50 shards per index).
	 */
	@Value("${rag.opensearch.shards:" + KnowledgeIndexSchema.DEFAULT_SHARDS + "}")
	private int shards;

	/**
	 * Number of replica shards for knowledge base indices.
	 * Override via env var {@code RAG_OPENSEARCH_REPLICAS}.
	 * Default is 0 (suitable for single-node development).
	 * For production HA, set to at least 1.
	 */
	@Value("${rag.opensearch.replicas:" + KnowledgeIndexSchema.DEFAULT_REPLICAS + "}")
	private int replicas;

	public KnowledgeIndexSchemaFactory() {
		this.restTemplate = new RestTemplate();
	}

	/**
	 * Create a document index for a knowledge base.
	 * Index name must end with {@code _document}.
	 *
	 * @param opensearchUrl the OpenSearch base URL
	 * @param username      OpenSearch username (may be blank for no auth)
	 * @param password      OpenSearch password
	 * @param indexName     the index name (must end with _document)
	 */
	public void createDocumentIndex(String opensearchUrl, String username, String password, String indexName) {
		KnowledgeIndexSchema.validateIndexName(indexName);
		if (!indexName.endsWith(KnowledgeIndexSchema.DOCUMENT_SUFFIX)) {
			throw new IllegalArgumentException(
					"Document index name must end with '" + KnowledgeIndexSchema.DOCUMENT_SUFFIX
							+ "', got: " + indexName);
		}
		createIndex(opensearchUrl, username, password, indexName,
				KnowledgeIndexSchema.documentIndexMapping(shards, replicas));
	}

	/**
	 * Create an authority index for a knowledge base.
	 * Index name must end with {@code _authority}.
	 *
	 * @param opensearchUrl the OpenSearch base URL
	 * @param username      OpenSearch username
	 * @param password      OpenSearch password
	 * @param indexName     the index name (must end with _authority)
	 */
	public void createAuthorityIndex(String opensearchUrl, String username, String password, String indexName) {
		KnowledgeIndexSchema.validateIndexName(indexName);
		if (!indexName.endsWith(KnowledgeIndexSchema.AUTHORITY_SUFFIX)) {
			throw new IllegalArgumentException(
					"Authority index name must end with '" + KnowledgeIndexSchema.AUTHORITY_SUFFIX
							+ "', got: " + indexName);
		}
		createIndex(opensearchUrl, username, password, indexName,
				KnowledgeIndexSchema.authorityIndexMapping(shards, replicas));
	}

	/**
	 * Create a RAG index for a knowledge base.
	 * Index name must end with {@code _rag}.
	 *
	 * @param opensearchUrl    the OpenSearch base URL
	 * @param username         OpenSearch username
	 * @param password         OpenSearch password
	 * @param indexName        the index name (must end with _rag)
	 * @param embeddingDimension vector embedding dimension (e.g. 1024)
	 */
	public void createRagIndex(String opensearchUrl, String username, String password,
			String indexName, int embeddingDimension) {
		KnowledgeIndexSchema.validateIndexName(indexName);
		if (!indexName.endsWith(KnowledgeIndexSchema.RAG_SUFFIX)) {
			throw new IllegalArgumentException(
					"RAG index name must end with '" + KnowledgeIndexSchema.RAG_SUFFIX
							+ "', got: " + indexName);
		}
		String effectiveMode = (ragVectorMode != null && !ragVectorMode.isBlank()) ? ragVectorMode : null;
		String effectiveCompression = (ragCompressionLevel != null && !ragCompressionLevel.isBlank()) ? ragCompressionLevel : null;
		log.info("RAG vector config: mode={}, compression={}, shards={}, replicas={}",
				effectiveMode, effectiveCompression, shards, replicas);
		String mapping = KnowledgeIndexSchema.ragIndexMapping(
				embeddingDimension, effectiveMode, effectiveCompression, shards, replicas);
		createIndex(opensearchUrl, username, password, indexName, mapping);
	}

	/**
	 * Delete an index from OpenSearch.
	 *
	 * @param opensearchUrl the OpenSearch base URL
	 * @param username      OpenSearch username
	 * @param password      OpenSearch password
	 * @param indexName     the index name to delete
	 */
	public void deleteIndex(String opensearchUrl, String username, String password, String indexName) {
		try {
			String endpoint = buildEndpoint(opensearchUrl, indexName);
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			HttpEntity<String> request = new HttpEntity<>(null, headers);
			restTemplate.exchange(endpoint, HttpMethod.DELETE, request, String.class);
			log.info("Deleted OpenSearch index: {}", indexName);
		}
		catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().contains("index_not_found")) {
				log.debug("Index '{}' does not exist, nothing to delete", indexName);
			}
			else {
				log.warn("Failed to delete index '{}': {}", indexName, e.getMessage());
			}
		}
	}

	/**
	 * Check if an index exists in OpenSearch.
	 */
	public boolean indexExists(String opensearchUrl, String username, String password, String indexName) {
		try {
			String endpoint = buildEndpoint(opensearchUrl, indexName);
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			HttpEntity<String> request = new HttpEntity<>(null, headers);
			ResponseEntity<String> response = restTemplate.exchange(endpoint, HttpMethod.HEAD, request, String.class);
			return response.getStatusCode().is2xxSuccessful();
		}
		catch (Exception e) {
			return false;
		}
	}

	// ── Internal ─────────────────────────────────────────────────────────

	private void createIndex(String opensearchUrl, String username, String password,
			String indexName, String mappingJson) {
		try {
			String endpoint = buildEndpoint(opensearchUrl, indexName);
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			HttpEntity<String> request = new HttpEntity<>(mappingJson, headers);
			restTemplate.exchange(endpoint, HttpMethod.PUT, request, String.class);
			log.info("Created OpenSearch index with enforced schema: {}", indexName);
		}
		catch (Exception e) {
			if (e.getMessage() != null && e.getMessage().contains("resource_already_exists")) {
				log.debug("Index '{}' already exists, skipping creation", indexName);
			}
			else {
				// B5: Rethrow real errors instead of silently swallowing them
				log.error("Index creation failed for {}: {}", indexName, e.getMessage());
				throw new RuntimeException("Failed to create OpenSearch index '" + indexName + "': " + e.getMessage(), e);
			}
		}
	}

	private String buildEndpoint(String baseUrl, String indexName) {
		return baseUrl.endsWith("/") ? baseUrl + indexName : baseUrl + "/" + indexName;
	}

}
