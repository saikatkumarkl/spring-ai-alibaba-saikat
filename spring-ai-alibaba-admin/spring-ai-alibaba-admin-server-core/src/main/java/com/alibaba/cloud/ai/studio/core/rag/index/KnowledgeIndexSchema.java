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

/**
 * Enforces a standardized index structure for all connectors.
 *
 * <p>Every knowledge base sync creates exactly three indices:</p>
 * <ul>
 *   <li><strong>{knowledgeName}_document</strong> — stores documents, properties, ACLs
 *       (equivalent to ManifoldCF's {@code manifold_{repositoryName}})</li>
 *   <li><strong>{knowledgeName}_authority</strong> — stores users and groups
 *       (equivalent to ManifoldCF's {@code manifold_{repositoryName}_authorities})</li>
 *   <li><strong>{knowledgeName}_rag</strong> — stores chunked content with vector embeddings
 *       for RAG retrieval</li>
 * </ul>
 *
 * <p>To enforce this structure: all index creation MUST go through
 * {@link KnowledgeIndexSchemaFactory}. The factory validates the index name suffix and
 * applies the correct mapping. Direct index creation that bypasses the factory will not
 * have the required schema.</p>
 *
 * <p><strong>Enforcement mechanism:</strong></p>
 * <ol>
 *   <li>The factory is the ONLY way to get index mappings. All sync services must call
 *       {@link KnowledgeIndexSchemaFactory#createDocumentIndex},
 *       {@link KnowledgeIndexSchemaFactory#createAuthorityIndex}, or
 *       {@link KnowledgeIndexSchemaFactory#createRagIndex}.</li>
 *   <li>Index names are validated — they MUST end with {@code _document}, {@code _authority},
 *       or {@code _rag} respectively. Any other suffix is rejected.</li>
 *   <li>The mappings are defined here as constants and cannot be overridden by connectors.
 *       All connectors share the same schema — this ensures consistency across CMIS,
 *       REST API, and future connectors.</li>
 * </ol>
 */
public final class KnowledgeIndexSchema {

	private KnowledgeIndexSchema() {
		// No instantiation
	}

	/** Suffix for document indices */
	public static final String DOCUMENT_SUFFIX = "_document";

	/** Suffix for authority indices */
	public static final String AUTHORITY_SUFFIX = "_authority";

	/** Suffix for RAG indices */
	public static final String RAG_SUFFIX = "_rag";

	/**
	 * Build the document index name from a knowledge base name.
	 * @param knowledgeName sanitized knowledge base name (lowercase, alphanumeric + underscore)
	 * @return the document index name, e.g. "my_knowledge_document"
	 */
	public static String documentIndexName(String knowledgeName) {
		return sanitize(knowledgeName) + DOCUMENT_SUFFIX;
	}

	/**
	 * Build the authority index name from a knowledge base name.
	 * @param knowledgeName sanitized knowledge base name
	 * @return the authority index name, e.g. "my_knowledge_authority"
	 */
	public static String authorityIndexName(String knowledgeName) {
		return sanitize(knowledgeName) + AUTHORITY_SUFFIX;
	}

	/**
	 * Build the RAG index name from a knowledge base name.
	 * @param knowledgeName sanitized knowledge base name
	 * @return the RAG index name, e.g. "my_knowledge_rag"
	 */
	public static String ragIndexName(String knowledgeName) {
		return sanitize(knowledgeName) + RAG_SUFFIX;
	}

	/**
	 * Sanitize a name to be a valid OpenSearch index name.
	 * Converts to lowercase, replaces non-alphanumeric chars with underscore,
	 * collapses consecutive underscores, trims leading/trailing underscores.
	 */
	public static String sanitize(String name) {
		if (name == null || name.isBlank()) {
			throw new IllegalArgumentException("Knowledge name cannot be blank");
		}
		String sanitized = name.replaceAll("[^a-zA-Z0-9_]", "_").toLowerCase();
		sanitized = sanitized.replaceAll("_+", "_");
		sanitized = sanitized.replaceAll("^_|_$", "");
		if (sanitized.isEmpty()) {
			throw new IllegalArgumentException("Knowledge name produces empty index name after sanitization: " + name);
		}
		return sanitized;
	}

	// ── Index Mapping Definitions ────────────────────────────────────────

	/**
	 * OpenSearch mapping for the <strong>_document</strong> index.
	 * Stores all crawled documents with their properties and ACLs.
	 * This schema is the same for ALL connectors (CMIS, REST API, etc.).
	 */
	public static final String DOCUMENT_INDEX_MAPPING = "{"
			+ "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0,"
			+ "\"index.highlight.max_analyzed_offset\":10000000,"
			+ "\"analysis\":{\"normalizer\":{\"lowercase\":{\"type\":\"custom\",\"filter\":[\"lowercase\"]}}}},"
			+ "\"mappings\":{\"properties\":{"
			+ "\"content\":{\"type\":\"text\"},"
			+ "\"file_title\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}},"
			+ "\"file_name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}},"
			+ "\"file_path\":{\"type\":\"keyword\"},"
			+ "\"file_size\":{\"type\":\"long\"},"
			+ "\"file_type\":{\"type\":\"keyword\"},"
			+ "\"created_at\":{\"type\":\"date\"},"
			+ "\"updated_at\":{\"type\":\"date\"},"
			+ "\"source_id\":{\"type\":\"keyword\"},"
			+ "\"connector_type\":{\"type\":\"keyword\"},"
			+ "\"allow_token_document\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"deny_token_document\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"allow_token_parent\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"deny_token_parent\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"authorities\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"}"
			+ "}}"
			+ "}";

	/**
	 * OpenSearch mapping for the <strong>_authority</strong> index.
	 * Stores users and groups (principals) with their memberships.
	 * Equivalent to ManifoldCF's {@code {repo}_authorities} index.
	 */
	public static final String AUTHORITY_INDEX_MAPPING = "{"
			+ "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0,"
			+ "\"index.highlight.max_analyzed_offset\":10000000,"
			+ "\"analysis\":{\"normalizer\":{\"lowercase\":{\"type\":\"custom\",\"filter\":[\"lowercase\"]}}}},"
			+ "\"mappings\":{\"properties\":{"
			+ "\"principal_id\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"principal_type\":{\"type\":\"keyword\"},"
			+ "\"display_name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}},"
			+ "\"member_of\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"members\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"member_count\":{\"type\":\"integer\"},"
			+ "\"source_id\":{\"type\":\"keyword\"},"
			+ "\"connector_type\":{\"type\":\"keyword\"},"
			+ "\"synced_at\":{\"type\":\"date\"}"
			+ "}}"
			+ "}";

	/**
	 * OpenSearch mapping template for the <strong>_rag</strong> index.
	 * Stores chunked content with vector embeddings for RAG retrieval.
	 * The {@code %d} placeholder must be replaced with the actual embedding dimension.
	 */
	public static final String RAG_INDEX_MAPPING_TEMPLATE = "{"
			+ "\"settings\":{\"number_of_shards\":1,\"number_of_replicas\":0,"
			+ "\"index\":{\"knn\":true},"
			+ "\"index.highlight.max_analyzed_offset\":10000000,"
			+ "\"analysis\":{\"normalizer\":{\"lowercase\":{\"type\":\"custom\",\"filter\":[\"lowercase\"]}}}},"
			+ "\"mappings\":{\"properties\":{"
			+ "\"chunk_id\":{\"type\":\"keyword\"},"
			+ "\"doc_id\":{\"type\":\"keyword\"},"
			+ "\"content\":{\"type\":\"text\"},"
			+ "\"embedding\":{\"type\":\"knn_vector\",\"dimension\":%d,\"method\":{\"name\":\"hnsw\",\"space_type\":\"cosinesimil\"}},"
			+ "\"metadata\":{\"type\":\"object\"},"
			+ "\"source_id\":{\"type\":\"keyword\"},"
			+ "\"file_title\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}},"
			+ "\"chunk_index\":{\"type\":\"integer\"},"
			+ "\"authorities\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"}"
			+ "}}"
			+ "}";

	/**
	 * Get the RAG index mapping with a specific embedding dimension.
	 * @param dimension the vector dimension (e.g., 1024 for DashScope text-embedding-v2)
	 * @return the complete mapping JSON
	 */
	public static String ragIndexMapping(int dimension) {
		return String.format(RAG_INDEX_MAPPING_TEMPLATE, dimension);
	}

	/**
	 * Validate that an index name follows the required naming convention.
	 * @param indexName the index name to validate
	 * @throws IllegalArgumentException if the name doesn't end with a valid suffix
	 */
	public static void validateIndexName(String indexName) {
		if (indexName == null || indexName.isBlank()) {
			throw new IllegalArgumentException("Index name cannot be blank");
		}
		boolean valid = indexName.endsWith(DOCUMENT_SUFFIX)
				|| indexName.endsWith(AUTHORITY_SUFFIX)
				|| indexName.endsWith(RAG_SUFFIX);
		if (!valid) {
			throw new IllegalArgumentException(
					"Index name '" + indexName + "' must end with one of: "
							+ DOCUMENT_SUFFIX + ", " + AUTHORITY_SUFFIX + ", " + RAG_SUFFIX
							+ ". All connectors must follow this naming convention.");
		}
	}

}
