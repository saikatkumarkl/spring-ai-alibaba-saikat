/*
 * Copyright 2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.studio.admin.builder.controller;

import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.domain.BaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.app.KnowledgeBaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.DocumentChunk;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.DocumentRetrieverQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.IndexConfig;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.KnowledgeBase;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.KnowledgeSync;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.base.manager.DocumentRetrieverManager;
import com.alibaba.cloud.ai.studio.core.rag.KnowledgeBaseService;
import com.alibaba.cloud.ai.studio.core.rag.KnowledgeSyncService;
import com.alibaba.cloud.ai.studio.admin.builder.annotation.ApiModelAttribute;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.rag.Query;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for managing knowledge bases and document retrieval operations. Provides
 * REST endpoints for CRUD operations on knowledge bases and document retrieval.
 *
 * @since 1.0.0.3
 */
@RestController()
@Tag(name = "rag_knowledge")
@RequestMapping("/console/v1/knowledge-bases")
public class KnowledgeBaseController {

	/** Service for managing knowledge base operations */
	private final KnowledgeBaseService knowledgeBaseService;

	/** Manager for handling document retrieval operations */
	private final DocumentRetrieverManager documentRetrieverManager;

	/** Service for knowledge sync jobs */
	private final KnowledgeSyncService knowledgeSyncService;

	public KnowledgeBaseController(KnowledgeBaseService knowledgeBaseService,
			DocumentRetrieverManager documentRetrieverManager,
			KnowledgeSyncService knowledgeSyncService) {
		this.knowledgeBaseService = knowledgeBaseService;
		this.documentRetrieverManager = documentRetrieverManager;
		this.knowledgeSyncService = knowledgeSyncService;
	}

	/**
	 * Creates a new knowledge base
	 * @param kb Knowledge base configuration
	 * @return Result containing the created knowledge base ID
	 */
	@PostMapping()
	public Result<String> createKnowledgeBase(@RequestBody KnowledgeBase kb) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(kb)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("knowledge_base"));
		}

		if (StringUtils.isBlank(kb.getName())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("name"));
		}

		if (kb.getProcessConfig() == null) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("process_config"));
		}

		if (kb.getIndexConfig() == null) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("index_config"));
		}

		IndexConfig indexConfig = kb.getIndexConfig();
		if (StringUtils.isBlank(indexConfig.getEmbeddingProvider())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("embedding_provider"));
		}

		if (StringUtils.isBlank(indexConfig.getEmbeddingModel())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("embedding_model"));
		}

		String kbId = knowledgeBaseService.createKnowledgeBase(kb);
		return Result.success(context.getRequestId(), kbId);
	}

	/**
	 * Updates an existing knowledge base
	 * @param kbId ID of the knowledge base to update
	 * @param kb Updated knowledge base configuration
	 * @return Result indicating success
	 */
	@PutMapping("/{kbId}")
	public Result<String> updateKnowledgeBase(@PathVariable("kbId") String kbId, @RequestBody KnowledgeBase kb) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(kb)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("kb_id"));
		}

		if (StringUtils.isBlank(kbId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("knowledge base id"));
		}

		if (StringUtils.isBlank(kb.getName())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("name"));
		}

		kb.setKbId(kbId);
		knowledgeBaseService.updateKnowledgeBase(kb);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Deletes a knowledge base
	 * @param kbId ID of the knowledge base to delete
	 * @return Result indicating success
	 */
	@DeleteMapping("/{kbId}")
	public Result<Void> deleteKnowledgeBase(@PathVariable("kbId") String kbId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(kbId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("knowledge base id"));
		}

		knowledgeBaseService.deleteKnowledgeBase(kbId);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Retrieves a knowledge base by ID
	 * @param kbId ID of the knowledge base to retrieve
	 * @return Result containing the knowledge base details
	 */
	@GetMapping("/{kbId}")
	public Result<KnowledgeBase> getKnowledgeBase(@PathVariable("kbId") String kbId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(kbId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("knowledge base id"));
		}

		KnowledgeBase kb = knowledgeBaseService.getKnowledgeBase(kbId);
		return Result.success(context.getRequestId(), kb);
	}

	/**
	 * Lists knowledge bases with pagination
	 * @param query Query parameters for pagination
	 * @return Result containing paginated list of knowledge bases
	 */
	@GetMapping()
	public Result<PagingList<KnowledgeBase>> listKnowledgeBases(@ApiModelAttribute BaseQuery query) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(query)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("query"));
		}

		PagingList<KnowledgeBase> kbs = knowledgeBaseService.listKnowledgeBases(query);
		return Result.success(context.getRequestId(), kbs);
	}

	/**
	 * Retrieves knowledge bases by their IDs
	 * @param query Query containing list of knowledge base IDs
	 * @return Result containing list of knowledge bases
	 */
	@PostMapping("/query-by-codes")
	public Result<List<KnowledgeBase>> queryKnowledgeBasesByCodes(@RequestBody KnowledgeBaseQuery query) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(query) || CollectionUtils.isEmpty(query.getKbIds())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("query or kb_ids"));
		}

		List<KnowledgeBase> knowledgeBases = knowledgeBaseService.listKnowledgeBases(query.getKbIds());
		return Result.success(context.getRequestId(), knowledgeBases);
	}

	/**
	 * Retrieves relevant document chunks based on query
	 * @param query Document retrieval query with search options
	 * @return Result containing list of relevant document chunks
	 */
	@PostMapping("/retrieve")
	public Result<List<DocumentChunk>> retrieve(@RequestBody DocumentRetrieverQuery query) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(query) || StringUtils.isBlank(query.getQuery())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("query"));
		}

		if (Objects.isNull(query.getSearchOptions()) || Objects.isNull(query.getSearchOptions().getKbIds())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("kbIds"));
		}

		if (query.getSearchOptions().getKbIds().size() == 1) {
			String kbId = query.getSearchOptions().getKbIds().get(0);
			KnowledgeBase knowledgeBase = knowledgeBaseService.getKnowledgeBase(kbId);
			query.getSearchOptions().setTopK(knowledgeBase.getSearchConfig().getTopK());
		}

		query.getSearchOptions().setEnableSearch(true);
		List<DocumentChunk> documentChunks = documentRetrieverManager
			.retrieve(Query.builder().text(query.getQuery()).build(), query.getSearchOptions());
		return Result.success(context.getRequestId(), documentChunks);
	}

	// ---- Knowledge Sync Endpoints ----

	/**
	 * Creates a sync job for a knowledge base.
	 */
	@PostMapping("/{kbId}/sync")
	public Result<String> createSync(@PathVariable("kbId") String kbId, @RequestBody KnowledgeSync sync) {
		RequestContext context = RequestContextHolder.getRequestContext();
		sync.setKbId(kbId);
		String syncId = knowledgeSyncService.createSync(sync);
		return Result.success(context.getRequestId(), syncId);
	}

	/**
	 * Gets sync job for a knowledge base.
	 */
	@GetMapping("/{kbId}/sync")
	public Result<KnowledgeSync> getSyncByKbId(@PathVariable("kbId") String kbId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		KnowledgeSync sync = knowledgeSyncService.getSyncByKbId(kbId);
		return Result.success(context.getRequestId(), sync);
	}

	/**
	 * Lists all sync jobs for a knowledge base.
	 */
	@GetMapping("/{kbId}/syncs")
	public Result<List<KnowledgeSync>> listSyncs(@PathVariable("kbId") String kbId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		List<KnowledgeSync> syncs = knowledgeSyncService.listSyncs(kbId);
		return Result.success(context.getRequestId(), syncs);
	}

	/**
	 * Starts a sync job.
	 */
	@PostMapping("/sync/{syncId}/start")
	public Result<Map<String, String>> startSync(@PathVariable("syncId") String syncId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, String> result = knowledgeSyncService.startSync(syncId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Gets detailed sync progress/status.
	 */
	@GetMapping("/sync/{syncId}/status")
	public Result<Map<String, Object>> getSyncStatus(@PathVariable("syncId") String syncId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> status = knowledgeSyncService.getSyncStatus(syncId);
		return Result.success(context.getRequestId(), status);
	}

	/**
	 * Deletes a sync job.
	 */
	@DeleteMapping("/sync/{syncId}")
	public Result<Void> deleteSync(@PathVariable("syncId") String syncId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		knowledgeSyncService.deleteSync(syncId);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Updates sync cron schedule.
	 */
	@PutMapping("/sync/{syncId}/schedule")
	public Result<Void> updateSyncCron(@PathVariable("syncId") String syncId,
			@RequestBody Map<String, String> body) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String cron = body.get("cron");
		if (StringUtils.isBlank(cron)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("cron"));
		}
		knowledgeSyncService.updateSyncCron(syncId, cron);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Start only the document sync phase (ManifoldCF crawl → OpenSearch).
	 */
	@PostMapping("/sync/{syncId}/sync-documents")
	public Result<Map<String, String>> syncDocumentsOnly(@PathVariable("syncId") String syncId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, String> result = knowledgeSyncService.syncDocumentsOnly(syncId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Start only the RAG reindex phase (OpenSearch → RAG vector embeddings).
	 */
	@PostMapping("/sync/{syncId}/reindex-rag")
	public Result<Map<String, String>> reindexRagOnly(@PathVariable("syncId") String syncId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, String> result = knowledgeSyncService.reindexRagOnly(syncId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Lists documents from the OpenSearch index for a source-based knowledge base.
	 */
	@GetMapping("/{kbId}/sync-documents")
	public Result<Map<String, Object>> listSyncDocuments(
			@PathVariable("kbId") String kbId,
			@RequestParam(value = "current", defaultValue = "1") int current,
			@RequestParam(value = "size", defaultValue = "10") int size,
			@RequestParam(value = "query", required = false) String query) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> result = knowledgeSyncService.listSyncDocuments(kbId, current, size, query);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Hard reset: abort MCF job, delete all indices, reset to pending.
	 */
	@PostMapping("/sync/{syncId}/hard-reset")
	public Result<Map<String, Object>> hardReset(@PathVariable("syncId") String syncId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> result = knowledgeSyncService.hardReset(syncId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Stop a running sync: abort MCF job, mark as failed.
	 */
	@PostMapping("/sync/{syncId}/stop")
	public Result<Map<String, Object>> stopSync(@PathVariable("syncId") String syncId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> result = knowledgeSyncService.stopSync(syncId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Browse documents in the document index for a sync job.
	 */
	@GetMapping("/sync/{syncId}/browse")
	public Result<Map<String, Object>> browseDocumentIndex(
			@PathVariable("syncId") String syncId,
			@RequestParam(value = "current", defaultValue = "1") int current,
			@RequestParam(value = "size", defaultValue = "10") int size,
			@RequestParam(value = "query", required = false) String query) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> result = knowledgeSyncService.browseDocumentIndex(syncId, current, size, query);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Get a single document's full detail from the document index.
	 */
	@GetMapping("/sync/{syncId}/document")
	public Result<Map<String, Object>> getDocumentDetail(
			@PathVariable("syncId") String syncId,
			@RequestParam("docId") String docId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> result = knowledgeSyncService.getDocumentDetail(syncId, docId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Download the original document from the CMIS source system.
	 * Proxies through the backend using source credentials.
	 */
	@GetMapping("/sync/{syncId}/document/download")
	public void downloadSourceDocument(
			@PathVariable("syncId") String syncId,
			@RequestParam("docId") String docId,
			HttpServletResponse response) {
		Map<String, Object> download = knowledgeSyncService.downloadSourceDocument(syncId, docId);
		byte[] content = (byte[]) download.get("content");
		String contentType = (String) download.getOrDefault("contentType", "application/octet-stream");
		String fileName = (String) download.getOrDefault("fileName", "document");

		response.setContentType(contentType);
		response.setContentLengthLong(content.length);

		// Encode filename for Content-Disposition (RFC 5987)
		String encodedFileName;
		try {
			encodedFileName = java.net.URLEncoder.encode(fileName, java.nio.charset.StandardCharsets.UTF_8)
					.replace("+", "%20");
		} catch (Exception e) {
			encodedFileName = "document";
		}
		response.setHeader("Content-Disposition",
				"attachment; filename=\"" + fileName + "\"; filename*=UTF-8''" + encodedFileName);

		try (var out = response.getOutputStream()) {
			out.write(content);
			out.flush();
		} catch (Exception e) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("download", "Failed to write document: " + e.getMessage()));
		}
	}

	/**
	 * Update document metadata fields in the document index.
	 */
	@PutMapping("/sync/{syncId}/document/metadata")
	public Result<Map<String, Object>> updateDocumentMetadata(
			@PathVariable("syncId") String syncId,
			@RequestParam("docId") String docId,
			@RequestBody Map<String, Object> metadata) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> result = knowledgeSyncService.updateDocumentMetadata(syncId, docId, metadata);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Get RAG chunks for a specific document from the RAG index.
	 */
	@GetMapping("/sync/{syncId}/document/chunks")
	public Result<Map<String, Object>> getDocumentChunks(
			@PathVariable("syncId") String syncId,
			@RequestParam("docId") String docId,
			@RequestParam(value = "current", defaultValue = "1") int current,
			@RequestParam(value = "size", defaultValue = "20") int size) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, Object> result = knowledgeSyncService.getDocumentChunks(syncId, docId, current, size);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Update a single RAG chunk's content.
	 */
	@PutMapping("/sync/{syncId}/chunk")
	public Result<Map<String, Object>> updateChunkContent(
			@PathVariable("syncId") String syncId,
			@RequestParam("chunkId") String chunkId,
			@RequestBody Map<String, String> body) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String content = body.getOrDefault("content", "");
		Map<String, Object> result = knowledgeSyncService.updateChunkContent(syncId, chunkId, content);
		return Result.success(context.getRequestId(), result);
	}

}
