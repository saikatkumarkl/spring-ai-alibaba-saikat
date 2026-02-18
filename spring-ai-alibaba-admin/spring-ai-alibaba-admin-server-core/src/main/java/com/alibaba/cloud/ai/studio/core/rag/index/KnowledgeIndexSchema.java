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

	// ── Default Settings ─────────────────────────────────────────────────

	/** Default number of shards for new indices. */
	public static final int DEFAULT_SHARDS = 1;

	/** Default number of replicas for new indices. */
	public static final int DEFAULT_REPLICAS = 0;

	// ── Index Mapping Definitions ────────────────────────────────────────

	/**
	 * OpenSearch mapping template for the <strong>_document</strong> index.
	 * Stores all crawled documents with their properties and ACLs.
	 * This schema is the same for ALL connectors (CMIS, REST API, etc.).
	 *
	 * <p>Contains two placeholders: {@code %d} for shards, {@code %d} for replicas.</p>
	 */
	private static final String DOCUMENT_INDEX_MAPPING_TEMPLATE = "{"
			+ "\"settings\":{\"number_of_shards\":%d,\"number_of_replicas\":%d,"
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
			+ "\"allow_token_document\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"deny_token_document\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"allow_token_parent\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"deny_token_parent\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"authorities\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"}"
			+ "}}"
			+ "}";

	/**
	 * Backward-compatible constant with default shard settings (1 shard, 0 replicas).
	 * @deprecated Use {@link #documentIndexMapping(int, int)} for explicit shard control.
	 */
	@Deprecated
	public static final String DOCUMENT_INDEX_MAPPING = String.format(
			DOCUMENT_INDEX_MAPPING_TEMPLATE, DEFAULT_SHARDS, DEFAULT_REPLICAS);

	/**
	 * Get the document index mapping with configurable shard settings.
	 * @param shards number of primary shards
	 * @param replicas number of replica shards
	 * @return the complete mapping JSON
	 */
	public static String documentIndexMapping(int shards, int replicas) {
		return String.format(DOCUMENT_INDEX_MAPPING_TEMPLATE, shards, replicas);
	}

	/**
	 * OpenSearch mapping template for the <strong>_authority</strong> index.
	 * Stores users and groups (principals) with their memberships.
	 * Equivalent to ManifoldCF's {@code {repo}_authorities} index.
	 *
	 * <p>Contains two placeholders: {@code %d} for shards, {@code %d} for replicas.</p>
	 */
	private static final String AUTHORITY_INDEX_MAPPING_TEMPLATE = "{"
			+ "\"settings\":{\"number_of_shards\":%d,\"number_of_replicas\":%d,"
			+ "\"index.highlight.max_analyzed_offset\":10000000,"
			+ "\"analysis\":{\"normalizer\":{\"lowercase\":{\"type\":\"custom\",\"filter\":[\"lowercase\"]}}}},"
			+ "\"mappings\":{\"properties\":{"
			+ "\"principal_id\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"principal_type\":{\"type\":\"keyword\"},"
			+ "\"display_name\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}},"
			+ "\"member_of\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"members\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"},"
			+ "\"member_count\":{\"type\":\"integer\"},"
			+ "\"synced_at\":{\"type\":\"date\"}"
			+ "}}"
			+ "}";

	/**
	 * Backward-compatible constant with default shard settings (1 shard, 0 replicas).
	 * @deprecated Use {@link #authorityIndexMapping(int, int)} for explicit shard control.
	 */
	@Deprecated
	public static final String AUTHORITY_INDEX_MAPPING = String.format(
			AUTHORITY_INDEX_MAPPING_TEMPLATE, DEFAULT_SHARDS, DEFAULT_REPLICAS);

	/**
	 * Get the authority index mapping with configurable shard settings.
	 * @param shards number of primary shards
	 * @param replicas number of replica shards
	 * @return the complete mapping JSON
	 */
	public static String authorityIndexMapping(int shards, int replicas) {
		return String.format(AUTHORITY_INDEX_MAPPING_TEMPLATE, shards, replicas);
	}

	/**
	 * OpenSearch mapping template for the <strong>_rag</strong> index.
	 * Stores chunked content with vector embeddings for RAG retrieval.
	 *
	 * <p>Contains two placeholders:</p>
	 * <ul>
	 *   <li>{@code %d} — embedding dimension (e.g. 1024)</li>
	 *   <li>{@code %s} — extra knn_vector attributes (mode, compression_level, or empty)</li>
	 * </ul>
	 *
	 * <p><strong>Vector storage modes</strong> (set via {@code rag.vector.mode}):</p>
	 * <table>
	 *   <tr><th>Mode</th><th>Behaviour</th></tr>
	 *   <tr><td>{@code on_disk}</td><td>Binary-quantised candidate search (32x compression
	 *       by default) with automatic full-precision rescoring from disk.
	 *       Big RAM savings, slight latency increase.</td></tr>
	 *   <tr><td>{@code in_memory}</td><td>Full-precision vectors kept in RAM.
	 *       Fastest search, highest RAM usage.</td></tr>
	 *   <tr><td><em>empty / not set</em></td><td>OpenSearch default (engine-dependent).</td></tr>
	 * </table>
	 */
	private static final String RAG_INDEX_MAPPING_TEMPLATE = "{"
			+ "\"settings\":{\"number_of_shards\":%d,\"number_of_replicas\":%d,"
			+ "\"index\":{\"knn\":true},"
			+ "\"index.highlight.max_analyzed_offset\":10000000,"
			+ "\"analysis\":{\"normalizer\":{\"lowercase\":{\"type\":\"custom\",\"filter\":[\"lowercase\"]}}}},"
			+ "\"mappings\":{\"properties\":{"
			+ "\"chunk_id\":{\"type\":\"keyword\"},"
			+ "\"doc_id\":{\"type\":\"keyword\"},"
			+ "\"content\":{\"type\":\"text\"},"
			+ "\"embedding\":{\"type\":\"knn_vector\",\"dimension\":%d,\"space_type\":\"cosinesimil\",\"data_type\":\"float\"%s},"
			+ "\"metadata\":{\"type\":\"object\"},"
			+ "\"file_title\":{\"type\":\"text\",\"fields\":{\"keyword\":{\"type\":\"keyword\"}}},"
			+ "\"chunk_index\":{\"type\":\"integer\"},"
			+ "\"authorities\":{\"type\":\"keyword\",\"normalizer\":\"lowercase\"}"
			+ "}}"
			+ "}";

	/**
	 * Get the RAG index mapping with default shard settings and no vector mode.
	 * @param dimension the vector dimension (e.g., 1024 for DashScope text-embedding-v2)
	 * @return the complete mapping JSON
	 */
	public static String ragIndexMapping(int dimension) {
		return ragIndexMapping(dimension, null, null, DEFAULT_SHARDS, DEFAULT_REPLICAS);
	}

	/**
	 * Get the RAG index mapping with configurable vector storage mode and default shards.
	 *
	 * @param dimension        the vector dimension (e.g. 1024)
	 * @param mode             vector storage mode: {@code "on_disk"}, {@code "in_memory"},
	 *                         or {@code null}/{@code ""} for OpenSearch default
	 * @param compressionLevel compression level: {@code "32x"}, {@code "16x"}, {@code "8x"},
	 *                         or {@code null}/{@code ""} for engine default
	 * @return the complete mapping JSON
	 */
	public static String ragIndexMapping(int dimension, String mode, String compressionLevel) {
		return ragIndexMapping(dimension, mode, compressionLevel, DEFAULT_SHARDS, DEFAULT_REPLICAS);
	}

	/**
	 * Get the RAG index mapping with configurable vector storage mode and shard settings.
	 *
	 * @param dimension        the vector dimension (e.g. 1024)
	 * @param mode             vector storage mode: {@code "on_disk"}, {@code "in_memory"},
	 *                         or {@code null}/{@code ""} for OpenSearch default
	 * @param compressionLevel compression level: {@code "32x"}, {@code "16x"}, {@code "8x"},
	 *                         or {@code null}/{@code ""} for engine default
	 * @param shards           number of primary shards
	 * @param replicas         number of replica shards
	 * @return the complete mapping JSON
	 */
	public static String ragIndexMapping(int dimension, String mode, String compressionLevel,
			int shards, int replicas) {
		StringBuilder extra = new StringBuilder();
		if (mode != null && !mode.isBlank()) {
			extra.append(",\"mode\":\"").append(mode.strip()).append("\"");
		}
		if (compressionLevel != null && !compressionLevel.isBlank()) {
			extra.append(",\"compression_level\":\"").append(compressionLevel.strip()).append("\"");
		}
		return String.format(RAG_INDEX_MAPPING_TEMPLATE, shards, replicas, dimension, extra.toString());
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
