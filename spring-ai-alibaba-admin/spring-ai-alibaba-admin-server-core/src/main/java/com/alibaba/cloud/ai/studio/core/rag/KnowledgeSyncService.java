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

package com.alibaba.cloud.ai.studio.core.rag;

import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.KnowledgeSync;

import java.util.List;
import java.util.Map;

/**
 * Service for managing knowledge sync jobs (source → destination indexing + RAG
 * processing).
 */
public interface KnowledgeSyncService {

	/**
	 * Create a new sync job for a knowledge base.
	 */
	String createSync(KnowledgeSync sync);

	/**
	 * Get sync details by sync ID.
	 */
	KnowledgeSync getSync(String syncId);

	/**
	 * Get sync details by knowledge base ID.
	 */
	KnowledgeSync getSyncByKbId(String kbId);

	/**
	 * List all sync jobs for a knowledge base.
	 */
	List<KnowledgeSync> listSyncs(String kbId);

	/**
	 * Start a sync job (triggers indexing then RAG processing).
	 */
	Map<String, String> startSync(String syncId);

	/**
	 * Get current sync status and progress.
	 */
	Map<String, Object> getSyncStatus(String syncId);

	/**
	 * Delete a sync job.
	 */
	void deleteSync(String syncId);

	/**
	 * Update sync cron schedule.
	 */
	void updateSyncCron(String syncId, String cronExpression);

	/**
	 * Start only the document sync phase (ManifoldCF crawl → OpenSearch).
	 */
	Map<String, String> syncDocumentsOnly(String syncId);

	/**
	 * Start only the RAG reindex phase (OpenSearch → RAG vector embeddings).
	 */
	Map<String, String> reindexRagOnly(String syncId);

	/**
	 * Hard reset: abort any running MCF job, delete all indices (_document, _authority, _rag),
	 * and reset sync state to pending so it can be re-run from scratch.
	 */
	Map<String, Object> hardReset(String syncId);

	/**
	 * Stop a running sync: abort the MCF job and mark status as failed/stopped.
	 */
	Map<String, Object> stopSync(String syncId);

	/**
	 * Browse documents from the knowledge base's _document index in OpenSearch.
	 * This queries the actual synced index (not manifold*).
	 */
	Map<String, Object> browseDocumentIndex(String syncId, int current, int size, String query);

	/**
	 * List documents from the OpenSearch index for a source-based knowledge base.
	 */
	Map<String, Object> listSyncDocuments(String kbId, int current, int size, String query);

	/**
	 * Get a single document's full detail from the document index by OpenSearch _id.
	 */
	Map<String, Object> getDocumentDetail(String syncId, String docId);

	/**
	 * Get RAG chunks for a specific document from the RAG index.
	 */
	Map<String, Object> getDocumentChunks(String syncId, String docId, int current, int size);

	/**
	 * Update a single RAG chunk's content in the RAG index.
	 */
	Map<String, Object> updateChunkContent(String syncId, String chunkId, String content);

	/**
	 * Update document metadata fields in the document index.
	 */
	Map<String, Object> updateDocumentMetadata(String syncId, String docId, Map<String, Object> metadata);

	/**
	 * Download the original document from the CMIS source system.
	 * Returns a map with "content" (byte[]), "contentType" (String), "fileName" (String).
	 */
	Map<String, Object> downloadSourceDocument(String syncId, String docId);

	/**
	 * Re-RAG specific documents: delete their existing RAG chunks and re-chunk/re-embed.
	 * This is useful for re-processing individual documents without a full reindex.
	 * @param syncId the sync job ID
	 * @param docIds list of OpenSearch document _id values to re-RAG
	 * @return status map with count of re-processed documents and chunks
	 */
	Map<String, Object> reragDocuments(String syncId, List<String> docIds);

}
