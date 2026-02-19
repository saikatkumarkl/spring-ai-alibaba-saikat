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

package com.alibaba.cloud.ai.studio.core.rag.impl;

import com.alibaba.cloud.ai.studio.core.base.entity.DestinationEntity;
import com.alibaba.cloud.ai.studio.core.base.entity.KnowledgeBaseEntity;
import com.alibaba.cloud.ai.studio.core.base.entity.KnowledgeSyncEntity;
import com.alibaba.cloud.ai.studio.core.base.entity.SourceSystemEntity;
import com.alibaba.cloud.ai.studio.core.base.mapper.DestinationMapper;
import com.alibaba.cloud.ai.studio.core.base.mapper.KnowledgeBaseMapper;
import com.alibaba.cloud.ai.studio.core.base.mapper.KnowledgeSyncMapper;
import com.alibaba.cloud.ai.studio.core.base.mapper.SourceSystemMapper;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.model.embedding.DefaultBatchingStrategy;
import com.alibaba.cloud.ai.studio.core.model.embedding.EmbeddingModelDimension;
import com.alibaba.cloud.ai.studio.core.model.llm.ModelFactory;
import com.alibaba.cloud.ai.studio.core.rag.KnowledgeSyncService;
import com.alibaba.cloud.ai.studio.core.rag.OpenSearchUtils;
import com.alibaba.cloud.ai.studio.core.rag.index.KnowledgeIndexSchema;
import com.alibaba.cloud.ai.studio.core.rag.index.KnowledgeIndexSchemaFactory;
import com.alibaba.cloud.ai.studio.core.source.ManifoldCFBridgeService;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.IndexConfig;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.KnowledgeSync;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.ProcessConfig;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingOptions;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.springframework.http.client.SimpleClientHttpRequestFactory;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.security.cert.X509Certificate;

/**
 * Implementation of KnowledgeSyncService. Orchestrates: 1. Source data fetching (via
 * ManifoldCF crawl or source test query) 2. Indexing to OpenSearch
 * ({knowledge}_index) 3. RAG processing and indexing ({knowledge}_rag) with ACL
 * data
 */
@Slf4j
@Service
public class KnowledgeSyncServiceImpl extends ServiceImpl<KnowledgeSyncMapper, KnowledgeSyncEntity>
		implements KnowledgeSyncService {

	private final SourceSystemMapper sourceSystemMapper;

	private final DestinationMapper destinationMapper;

	private final ManifoldCFBridgeService mcfBridge;

	private final KnowledgeIndexSchemaFactory indexSchemaFactory;

	private final KnowledgeBaseMapper knowledgeBaseMapper;

	private final ModelFactory modelFactory;

	private final ObjectMapper objectMapper;

	private final RestTemplate restTemplate;

	/**
	 * Tracks active sync tokens per syncId. When a new sync starts (or a hard
	 * reset / stop occurs), the old token is replaced or removed.  Async threads
	 * check their token before each DB write — if the token has changed, the
	 * thread knows it has been superseded and should stop immediately.
	 */
	private final ConcurrentHashMap<String, String> activeSyncTokens = new ConcurrentHashMap<>();

	// ── Sync pipeline constants ──────────────────────────────────────────

	/** ManifoldCF job poll interval in milliseconds. */
	private static final int MCF_POLL_INTERVAL_MS = 3_000;

	/** Maximum number of MCF poll iterations (~30 minutes at 3 s intervals). */
	private static final int MCF_MAX_POLL_COUNT = 600;

	/** Consecutive zero-doc polls before declaring source stuck (~2 minutes). */
	private static final int MCF_STUCK_THRESHOLD = 40;

	/** Default chunk size in characters for RAG text splitting (used when KB has no ProcessConfig). */
	private static final int DEFAULT_CHUNK_SIZE_CHARS = 1_000;

	/** Default overlap in characters between adjacent RAG chunks. */
	private static final int DEFAULT_CHUNK_OVERLAP_CHARS = 200;

	/** Number of documents per OpenSearch scroll fetch. */
	private static final int SCROLL_BATCH_SIZE = 50;

	/** Maximum number of RAG chunks per bulk indexing request. */
	private static final int MAX_CHUNKS_PER_BULK = 200;

	/** Maximum bulk request body size in bytes (5 MB). */
	private static final int MAX_BULK_BYTES = 5 * 1024 * 1024;

	/** Timeout in seconds for pre-flight connectivity checks. */
	private static final long CONNECTIVITY_TIMEOUT_SECS = 60;

	/** Default vector embedding dimension. */
	private static final int DEFAULT_EMBEDDING_DIM = 1024;

	/** Max retries for transient OpenSearch failures (5xx, connection reset). */
	private static final int MAX_RETRIES = 3;

	/** Initial backoff in ms between retries (multiplied by attempt number). */
	private static final long RETRY_BACKOFF_MS = 1_000;

	/** Alfresco REST API pagination cap for groups/members listing. */
	private static final int ALFRESCO_MAX_ITEMS = 1_000;

	/**
	 * MIME types that Apache Tika can reliably extract full text from.
	 * Documents with other MIME types will have metadata indexed but content
	 * skipped during RAG processing (no text chunking / embedding).
	 *
	 * Categories:
	 *   - PDF
	 *   - Microsoft Office (Word, Excel, PowerPoint — legacy and OOXML)
	 *   - OpenDocument (ODF: text, spreadsheet, presentation)
	 *   - Plain text and markup (TXT, CSV, TSV, HTML, XML, JSON, YAML, MD)
	 *   - Rich Text Format (RTF)
	 *   - E-books (EPUB)
	 *   - Email (EML, MSG)
	 *   - Source code (Java, Python, JS, TS, C/C++, etc.)
	 */
	public static final Set<String> TIKA_PROCESSABLE_MIME_TYPES = Set.of(
			// PDF
			"application/pdf",
			// Microsoft Word
			"application/msword",
			"application/vnd.openxmlformats-officedocument.wordprocessingml.document",
			// Microsoft Excel
			"application/vnd.ms-excel",
			"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
			// Microsoft PowerPoint
			"application/vnd.ms-powerpoint",
			"application/vnd.openxmlformats-officedocument.presentationml.presentation",
			// OpenDocument
			"application/vnd.oasis.opendocument.text",
			"application/vnd.oasis.opendocument.spreadsheet",
			"application/vnd.oasis.opendocument.presentation",
			// Plain text, markup, data
			"text/plain", "text/csv", "text/tab-separated-values",
			"text/html", "text/xml", "application/xml",
			"application/json", "text/yaml", "application/x-yaml",
			"text/markdown", "text/x-markdown",
			// RTF
			"text/rtf", "application/rtf",
			// E-books
			"application/epub+zip",
			// Email
			"message/rfc822",           // .eml
			"application/vnd.ms-outlook", // .msg
			// Source code
			"text/x-java-source", "text/x-python", "text/javascript",
			"application/javascript", "text/x-c", "text/x-csrc",
			"text/x-c++src", "text/x-csharp", "text/x-go",
			"text/x-rustsrc", "text/x-scala", "text/x-kotlin",
			"text/x-shellscript", "application/x-sh",
			// Visio
			"application/vnd.visio",
			"application/vnd.ms-visio.drawing.main+xml"
	);

	/**
	 * File extensions that Tika can process for full text extraction.
	 * Used as fallback when MIME type is missing or generic (application/octet-stream).
	 */
	public static final Set<String> TIKA_PROCESSABLE_EXTENSIONS = Set.of(
			"pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx",
			"odt", "ods", "odp",
			"txt", "text", "csv", "tsv", "log",
			"html", "htm", "xhtml", "xml",
			"json", "yaml", "yml",
			"md", "markdown", "rst",
			"rtf",
			"epub",
			"eml", "msg",
			"java", "py", "js", "ts", "jsx", "tsx",
			"c", "cpp", "cc", "h", "hpp", "cs",
			"go", "rs", "scala", "kt", "kts",
			"sh", "bash", "zsh", "bat", "cmd", "ps1",
			"sql", "r", "rb", "pl", "lua", "swift",
			"vsd", "vsdx"
	);

	/** Maximum number of buckets in OpenSearch terms aggregation. */
	private static final int MAX_AGGREGATION_BUCKETS = 10_000;

	// B16: Sync status constants — single source of truth for status strings
	// (stored in DB and sent to frontend as-is)
	private static final String STATUS_PENDING = "pending";
	private static final String STATUS_INDEXING = "indexing";
	private static final String STATUS_AUTHORITY_SYNCING = "authority_syncing";
	private static final String STATUS_RAG_PROCESSING = "rag_processing";
	private static final String STATUS_COMPLETED = "completed";
	private static final String STATUS_FAILED = "failed";

	/** Dedicated thread pool for async sync operations (avoids ForkJoinPool starvation). */
	private final ExecutorService syncExecutor = Executors.newFixedThreadPool(4, r -> {
		Thread t = new Thread(r, "kb-sync-worker");
		t.setDaemon(true);
		return t;
	});

	public KnowledgeSyncServiceImpl(SourceSystemMapper sourceSystemMapper, DestinationMapper destinationMapper,
			ManifoldCFBridgeService mcfBridge, KnowledgeIndexSchemaFactory indexSchemaFactory,
			KnowledgeBaseMapper knowledgeBaseMapper, ModelFactory modelFactory) {
		this.sourceSystemMapper = sourceSystemMapper;
		this.destinationMapper = destinationMapper;
		this.mcfBridge = mcfBridge;
		this.indexSchemaFactory = indexSchemaFactory;
		this.knowledgeBaseMapper = knowledgeBaseMapper;
		this.modelFactory = modelFactory;
		this.objectMapper = new ObjectMapper();

		// Configure RestTemplate with connection/read timeouts to prevent threads hanging indefinitely
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(30));
		factory.setReadTimeout(Duration.ofSeconds(120));
		this.restTemplate = new RestTemplate(factory);
	}

	/**
	 * On startup, recover any syncs that were left in an active state
	 * ("indexing", "rag_processing", "authority_syncing") due to a server restart.
	 * These syncs will never complete because the async thread died with the old JVM.
	 */
	@PostConstruct
	void recoverOrphanedSyncs() {
		try {
			LambdaQueryWrapper<KnowledgeSyncEntity> wrapper = new LambdaQueryWrapper<>();
			wrapper.in(KnowledgeSyncEntity::getStatus, STATUS_INDEXING, STATUS_RAG_PROCESSING, STATUS_AUTHORITY_SYNCING);
			List<KnowledgeSyncEntity> orphans = this.list(wrapper);
			if (!orphans.isEmpty()) {
				log.warn("Found {} orphaned syncs from previous run, marking as failed", orphans.size());
				for (KnowledgeSyncEntity orphan : orphans) {
					orphan.setStatus(STATUS_FAILED);
					orphan.setErrorMessage("Server restarted during sync — please restart the sync manually");
					orphan.setGmtModified(new Date());
					this.updateById(orphan);
					log.info("Recovered orphaned sync '{}' (was '{}')", orphan.getSyncId(), orphan.getStatus());
				}
			}
		}
		catch (Exception e) {
			log.warn("Failed to recover orphaned syncs on startup: {}", e.getMessage());
		}
	}

	/**
	 * Gracefully shut down the sync executor on application context close.
	 * Waits up to 30 seconds for running tasks to finish, then force-terminates.
	 */
	@PreDestroy
	void shutdownExecutor() {
		log.info("Shutting down knowledge sync executor...");
		syncExecutor.shutdown();
		try {
			if (!syncExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
				log.warn("Sync executor did not terminate in 30s, forcing shutdown");
				syncExecutor.shutdownNow();
			}
		}
		catch (InterruptedException e) {
			syncExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
	}

	@Override
	public String createSync(KnowledgeSync sync) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String workspaceId = context.getWorkspaceId();
		String accountId = context.getAccountId();

		String syncId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

		// Build index names from kb_id using enforced naming convention
		String kbId = sync.getKbId();
		String sanitizedName = KnowledgeIndexSchema.sanitize(kbId);
		String indexName = KnowledgeIndexSchema.documentIndexName(sanitizedName);
		String authorityIndexName = KnowledgeIndexSchema.authorityIndexName(sanitizedName);
		String ragIndexName = KnowledgeIndexSchema.ragIndexName(sanitizedName);

		KnowledgeSyncEntity entity = new KnowledgeSyncEntity();
		entity.setSyncId(syncId);
		entity.setWorkspaceId(workspaceId);
		entity.setKbId(kbId);
		entity.setSourceId(sync.getSourceId());
		entity.setDestinationId(sync.getDestinationId());
		entity.setSyncCron(sync.getSyncCron());
		entity.setIndexName(indexName);
		entity.setAuthorityIndexName(authorityIndexName);
		entity.setRagIndexName(ragIndexName);
		entity.setStatus(STATUS_PENDING);
		entity.setIndexProgress(0);
		entity.setRagProgress(0);
		entity.setTotalDocs(0L);
		entity.setIndexedDocs(0L);
		entity.setRagDocs(0L);
		entity.setFailedDocs(0L);
		entity.setGmtCreate(new Date());
		entity.setGmtModified(new Date());
		entity.setCreator(accountId);
		entity.setModifier(accountId);

		this.save(entity);
		log.info("Created knowledge sync '{}' for kb='{}', source='{}', dest='{}'", syncId, kbId, sync.getSourceId(),
				sync.getDestinationId());

		return syncId;
	}

	@Override
	public KnowledgeSync getSync(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		return toDto(entity);
	}

	@Override
	public KnowledgeSync getSyncByKbId(String kbId) {
		LambdaQueryWrapper<KnowledgeSyncEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(KnowledgeSyncEntity::getKbId, kbId).orderByDesc(KnowledgeSyncEntity::getGmtModified).last("LIMIT 1");
		KnowledgeSyncEntity entity = this.getOne(wrapper);
		return entity != null ? toDto(entity) : null;
	}

	@Override
	public List<KnowledgeSync> listSyncs(String kbId) {
		LambdaQueryWrapper<KnowledgeSyncEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(KnowledgeSyncEntity::getKbId, kbId).orderByDesc(KnowledgeSyncEntity::getGmtModified);
		return this.list(wrapper).stream().map(this::toDto).collect(Collectors.toList());
	}

	@Override
	public Map<String, String> startSync(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		// B6: Prevent concurrent syncs — reject if already running
		String currentStatus = entity.getStatus();
		if (STATUS_INDEXING.equals(currentStatus) || STATUS_RAG_PROCESSING.equals(currentStatus)
				|| STATUS_AUTHORITY_SYNCING.equals(currentStatus)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("status",
					"Sync is already running (status: " + currentStatus + "). Stop it first or wait for completion."));
		}

		// Validate destination exists
		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}

		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		// Resolve source system (may be null for file-upload-based KBs)
		SourceSystemEntity source = StringUtils.isNotBlank(entity.getSourceId())
				? findSource(entity.getSourceId()) : null;

		// ── Pre-flight connectivity checks (60s timeout per system) ──
		// Don't start sync or show progress until all systems are reachable.
		List<String> failures = runConnectivityChecks(destUrl, destUsername, destPassword, source);
		if (!failures.isEmpty()) {
			String errorMsg = "Connectivity check failed: " + String.join("; ", failures);
			log.warn("Cannot start sync {}: {}", syncId, errorMsg);
			entity.setStatus(STATUS_FAILED);
			entity.setErrorMessage(errorMsg);
			entity.setGmtModified(new Date());
			this.updateById(entity);
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("connectivity", errorMsg));
		}

		// Update status to indexing and clear stale error messages
		entity.setStatus(STATUS_INDEXING);
		entity.setIndexProgress(0);
		entity.setRagProgress(0);
		entity.setErrorMessage("");
		entity.setFailedDocs(0L);
		entity.setGmtModified(new Date());
		this.updateById(entity);

		// Generate a unique sync token. Any previous async thread for this
		// syncId will see a token mismatch on its next DB write and stop.
		String syncToken = UUID.randomUUID().toString();
		activeSyncTokens.put(entity.getSyncId(), syncToken);

		// Start async indexing in a separate thread
		// Note: @Async on self-invocation doesn't work with Spring AOP,
		// so we use CompletableFuture.runAsync() instead.
		CompletableFuture.runAsync(
				() -> startAsyncSync(entity, destUrl, destUsername, destPassword, syncToken), syncExecutor);

		Map<String, String> result = new LinkedHashMap<>();
		result.put("status", "started");
		result.put("sync_id", syncId);
		result.put("index_name", entity.getIndexName());
		result.put("rag_index_name", entity.getRagIndexName());
		return result;
	}

	/**
	 * Runs connectivity checks against all systems involved in the sync pipeline.
	 * Each check has a 60-second timeout. Returns a list of failure messages
	 * (empty if all systems are reachable).
	 *
	 * Systems checked:
	 * - Internal system (ManifoldCF API)
	 * - Target (OpenSearch / destination URL)
	 * - Source (via ManifoldCF repo connection status API)
	 */
	private List<String> runConnectivityChecks(String destUrl, String destUsername, String destPassword,
			SourceSystemEntity source) {
		long timeoutSeconds = CONNECTIVITY_TIMEOUT_SECS;
		List<String> failures = Collections.synchronizedList(new ArrayList<>());

		try {
			// Check 1: Internal system (ManifoldCF)
			Future<?> mcfFuture = syncExecutor.submit(() -> {
				try {
					checkManifoldCFConnectivity();
				}
				catch (Exception e) {
					failures.add("Internal system (ManifoldCF): " + summarizeError(e));
				}
			});

			// Check 2: Target (OpenSearch)
			Future<?> targetFuture = syncExecutor.submit(() -> {
				try {
					checkOpenSearchConnectivity(destUrl, destUsername, destPassword);
				}
				catch (Exception e) {
					failures.add("Target system (OpenSearch): " + summarizeError(e));
				}
			});

			// Check 3: Source (via MCF repo connection status)
			Future<?> sourceFuture = null;
			if (source != null && StringUtils.isNotBlank(source.getMcfConnectionName())) {
				sourceFuture = syncExecutor.submit(() -> {
					try {
						checkSourceConnectivity(source);
					}
					catch (Exception e) {
						String sourceName = StringUtils.isNotBlank(source.getName())
								? source.getName() : source.getSourceId();
						failures.add("Source system (" + sourceName + "): " + summarizeError(e));
					}
				});
			}

			// Wait for all checks with timeout
			waitForCheck(mcfFuture, timeoutSeconds, "Internal system (ManifoldCF)", failures);
			waitForCheck(targetFuture, timeoutSeconds, "Target system (OpenSearch)", failures);
			if (sourceFuture != null) {
				String sourceName = (source != null && StringUtils.isNotBlank(source.getName()))
						? source.getName() : "Source";
				waitForCheck(sourceFuture, timeoutSeconds, "Source system (" + sourceName + ")", failures);
			}
		}
		finally {
			// syncExecutor is shared — not shut down per invocation
		}

		if (failures.isEmpty()) {
			log.info("Pre-flight connectivity checks passed for all systems");
		}
		else {
			log.warn("Pre-flight connectivity check failures: {}", failures);
		}

		return failures;
	}

	/**
	 * Waits for a future to complete within the timeout. On timeout, adds a
	 * "connection timed out" message to the failures list.
	 */
	private void waitForCheck(Future<?> future, long timeoutSeconds, String systemLabel, List<String> failures) {
		try {
			future.get(timeoutSeconds, TimeUnit.SECONDS);
		}
		catch (TimeoutException e) {
			future.cancel(true);
			failures.add(systemLabel + ": connection timed out (no response within " + timeoutSeconds + "s)");
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			failures.add(systemLabel + ": check interrupted");
		}
		catch (ExecutionException e) {
			// Error already captured inside the task
		}
	}

	/**
	 * Check ManifoldCF API is reachable by calling its connector-types endpoint.
	 */
	private void checkManifoldCFConnectivity() {
		try {
			List<Map<String, String>> types = mcfBridge.getConnectorTypes();
			if (types == null || types.isEmpty()) {
				throw new RuntimeException("ManifoldCF returned empty connector list — service may be starting up");
			}
			log.debug("ManifoldCF connectivity OK ({} connector types)", types.size());
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot reach ManifoldCF API: " + e.getMessage(), e);
		}
	}

	/**
	 * Check OpenSearch is reachable by issuing a GET to the cluster root.
	 */
	private void checkOpenSearchConnectivity(String destUrl, String username, String password) {
		RestTemplate timeoutRt = createTimeoutRestTemplate();
		try {
			String url = destUrl.endsWith("/") ? destUrl : destUrl + "/";
			HttpHeaders headers = new HttpHeaders();
			if (StringUtils.isNotBlank(username)) {
				String credentials = username + ":" + password;
				String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
				headers.set("Authorization", "Basic " + encoded);
			}
			HttpEntity<Void> request = new HttpEntity<>(headers);
			ResponseEntity<String> response = timeoutRt.exchange(url, HttpMethod.GET, request, String.class);
			if (!response.getStatusCode().is2xxSuccessful()) {
				throw new RuntimeException("OpenSearch returned HTTP " + response.getStatusCode());
			}
			log.debug("OpenSearch connectivity OK at {}", destUrl);
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot reach OpenSearch at " + destUrl + ": " + e.getMessage(), e);
		}
	}

	/**
	 * Check source system connectivity by asking ManifoldCF to test the repository connection.
	 * MCF internally connects to the source (e.g., Alfresco CMIS) and reports the result.
	 */
	private void checkSourceConnectivity(SourceSystemEntity source) {
		try {
			Map<String, String> status = mcfBridge.testConnection(source.getMcfConnectionName());
			String result = status.getOrDefault("result", status.getOrDefault("check_result", ""));
			if (result.toLowerCase().contains("fail") || result.toLowerCase().contains("error")
					|| result.toLowerCase().contains("exception")) {
				throw new RuntimeException("Source connection check failed: " + result);
			}
			log.debug("Source connectivity OK for '{}': {}", source.getMcfConnectionName(), result);
		}
		catch (RuntimeException e) {
			throw e;
		}
		catch (Exception e) {
			throw new RuntimeException("Cannot verify source system: " + e.getMessage(), e);
		}
	}

	/**
	 * Creates a RestTemplate with connect/read timeouts set to 55 seconds
	 * (slightly under the 60s check timeout to avoid race conditions).
	 */
	private RestTemplate createTimeoutRestTemplate() {
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(55));
		factory.setReadTimeout(Duration.ofSeconds(55));
		return new RestTemplate(factory);
	}

	/**
	 * Extracts a concise error message from an exception, truncating if too long.
	 */
	private String summarizeError(Exception e) {
		String msg = e.getMessage();
		if (msg == null) {
			msg = e.getClass().getSimpleName();
		}
		return msg.length() > 200 ? msg.substring(0, 200) + "..." : msg;
	}

	/**
	 * Configure an HttpURLConnection to trust all SSL certificates.
	 * Used for connecting to source systems with self-signed certificates.
	 */
	private void configureTrustAllSsl(HttpURLConnection conn) throws Exception {
		if (conn instanceof HttpsURLConnection httpsConn) {
			SSLContext sslContext = SSLContext.getInstance("TLS");
			sslContext.init(null, new TrustManager[]{ new X509TrustManager() {
				public void checkClientTrusted(X509Certificate[] chain, String authType) {}
				public void checkServerTrusted(X509Certificate[] chain, String authType) {}
				public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
			}}, new java.security.SecureRandom());
			httpsConn.setSSLSocketFactory(sslContext.getSocketFactory());
			httpsConn.setHostnameVerifier((hostname, session) -> true);
		}
	}

	/**
	 * Wrapper around {@link RestTemplate#exchange} that retries transient failures
	 * (5xx, connection reset, timeout) up to {@link #MAX_RETRIES} times with
	 * exponential backoff.
	 */
	private <T> ResponseEntity<T> exchangeWithRetry(String url, HttpMethod method,
			HttpEntity<?> request, Class<T> responseType) {
		for (int attempt = 1; ; attempt++) {
			try {
				return restTemplate.exchange(url, method, request, responseType);
			}
			catch (Exception e) {
				if (attempt >= MAX_RETRIES || !isRetryableError(e)) {
					throw e;
				}
				long backoff = RETRY_BACKOFF_MS * attempt;
				log.warn("OpenSearch {} {} failed (attempt {}/{}), retrying in {}ms: {}",
						method, url, attempt, MAX_RETRIES, backoff, e.getMessage());
				try {
					Thread.sleep(backoff);
				}
				catch (InterruptedException ie) {
					Thread.currentThread().interrupt();
					throw e;
				}
			}
		}
	}

	/**
	 * Returns {@code true} if the exception indicates a transient failure
	 * that is safe to retry.
	 */
	private boolean isRetryableError(Exception e) {
		String msg = e.getMessage();
		if (msg == null) {
			return false;
		}
		return msg.contains("503") || msg.contains("502") || msg.contains("429")
				|| msg.contains("Connection refused") || msg.contains("Connection reset")
				|| msg.contains("Read timed out") || msg.contains("connect timed out");
	}

	private void startAsyncSync(KnowledgeSyncEntity entity, String destUrl, String destUsername,
			String destPassword, String syncToken) {
		try {
			// Check we are still the active sync before doing anything
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Async sync for {} superseded before start, aborting", entity.getSyncId());
				return;
			}

			// Clear any stale error message from previous runs
			entity.setErrorMessage("");
			entity.setGmtModified(new Date());
			this.updateById(entity);

			// Step 1: Create the document index in OpenSearch using enforced schema
			indexSchemaFactory.createDocumentIndex(destUrl, destUsername, destPassword, entity.getIndexName());

			// Step 1b: Create the authority index in OpenSearch using enforced schema
			indexSchemaFactory.createAuthorityIndex(destUrl, destUsername, destPassword, entity.getAuthorityIndexName());

			// Step 2: If source is set, trigger MCF crawl
			if (StringUtils.isNotBlank(entity.getSourceId())) {
				SourceSystemEntity source = findSource(entity.getSourceId());
				if (source != null && StringUtils.isNotBlank(source.getMcfConnectionName())) {
					if (!isSyncActive(entity.getSyncId(), syncToken)) {
						log.info("Async sync for {} superseded before MCF crawl, aborting", entity.getSyncId());
						return;
					}
					entity.setStatus(STATUS_INDEXING);
					entity.setIndexProgress(5);
					entity.setGmtModified(new Date());
					this.updateById(entity);

					// Step 2.0: Update the CMIS repo connection with the authority index name
					// so the connector knows where to sync group membership data.
					// This ensures no auto-generated "manifold_*" indices are created.
					try {
						Map<String, Object> sourceConfig = deserializeConfig(source.getConnectionConfig());
						sourceConfig.put("authorityIndexName", entity.getAuthorityIndexName());
						mcfBridge.createRepositoryConnection(source.getMcfConnectionName(),
								source.getDescription(), source.getConnectorClass(), sourceConfig);
						log.info("Updated CMIS repo connection '{}' with authorityIndexName='{}'",
								source.getMcfConnectionName(), entity.getAuthorityIndexName());
					}
					catch (Exception e) {
						log.warn("Could not update repo connection with authority index name: {}",
								e.getMessage());
					}

					// Clean up old MCF job ONLY if it's in a terminal error state.
					// Otherwise, KEEP the existing job to preserve MCF's version tracking,
					// enabling incremental sync (only modified/new documents get re-indexed).
					boolean hasExistingJob = StringUtils.isNotBlank(entity.getMcfJobId());
					boolean reuseExistingJob = false;
					if (hasExistingJob) {
						try {
							Map<String, String> jobStatus = mcfBridge.getJobStatus(entity.getMcfJobId());
							String jobStatusStr = jobStatus != null ? String.valueOf(jobStatus.get("status")) : "";
							if ("error".equalsIgnoreCase(jobStatusStr)) {
								log.info("Old MCF job {} is in error state, will recreate", entity.getMcfJobId());
								mcfBridge.abortJob(entity.getMcfJobId());
								mcfBridge.deleteJob(entity.getMcfJobId());
							}
							else {
								// Job exists and is not in error — reuse it for incremental sync
								reuseExistingJob = true;
								log.info("Reusing existing MCF job {} for incremental sync (status={})",
										entity.getMcfJobId(), jobStatusStr);
							}
						}
						catch (Exception e) {
							log.debug("Could not check old MCF job status, will recreate: {}",
									e.getMessage());
							try {
								mcfBridge.abortJob(entity.getMcfJobId());
								mcfBridge.deleteJob(entity.getMcfJobId());
							}
							catch (Exception e2) {
								log.debug("Could not clean up old MCF job: {}", e2.getMessage());
							}
						}
					}

					// Step 2a: Create a per-KB MCF output connection so MCF writes
					// directly to our enforced index name (e.g., "2023070559378198529_document")
					// instead of the shared "OpenSearch" connection's default index.
					String perKbOutputConnName = "KB_" + entity.getKbId();
					String perKbOutputDesc = "KB " + entity.getKbId() + " document index: "
							+ entity.getIndexName();
					mcfBridge.createOutputConnection(perKbOutputConnName, perKbOutputDesc,
							entity.getIndexName());
					log.info("Created per-KB MCF output connection '{}' -> index '{}'",
							perKbOutputConnName, entity.getIndexName());

					// Step 2b: Ensure Tika transformation connection exists
					// MCF will extract text from binary docs (PDF, DOCX, PPTX, etc.)
					// during crawl so the document index has clean text for full-text search.
					mcfBridge.ensureTikaTransformationConnection();

					String jobId;
					if (reuseExistingJob) {
						// Reuse existing MCF job for incremental sync.
						// MCF's version tracking will skip unchanged documents
						// (version = docId + lastModifiedDate + cmisQuery).
						jobId = entity.getMcfJobId();
						mcfBridge.startJob(jobId);
						log.info("Re-started existing MCF job {} for incremental sync {}", jobId,
								entity.getSyncId());
					}
					else {
						// Step 2c: Create and start MCF crawl job with Tika pipeline:
						// CMIS Repository → [Tika text extraction] → [OpenSearch output]
						String jobDescription = "KB Sync: " + entity.getKbId() + " / "
								+ entity.getSyncId();
						// Read CMIS query from source connection config; default to all
						// documents
						Map<String, Object> sourceConfig2 = deserializeConfig(
								source.getConnectionConfig());
						String cmisQuery = getConfigString(sourceConfig2, "cmisQuery",
								"SELECT * FROM cmis:document");
						log.info("Using CMIS query for sync {}: {}", entity.getSyncId(), cmisQuery);
						jobId = mcfBridge.createCrawlJob(jobDescription,
								source.getMcfConnectionName(), perKbOutputConnName, cmisQuery,
								"cmisQuery", mcfBridge.getTikaConnectionName());
						mcfBridge.startJob(jobId);
						log.info("Created and started new MCF crawl job {} for sync {}", jobId,
								entity.getSyncId());
					}

					entity.setMcfJobId(jobId);
					entity.setIndexProgress(10);
					entity.setGmtModified(new Date());
					this.updateById(entity);

					log.info("Started MCF crawl job {} for sync {}", jobId, entity.getSyncId());

					// Poll MCF job status until completion
					pollMcfJobUntilDone(entity, syncToken);
				}
				else {
					log.warn("Source '{}' has no MCF connection, skipping crawl", entity.getSourceId());
					entity.setIndexProgress(100);
					entity.setGmtModified(new Date());
					this.updateById(entity);
				}
			}
			else {
				// No source — just create empty indices (for file upload-based knowledge)
				entity.setIndexProgress(100);
				entity.setGmtModified(new Date());
				this.updateById(entity);
			}

			// ---- Final document count from OpenSearch (stable, not MCF's fluctuating number) ----
			long docCount = countOpenSearchDocs(destUrl, destUsername, destPassword, entity.getIndexName());
			entity.setTotalDocs(docCount);
			entity.setIndexedDocs(docCount);
			entity.setIndexProgress(100);
			entity.setGmtModified(new Date());
			this.updateById(entity);
			log.info("Document crawl complete: {} docs in index '{}'", docCount, entity.getIndexName());

			// ---- Step 3: Authority extraction + group member resolution ----
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Async sync for {} superseded before authority phase, aborting", entity.getSyncId());
				return;
			}
			entity.setStatus(STATUS_AUTHORITY_SYNCING);
			entity.setGmtModified(new Date());
			this.updateById(entity);

			// Load source config for Group Members API (needed to resolve group → users)
			Map<String, Object> authSourceConfig = Collections.emptyMap();
			if (StringUtils.isNotBlank(entity.getSourceId())) {
				SourceSystemEntity authSource = findSource(entity.getSourceId());
				if (authSource != null && StringUtils.isNotBlank(authSource.getConnectionConfig())) {
					authSourceConfig = deserializeConfig(authSource.getConnectionConfig());
				}
			}

			long authCount = populateAuthorityIndex(destUrl, destUsername, destPassword,
					entity.getIndexName(), entity.getAuthorityIndexName(), authSourceConfig);
			log.info("Authority extraction complete: {} unique principals", authCount);

			// Refresh the _document index so that the resolved authorities written by
			// updateDocumentAuthorities are visible to the RAG scroll search below.
			// Without this, OpenSearch's near-real-time lag can cause populateRagIndex
			// to read stale ACL tokens (groups) instead of users-only authorities.
			refreshIndex(destUrl, destUsername, destPassword, entity.getIndexName());

			// ---- Step 4: RAG chunking + embedding ----
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Async sync for {} superseded before RAG phase, aborting", entity.getSyncId());
				return;
			}

			// B10: Single KB lookup for both embedding and process configs
			KnowledgeBaseEntity kbEntity = findKnowledgeBase(entity.getKbId());
			IndexConfig embeddingConfig = resolveEmbeddingConfigFromEntity(kbEntity);
			EmbeddingModel embeddingModel = null;
			int embeddingDim = DEFAULT_EMBEDDING_DIM;
			if (embeddingConfig != null) {
				embeddingDim = EmbeddingModelDimension.getDimension(embeddingConfig.getEmbeddingModel(), DEFAULT_EMBEDDING_DIM);
				embeddingModel = modelFactory.getEmbeddingModel(MetadataMode.EMBED, embeddingConfig);
				log.info("Embedding enabled: provider={}, model={}, dim={}",
						embeddingConfig.getEmbeddingProvider(), embeddingConfig.getEmbeddingModel(), embeddingDim);
			}
			else {
				log.info("No embedding config found for KB {} — RAG index will have text only (no vectors)", entity.getKbId());
			}

			// Resolve chunk config from the KB (size, overlap)
			ProcessConfig processConfig = resolveProcessConfigFromEntity(kbEntity);
			int chunkSize = (processConfig != null && processConfig.getChunkSize() != null && processConfig.getChunkSize() > 0)
					? processConfig.getChunkSize() : DEFAULT_CHUNK_SIZE_CHARS;
			int chunkOverlap = (processConfig != null && processConfig.getChunkOverlap() != null && processConfig.getChunkOverlap() >= 0)
					? processConfig.getChunkOverlap() : DEFAULT_CHUNK_OVERLAP_CHARS;

			indexSchemaFactory.createRagIndex(destUrl, destUsername, destPassword, entity.getRagIndexName(), embeddingDim);
			entity.setStatus(STATUS_RAG_PROCESSING);
			entity.setRagProgress(5);
			entity.setGmtModified(new Date());
			this.updateById(entity);

			long ragCount = populateRagIndex(entity, destUrl, destUsername, destPassword, syncToken,
					embeddingModel, chunkSize, chunkOverlap, kbEntity.getWorkspaceId());
			entity.setRagDocs(ragCount);
			entity.setRagProgress(100);
			entity.setGmtModified(new Date());
			this.updateById(entity);
			log.info("RAG chunking complete: {} chunks in index '{}'", ragCount, entity.getRagIndexName());

			// ---- Step 5: Remove content from _document if full-text search is disabled ----
			boolean fullTextSearch = (processConfig == null || processConfig.getFullTextSearch() == null
					|| processConfig.getFullTextSearch());
			if (!fullTextSearch) {
				log.info("full_text_search=false for KB {} — removing content from document index '{}'",
						entity.getKbId(), entity.getIndexName());
				removeContentFromDocumentIndex(destUrl, destUsername, destPassword, entity.getIndexName());
			}

			// ---- Done ----
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Async sync for {} superseded before completion, aborting", entity.getSyncId());
				return;
			}
			entity.setStatus(STATUS_COMPLETED);
			entity.setIndexProgress(100);
			entity.setRagProgress(100);
			entity.setLastSyncTime(new Date());
			entity.setGmtModified(new Date());
			this.updateById(entity);

			log.info("Knowledge sync completed: syncId={}, docs={}, authorities={}, ragChunks={}",
					entity.getSyncId(), docCount, authCount, ragCount);
		}
		catch (Throwable t) {
			log.error("Knowledge sync failed: syncId={}", entity.getSyncId(), t);
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Async sync for {} superseded, not writing failure to DB", entity.getSyncId());
				return;
			}
			entity.setStatus(STATUS_FAILED);
			entity.setErrorMessage(t.getMessage());
			entity.setGmtModified(new Date());
			this.updateById(entity);
		}
	}

	/**
	 * Checks whether the given sync token is still the active one for this syncId.
	 * Returns false if the token has been superseded by a new sync, hard reset, or stop.
	 */
	private boolean isSyncActive(String syncId, String syncToken) {
		String currentToken = activeSyncTokens.get(syncId);
		return syncToken.equals(currentToken);
	}

	/**
	 * Poll MCF job status every 3 seconds until the job completes or fails.
	 * Updates entity with real document counts during polling.
	 *
	 * Guards against two problems:
	 * 1. Stale threads — checks syncToken before each DB write; stops if superseded.
	 * 2. False progress — when no docs found yet (MCF "starting up"), progress stays
	 *    at 10% instead of climbing. If MCF remains stuck for >2 min, fails the sync.
	 */
	private void pollMcfJobUntilDone(KnowledgeSyncEntity entity, String syncToken) {
		String jobId = entity.getMcfJobId();
		int maxPolls = MCF_MAX_POLL_COUNT;
		int pollCount = 0;
		int stuckCount = 0;
		int maxStuckPolls = MCF_STUCK_THRESHOLD;

		while (pollCount < maxPolls) {
			try {
				Thread.sleep(MCF_POLL_INTERVAL_MS);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				throw new RuntimeException("MCF job polling interrupted", e);
			}

			// Check if this sync has been superseded (hard reset, stop, or new sync)
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("MCF poll for job {} aborted — sync {} superseded", jobId, entity.getSyncId());
				return;
			}

			Map<String, String> status = mcfBridge.getJobStatus(jobId);
			String jobStatus = status.getOrDefault("status", "unknown");

			// Parse document counts from MCF status
			long docsProcessed = parseLong(status.get("documents_processed"), 0L);
			long docsInQueue = parseLong(status.get("documents_in_queue"), 0L);
			long docsOutstanding = parseLong(status.get("documents_outstanding"), 0L);
			long totalDocs = docsProcessed + docsInQueue + docsOutstanding;

			// Calculate progress percentage
			int progress;
			if (totalDocs > 0) {
				// Real docs found — calculate actual progress
				progress = (int) Math.min(95, 10 + (docsProcessed * 85 / totalDocs));
				stuckCount = 0; // reset stuck counter
			}
			else {
				// No docs discovered yet — keep progress at 10% (don't fake progress)
				progress = 10;
				// Track how long we've been stuck with no documents
				if ("starting up".equalsIgnoreCase(jobStatus) || "running".equalsIgnoreCase(jobStatus)) {
					stuckCount++;
				}
			}

			entity.setIndexProgress(progress);
			entity.setIndexedDocs(docsProcessed);
			entity.setGmtModified(new Date());
			this.updateById(entity);

			log.debug("MCF job {} status={}, processed={}/{}, progress={}%, stuckCount={}",
					jobId, jobStatus, docsProcessed, totalDocs, progress, stuckCount);

			// If MCF has been "starting up" with 0 docs for too long, the source is likely down
			if (stuckCount >= maxStuckPolls && totalDocs == 0) {
				String errorMsg = "Source system not responding — MCF job stuck in '"
						+ jobStatus + "' with 0 documents for " + (stuckCount * 3) + " seconds";
				log.warn("MCF job {} timed out waiting for source: {}", jobId, errorMsg);
				entity.setStatus(STATUS_FAILED);
				entity.setErrorMessage(errorMsg);
				entity.setGmtModified(new Date());
				this.updateById(entity);
				// Try to abort the stuck job
				try {
					mcfBridge.abortJob(jobId);
				}
				catch (Exception e) {
					log.debug("Could not abort stuck MCF job: {}", e.getMessage());
				}
				throw new RuntimeException(errorMsg);
			}

			// Check terminal states
			if ("done".equalsIgnoreCase(jobStatus) || "completed".equalsIgnoreCase(jobStatus)) {
				entity.setIndexProgress(95); // Will be set to 100 after OpenSearch final count
				entity.setIndexedDocs(docsProcessed);
				entity.setGmtModified(new Date());
				this.updateById(entity);
				log.info("MCF job {} completed: {} docs processed", jobId, docsProcessed);
				return;
			}

			if ("error".equalsIgnoreCase(jobStatus) || "aborting".equalsIgnoreCase(jobStatus)) {
				String errorMsg = status.getOrDefault("error", "MCF job " + jobStatus);
				entity.setFailedDocs(totalDocs - docsProcessed);
				entity.setErrorMessage(errorMsg);
				entity.setGmtModified(new Date());
				this.updateById(entity);
				log.warn("MCF job {} ended with status: {}", jobId, jobStatus);
				// Don't throw — let RAG phase still run with whatever was indexed
				return;
			}

			// Also check "not yet run" which could mean job was already completed
			if ("not yet run".equalsIgnoreCase(jobStatus)) {
				// This shouldn't happen after startJob, but handle gracefully
				if (pollCount > 5) {
					log.warn("MCF job {} stuck in 'not yet run' after {} polls, continuing", jobId, pollCount);
					return;
				}
			}

			pollCount++;
		}

		log.warn("MCF job {} timed out after {} polls", jobId, maxPolls);
		entity.setErrorMessage("MCF crawl timed out after 30 minutes");
		entity.setGmtModified(new Date());
		this.updateById(entity);
	}

	/**
	 * Count documents in the document index from OpenSearch.
	 * MCF now writes directly to the enforced index name via per-KB output connections,
	 * so no manifold* fallback is needed.
	 */
	private long countOpenSearchDocs(String destUrl, String username, String password, String indexName) {
		try {
			String endpoint = destUrl.endsWith("/")
					? destUrl + indexName + "/_count"
					: destUrl + "/" + indexName + "/_count";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			HttpEntity<String> request = new HttpEntity<>(null, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					endpoint, HttpMethod.GET, request, String.class);

			if (response.getBody() != null) {
				Map<String, Object> body = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {});
				Object count = body.get("count");
				if (count instanceof Number) {
					long docCount = ((Number) count).longValue();
					log.info("Found {} documents in OpenSearch index '{}'", docCount, indexName);
					return docCount;
				}
			}
		}
		catch (Exception e) {
			log.debug("Could not count docs in index '{}': {}", indexName, e.getMessage());
		}

		return 0;
	}

	/**
	 * Step 3: Populate the authority index by extracting unique ACL tokens
	 * from all documents in the document index via terms aggregation,
	 * then resolving group members via the source system's Group API
	 * (e.g., Alfresco REST API).
	 *
	 * <p>For each group token, the method calls the configured Group Members API to
	 * resolve individual PERSON members. Groups are stored with their resolved
	 * {@code members} array and {@code member_count}. Users are stored with their
	 * {@code member_of} array (which groups they belong to).</p>
	 *
	 * <p>After populating the authority index, this method also updates each document
	 * in the document index with a resolved {@code authorities} field — a deduplicated
	 * list of all individual usernames who can access the document (computed by
	 * expanding group tokens to their resolved members).</p>
	 *
	 * @param sourceConfig the source system connection config (for Group API URLs and credentials)
	 * @return the number of unique principals stored
	 */
	@SuppressWarnings("unchecked")
	private long populateAuthorityIndex(String destUrl, String username, String password,
			String docIndexName, String authIndexName, Map<String, Object> sourceConfig) {
		Set<String> allTokens = new HashSet<>();

		// Aggregate unique values from all ACL token fields
		String[] tokenFields = { "allow_token_document", "deny_token_document",
				"allow_token_parent", "deny_token_parent" };
		for (String field : tokenFields) {
			allTokens.addAll(aggregateFieldValues(destUrl, username, password, docIndexName, field));
		}

		// Remove sentinel values
		allTokens.remove("__nosecurity__");
		allTokens.removeIf(t -> t == null || t.isEmpty());

		if (allTokens.isEmpty()) {
			log.info("No ACL tokens found in document index '{}'", docIndexName);
			return 0;
		}

		// ---- Resolve group members via source system Group API ----
		// groupId -> Set<username> (lowercased)
		Map<String, Set<String>> groupMembersMap = new LinkedHashMap<>();
		// username -> Set<groupId> (reverse mapping)
		Map<String, Set<String>> userMemberOfMap = new LinkedHashMap<>();

		// Extract source connection details for Group Members API
		String sourceProtocol = getConfigString(sourceConfig, "protocol", "https");
		String sourceServer = getConfigString(sourceConfig, "server", "");
		String sourcePort = getConfigString(sourceConfig, "port", "443");
		String sourceUsername = getConfigString(sourceConfig, "username", "");
		String sourcePassword = getConfigString(sourceConfig, "password", "");
		String groupMembersApiUrl = getConfigString(sourceConfig, "groupMembersApiUrl", "");

		boolean canResolveGroups = !sourceServer.isEmpty() && !groupMembersApiUrl.isEmpty();
		if (!canResolveGroups) {
			log.info("Group Members API not configured — storing raw tokens without member resolution");
		}

		// Separate groups from users
		Set<String> groupTokens = new HashSet<>();
		Set<String> userTokens = new HashSet<>();
		for (String token : allTokens) {
			if (token.toLowerCase().startsWith("group_")) {
				groupTokens.add(token);
			}
			else {
				userTokens.add(token);
			}
		}

		// Admin users — resolved from admin groups, added to ALL documents' authorities
		Set<String> adminUsers = new LinkedHashSet<>();

		// Resolve members for each group via Alfresco REST API
		if (canResolveGroups) {
			String baseUrl = sourceProtocol + "://" + sourceServer + ":" + sourcePort;

			// Alfresco Group API is case-sensitive but ManifoldCF lowercases all ACL tokens.
			// Fetch all groups from Alfresco to build a case-insensitive lookup map:
			// lowercased_group_id -> original_case_group_id
			String groupApiUrl = getConfigString(sourceConfig, "groupApiUrl", "");
			Map<String, String> groupIdCaseMap = fetchGroupIdCaseMap(baseUrl, groupApiUrl,
					sourceUsername, sourcePassword);
			log.info("Fetched {} groups from Alfresco for case-insensitive lookup", groupIdCaseMap.size());

			// Read admin groups from source config — members of these groups
			// get access to ALL documents regardless of per-document ACLs
			Set<String> adminGroupNames = new LinkedHashSet<>();
			Object adminGroupsObj = sourceConfig.get("adminGroups");
			if (adminGroupsObj instanceof List) {
				for (Object g : (List<?>) adminGroupsObj) {
					if (g != null && !String.valueOf(g).isEmpty()) {
						adminGroupNames.add(String.valueOf(g).toLowerCase());
					}
				}
			}
			if (!adminGroupNames.isEmpty()) {
				log.info("Admin groups configured: {}", adminGroupNames);
			}

			for (String groupToken : groupTokens) {
				// Skip GROUP_EVERYONE — it means all authenticated users
				if ("group_everyone".equalsIgnoreCase(groupToken)) {
					groupMembersMap.put(groupToken, Collections.emptySet());
					continue;
				}

				// Look up the original-case group ID for the Alfresco API
				String originalCaseGroupId = groupIdCaseMap.get(groupToken.toLowerCase());
				if (originalCaseGroupId == null) {
					// Fallback: try the token as-is (may work if Alfresco is not case-sensitive)
					originalCaseGroupId = groupToken;
					log.debug("No case mapping found for '{}', using as-is", groupToken);
				}

				Set<String> members = resolveGroupMembers(baseUrl, groupMembersApiUrl,
						sourceUsername, sourcePassword, originalCaseGroupId);
				groupMembersMap.put(groupToken, members);

				// Collect admin users — check if this group matches any configured admin group
				if (adminGroupNames.contains(groupToken.toLowerCase())) {
					adminUsers.addAll(members);
					log.info("Admin group '{}' resolved to {} users: {}", groupToken, members.size(), members);
				}

				// Build reverse mapping: user -> groups they belong to
				for (String member : members) {
					userMemberOfMap.computeIfAbsent(member, k -> new LinkedHashSet<>()).add(groupToken);
					// Also ensure the resolved user is tracked even if not in raw ACL tokens
					userTokens.add(member);
				}

				log.debug("Resolved group '{}' (API ID: '{}') to {} members: {}",
						groupToken, originalCaseGroupId, members.size(), members);
			}
			log.info("Resolved {} groups via Group Members API, found {} unique users",
					groupTokens.size(),
					userTokens.size());

			// Resolve admin groups that may NOT appear in document ACL tokens
			// (e.g., an admin group with no direct ACL on any document)
			for (String adminGroupName : adminGroupNames) {
				String adminGroupToken = adminGroupName.startsWith("group_")
						? adminGroupName : "group_" + adminGroupName;
				if (!groupMembersMap.containsKey(adminGroupToken)) {
					String originalCaseGroupId = groupIdCaseMap.get(adminGroupToken.toLowerCase());
					if (originalCaseGroupId == null) {
						originalCaseGroupId = adminGroupToken;
					}
					Set<String> members = resolveGroupMembers(baseUrl, groupMembersApiUrl,
							sourceUsername, sourcePassword, originalCaseGroupId);
					if (!members.isEmpty()) {
						adminUsers.addAll(members);
						log.info("Extra admin group '{}' (not in ACL tokens) resolved to {} users: {}",
								adminGroupToken, members.size(), members);
					}
				}
			}

			if (!adminUsers.isEmpty()) {
				log.info("Total admin users (from {} admin groups): {} — these users will have access to all documents",
						adminGroupNames.size(), adminUsers);
				// Ensure admin users are also in userTokens for the authority index
				userTokens.addAll(adminUsers);
			}
		}

		// ---- Build bulk request for authority index ----
		StringBuilder bulk = new StringBuilder();
		String now = new Date().toInstant().toString();

		// Index group entries with resolved members
		for (String groupToken : groupTokens) {
			String id = groupToken.toLowerCase().replace(" ", "_");
			Set<String> members = groupMembersMap.getOrDefault(groupToken, Collections.emptySet());

			bulk.append("{\"index\":{\"_index\":\"").append(authIndexName)
					.append("\",\"_id\":\"").append(escapeJsonString(id)).append("\"}}\n");

			Map<String, Object> doc = new LinkedHashMap<>();
			doc.put("principal_id", groupToken);
			doc.put("principal_type", "group");
			doc.put("display_name", groupToken);
			doc.put("member_count", members.size());
			if (!members.isEmpty()) {
				doc.put("members", new ArrayList<>(members));
			}
			doc.put("synced_at", now);
			try {
				bulk.append(objectMapper.writeValueAsString(doc)).append("\n");
			}
			catch (JsonProcessingException e) {
				log.debug("Failed to serialize authority entry for {}: {}", groupToken, e.getMessage());
			}
		}

		// Index user entries with member_of
		for (String userToken : userTokens) {
			String id = userToken.toLowerCase().replace(" ", "_");
			Set<String> memberOf = userMemberOfMap.getOrDefault(userToken, Collections.emptySet());

			bulk.append("{\"index\":{\"_index\":\"").append(authIndexName)
					.append("\",\"_id\":\"").append(escapeJsonString(id)).append("\"}}\n");

			Map<String, Object> doc = new LinkedHashMap<>();
			doc.put("principal_id", userToken);
			doc.put("principal_type", "user");
			doc.put("display_name", userToken);
			if (!memberOf.isEmpty()) {
				doc.put("member_of", new ArrayList<>(memberOf));
			}
			doc.put("synced_at", now);
			try {
				bulk.append(objectMapper.writeValueAsString(doc)).append("\n");
			}
			catch (JsonProcessingException e) {
				log.debug("Failed to serialize authority entry for {}: {}", userToken, e.getMessage());
			}
		}

		// Execute bulk request with validation
		if (bulk.length() > 0) {
			String endpoint = destUrl.endsWith("/") ? destUrl + "_bulk" : destUrl + "/_bulk";
			int accepted = sendBulkAndCountSuccess(endpoint, bulk.toString(), username, password);
			log.debug("Authority bulk: {} entries accepted", accepted);
		}

		long totalPrincipals = groupTokens.size() + userTokens.size();
		log.info("Populated authority index '{}' with {} principals ({} groups, {} users)",
				authIndexName, totalPrincipals, groupTokens.size(), userTokens.size());

		// ---- Step 3b: Update each document with resolved authorities ----
		if (canResolveGroups) {
			updateDocumentAuthorities(destUrl, username, password, docIndexName, groupMembersMap, adminUsers);
		}

		return totalPrincipals;
	}

	/**
	 * Resolve members of an Alfresco group via the Group Members REST API.
	 *
	 * <p>Calls: {@code GET {baseUrl}{groupMembersApiUrl}?where=(memberType='PERSON')&maxItems=1000}
	 * with the group ID substituted into the URL template.</p>
	 *
	 * <p>Uses raw {@link HttpURLConnection} with SSL trust-all to support self-signed
	 * certificates (common in dev/proxy setups).</p>
	 *
	 * @param baseUrl           the source system base URL (e.g., "https://alfresco-demo:8080")
	 * @param groupMembersApiUrl the API URL template with {groupId} placeholder
	 * @param srcUsername       the source system username for Basic auth
	 * @param srcPassword       the source system password
	 * @param groupToken        the group principal ID (e.g., "GROUP_site_demo-test-site_SiteManager")
	 * @return set of lowercase member usernames
	 */
	@SuppressWarnings("unchecked")
	private Set<String> resolveGroupMembers(String baseUrl, String groupMembersApiUrl,
			String srcUsername, String srcPassword, String groupToken) {
		Set<String> members = new LinkedHashSet<>();
		int skipCount = 0;
		boolean hasMore = true;

		while (hasMore) {
			HttpURLConnection conn = null;
			try {
				// Build the members URL from template
				String membersUrl = baseUrl + groupMembersApiUrl.replace("{groupId}", groupToken);
				// Add memberType=PERSON filter and pagination
				membersUrl += (membersUrl.contains("?") ? "&" : "?")
						+ "where=(memberType%3D%27PERSON%27)&maxItems=" + ALFRESCO_MAX_ITEMS
						+ "&skipCount=" + skipCount;

				URI uri = URI.create(membersUrl);
				conn = (HttpURLConnection) uri.toURL().openConnection();

				configureTrustAllSsl(conn);

				conn.setRequestMethod("GET");
				conn.setConnectTimeout(15_000);
				conn.setReadTimeout(15_000);

				// Basic auth
				if (StringUtils.isNotBlank(srcUsername)) {
					String credentials = srcUsername + ":" + srcPassword;
					String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
					conn.setRequestProperty("Authorization", "Basic " + encoded);
				}
				conn.setRequestProperty("Accept", "application/json");

				int httpStatus = conn.getResponseCode();
				if (httpStatus == 200) {
					try (InputStream in = conn.getInputStream();
							ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
						byte[] buffer = new byte[4096];
						int bytesRead;
						while ((bytesRead = in.read(buffer)) != -1) {
							baos.write(buffer, 0, bytesRead);
						}
						String responseBody = baos.toString(StandardCharsets.UTF_8);

						Map<String, Object> root = objectMapper.readValue(responseBody,
								new TypeReference<Map<String, Object>>() {});
						Map<String, Object> list = (Map<String, Object>) root.get("list");
						if (list != null) {
							List<Map<String, Object>> entries = (List<Map<String, Object>>) list.get("entries");
							if (entries != null) {
								for (Map<String, Object> entryWrapper : entries) {
									Map<String, Object> entry = (Map<String, Object>) entryWrapper.get("entry");
									if (entry != null) {
										String memberType = String.valueOf(entry.getOrDefault("memberType", ""));
										if ("PERSON".equalsIgnoreCase(memberType)) {
											String memberId = String.valueOf(entry.get("id")).toLowerCase();
											members.add(memberId);
										}
									}
								}
								skipCount += entries.size();
							}

							// Check pagination
							Map<String, Object> pagination = (Map<String, Object>) list.get("pagination");
							if (pagination != null) {
								Boolean hasMoreItems = (Boolean) pagination.get("hasMoreItems");
								hasMore = Boolean.TRUE.equals(hasMoreItems);
							}
							else {
								hasMore = false;
							}
						}
						else {
							hasMore = false;
						}
					}
				}
				else if (httpStatus == 404) {
					log.debug("Group '{}' not found via Group Members API (HTTP 404)", groupToken);
					hasMore = false;
				}
				else {
					log.warn("Group Members API returned HTTP {} for group '{}'", httpStatus, groupToken);
					hasMore = false;
				}
			}
			catch (Exception e) {
				log.warn("Failed to resolve group '{}' members (skipCount={}): {}", groupToken, skipCount, e.getMessage());
				hasMore = false;
			}
			finally {
				if (conn != null) {
					conn.disconnect();
				}
			}
		}
		return members;
	}

	/**
	 * Fetch all groups from Alfresco and build a case-insensitive lookup map.
	 *
	 * <p>ManifoldCF lowercases all ACL tokens (e.g., {@code group_site_afc_sitemanager}),
	 * but the Alfresco Groups REST API is case-sensitive (e.g.,
	 * {@code GROUP_site_AFC_SiteManager}). This method fetches ALL groups from Alfresco
	 * and creates a mapping: lowercased group ID → original-case group ID.</p>
	 *
	 * @param baseUrl       the source system base URL
	 * @param groupApiUrl   the Groups list API URL
	 * @param srcUsername   source system username
	 * @param srcPassword   source system password
	 * @return map of lowercased group ID to original-case group ID
	 */
	@SuppressWarnings("unchecked")
	private Map<String, String> fetchGroupIdCaseMap(String baseUrl, String groupApiUrl,
			String srcUsername, String srcPassword) {
		Map<String, String> caseMap = new HashMap<>();
		if (groupApiUrl == null || groupApiUrl.isEmpty()) {
			return caseMap;
		}

		int skipCount = 0;
		boolean hasMore = true;

		while (hasMore) {
			HttpURLConnection conn = null;
			try {
				String fullUrl = baseUrl + groupApiUrl;
				// Paginate to get all groups
				fullUrl += (fullUrl.contains("?") ? "&" : "?")
						+ "maxItems=" + ALFRESCO_MAX_ITEMS + "&skipCount=" + skipCount;

				URI uri = URI.create(fullUrl);
				conn = (HttpURLConnection) uri.toURL().openConnection();

				configureTrustAllSsl(conn);

				conn.setRequestMethod("GET");
				conn.setConnectTimeout(15_000);
				conn.setReadTimeout(30_000);

				if (StringUtils.isNotBlank(srcUsername)) {
					String credentials = srcUsername + ":" + srcPassword;
					String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
					conn.setRequestProperty("Authorization", "Basic " + encoded);
				}
				conn.setRequestProperty("Accept", "application/json");

				int httpStatus = conn.getResponseCode();
				if (httpStatus == 200) {
					try (InputStream in = conn.getInputStream();
							ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
						byte[] buffer = new byte[8192];
						int bytesRead;
						while ((bytesRead = in.read(buffer)) != -1) {
							baos.write(buffer, 0, bytesRead);
						}
						String responseBody = baos.toString(StandardCharsets.UTF_8);

						Map<String, Object> root = objectMapper.readValue(responseBody,
								new TypeReference<Map<String, Object>>() {});
						Map<String, Object> list = (Map<String, Object>) root.get("list");
						if (list != null) {
							List<Map<String, Object>> entries = (List<Map<String, Object>>) list.get("entries");
							if (entries != null) {
								for (Map<String, Object> entryWrapper : entries) {
									Map<String, Object> entry = (Map<String, Object>) entryWrapper.get("entry");
									if (entry != null && entry.containsKey("id")) {
										String originalId = String.valueOf(entry.get("id"));
										caseMap.put(originalId.toLowerCase(), originalId);
									}
								}
								skipCount += entries.size();
							}

							// Check pagination: hasMoreItems flag
							Map<String, Object> pagination = (Map<String, Object>) list.get("pagination");
							if (pagination != null) {
								Boolean hasMoreItems = (Boolean) pagination.get("hasMoreItems");
								hasMore = Boolean.TRUE.equals(hasMoreItems);
							}
							else {
								hasMore = false;
							}
						}
						else {
							hasMore = false;
						}
					}
				}
				else {
					log.warn("Groups list API returned HTTP {} — case-insensitive lookup unavailable", httpStatus);
					hasMore = false;
				}
			}
			catch (Exception e) {
				log.warn("Failed to fetch groups for case mapping (skipCount={}): {}", skipCount, e.getMessage());
				hasMore = false;
			}
			finally {
				if (conn != null) {
					conn.disconnect();
				}
			}
		}

		log.debug("Fetched {} groups for case-insensitive lookup", caseMap.size());
		return caseMap;
	}

	/**
	 * Update each document in the document index with a resolved {@code authorities}
	 * field — a deduplicated list of all individual usernames who can access the document.
	 *
	 * <p>For each document, the method:</p>
	 * <ol>
	 *   <li>Reads {@code allow_token_document} (the raw ACL tokens, mix of users and groups)</li>
	 *   <li>For each group token, expands it to its resolved member usernames</li>
	 *   <li>For each user token, adds it directly</li>
	 *   <li>Writes the deduplicated list to the {@code authorities} field via bulk update</li>
	 * </ol>
	 *
	 * @param destUrl          OpenSearch URL
	 * @param username         OpenSearch username
	 * @param password         OpenSearch password
	 * @param docIndexName     the document index name
	 * @param groupMembersMap  map of groupToken → resolved member usernames
	 */
	@SuppressWarnings("unchecked")
	private void updateDocumentAuthorities(String destUrl, String username, String password,
			String docIndexName, Map<String, Set<String>> groupMembersMap, Set<String> adminUsers) {
		log.info("Updating document authorities in index '{}' (adminUsers={})",
				docIndexName, adminUsers.isEmpty() ? "none" : adminUsers);
		int updatedCount = 0;
		String lastScrollId = null;

		try {
			// Scroll through all documents to read their allow_token_document
			String searchEndpoint = destUrl.endsWith("/")
					? destUrl + docIndexName + "/_search?scroll=2m"
					: destUrl + "/" + docIndexName + "/_search?scroll=2m";

			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Fetch all docs with their allow_token_document field
			String searchBody = "{\"size\":" + MAX_CHUNKS_PER_BULK + ",\"_source\":[\"allow_token_document\"]}";
			HttpEntity<String> searchRequest = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					searchEndpoint, HttpMethod.POST, searchRequest, String.class);

			StringBuilder bulkUpdate = new StringBuilder();
			int batchSize = 0;

			while (response.getBody() != null) {
				Map<String, Object> result = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {});

				String scrollId = (String) result.get("_scroll_id");
				lastScrollId = scrollId;
				Map<String, Object> hits = (Map<String, Object>) result.get("hits");
				if (hits == null) break;

				List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
				if (hitList == null || hitList.isEmpty()) break;

				for (Map<String, Object> hit : hitList) {
					String docId = (String) hit.get("_id");
					Map<String, Object> source = (Map<String, Object>) hit.get("_source");
					if (source == null || docId == null) continue;

					// Get allow_token_document (can be String or List<String>)
					Set<String> resolvedUsers = new LinkedHashSet<>();
					Object allowTokens = source.get("allow_token_document");
					List<String> tokenList = new ArrayList<>();
					if (allowTokens instanceof List) {
						for (Object t : (List<?>) allowTokens) {
							if (t != null) tokenList.add(String.valueOf(t).toLowerCase());
						}
					}
					else if (allowTokens instanceof String) {
						tokenList.add(((String) allowTokens).toLowerCase());
					}

					// Resolve each token
					for (String token : tokenList) {
						if ("__nosecurity__".equals(token)) continue;

						if (token.startsWith("group_")) {
							// Expand group to members
							Set<String> members = groupMembersMap.getOrDefault(token, Collections.emptySet());
							if (!members.isEmpty()) {
								resolvedUsers.addAll(members);
							}
							// If group_everyone or unresolvable, don't add raw group token
						}
						else {
							// Direct user token
							resolvedUsers.add(token);
						}
					}

					// Admin users get access to ALL documents regardless of per-document ACLs
					if (!adminUsers.isEmpty()) {
						resolvedUsers.addAll(adminUsers);
					}

					if (!resolvedUsers.isEmpty()) {
						// Build partial update
						bulkUpdate.append("{\"update\":{\"_index\":\"").append(docIndexName)
								.append("\",\"_id\":\"").append(escapeJsonString(docId)).append("\"}}\n");

						bulkUpdate.append("{\"doc\":{\"authorities\":[");
						boolean first = true;
						for (String user : resolvedUsers) {
							if (!first) bulkUpdate.append(",");
							bulkUpdate.append("\"").append(escapeJsonString(user)).append("\"");
							first = false;
						}
						bulkUpdate.append("]}}\n");
						batchSize++;
						updatedCount++;
					}

					// Flush bulk in batches (by count or byte size)
					if (batchSize >= MAX_CHUNKS_PER_BULK
							|| bulkUpdate.length() * 2 >= MAX_BULK_BYTES) {
						executeBulk(destUrl, username, password, bulkUpdate.toString());
						bulkUpdate.setLength(0);
						batchSize = 0;
					}
				}

				// Scroll to next page
				if (scrollId == null) break;
				String scrollEndpoint = destUrl.endsWith("/")
						? destUrl + "_search/scroll"
						: destUrl + "/_search/scroll";
				String scrollBody = "{\"scroll\":\"2m\",\"scroll_id\":\"" + scrollId + "\"}";
				HttpEntity<String> scrollRequest = new HttpEntity<>(scrollBody, headers);
				response = exchangeWithRetry(scrollEndpoint, HttpMethod.POST, scrollRequest, String.class);
			}

			// Flush remaining
			if (batchSize > 0) {
				executeBulk(destUrl, username, password, bulkUpdate.toString());
			}

			log.info("Updated {} documents with resolved authorities in index '{}'", updatedCount, docIndexName);
		}
		catch (Exception e) {
			log.error("Failed to update document authorities in '{}': {}", docIndexName, e.getMessage(), e);
		}
		finally {
			clearScroll(destUrl, username, password, lastScrollId);
		}
	}

	/**
	 * Execute a bulk request against OpenSearch.
	 */
	private void executeBulk(String destUrl, String username, String password, String bulkBody) {
		String endpoint = destUrl.endsWith("/") ? destUrl + "_bulk" : destUrl + "/_bulk";
		HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
		headers.setContentType(MediaType.valueOf("application/x-ndjson"));
		HttpEntity<String> request = new HttpEntity<>(bulkBody, headers);
		exchangeWithRetry(endpoint, HttpMethod.POST, request, String.class);
	}

	/**
	 * Force a refresh on an OpenSearch index so that recent writes become visible
	 * to subsequent search/scroll requests.
	 *
	 * <p>This is needed after bulk updates (e.g. authority resolution) that must be
	 * readable by the immediately following scroll-based RAG population pass.</p>
	 */
	private void refreshIndex(String destUrl, String username, String password, String indexName) {
		String endpoint = destUrl.endsWith("/")
				? destUrl + indexName + "/_refresh"
				: destUrl + "/" + indexName + "/_refresh";
		HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
		try {
			exchangeWithRetry(endpoint, HttpMethod.POST, new HttpEntity<>(headers), String.class);
			log.debug("Refreshed index '{}' before RAG scroll", indexName);
		}
		catch (Exception e) {
			log.warn("Failed to refresh index '{}': {} — RAG may read stale authorities",
					indexName, e.getMessage());
		}
	}

	/**
	 * Aggregate unique values of a keyword field using OpenSearch terms aggregation.
	 */
	@SuppressWarnings("unchecked")
	private Set<String> aggregateFieldValues(String destUrl, String username, String password,
			String indexName, String fieldName) {
		Set<String> values = new HashSet<>();
		try {
			String endpoint = destUrl.endsWith("/")
					? destUrl + indexName + "/_search"
					: destUrl + "/" + indexName + "/_search";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Terms aggregation with large size to get all unique values
			String body = "{\"size\":0,\"aggs\":{\"tokens\":{\"terms\":{\"field\":\""
					+ fieldName + "\",\"size\":" + MAX_AGGREGATION_BUCKETS + "}}}}";

			HttpEntity<String> request = new HttpEntity<>(body, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					endpoint, HttpMethod.POST, request, String.class);

			if (response.getBody() != null) {
				Map<String, Object> result = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {});
				Map<String, Object> aggs = (Map<String, Object>) result.get("aggregations");
				if (aggs != null) {
					Map<String, Object> tokens = (Map<String, Object>) aggs.get("tokens");
					if (tokens != null) {
						List<Map<String, Object>> buckets = (List<Map<String, Object>>) tokens.get("buckets");
						if (buckets != null) {
							for (Map<String, Object> bucket : buckets) {
								Object key = bucket.get("key");
								if (key != null) {
									values.add(String.valueOf(key));
								}
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			log.debug("Could not aggregate field '{}' in index '{}': {}", fieldName, indexName, e.getMessage());
		}
		return values;
	}

	/**
	 * Step 4: Populate the RAG index by reading Tika-extracted text from the document
	 * index, chunking it, generating vector embeddings, and indexing to the RAG index
	 * with metadata and ACL tokens.
	 *
	 * <p>Key design: The MCF Tika transformation connector extracts text during crawl,
	 * so the document index already contains clean text in the {@code content} field.
	 * The RAG index stores chunked text with embeddings for similarity search — following
	 * RAG best practices. This avoids re-downloading files from CMIS.</p>
	 *
	 * @param entity          the sync entity
	 * @param destUrl         OpenSearch base URL
	 * @param username        OpenSearch username
	 * @param password        OpenSearch password
	 * @param syncToken       token to detect superseded syncs
	 * @param embeddingModel  the embedding model from KB config; {@code null} to skip embeddings
	 * @param chunkSize       chunk size in chars (from KB ProcessConfig or default)
	 * @param chunkOverlap    overlap in chars between chunks
	 * @return number of chunks indexed
	 */
	@SuppressWarnings("unchecked")
	private long populateRagIndex(KnowledgeSyncEntity entity, String destUrl, String username,
			String password, String syncToken, EmbeddingModel embeddingModel,
			int chunkSize, int chunkOverlap, String workspaceId) {
		String docIndexName = entity.getIndexName();
		String ragIndexName = entity.getRagIndexName();
		long chunksIndexed = 0;
		int batchSize = SCROLL_BATCH_SIZE;

		log.info("RAG population starting for sync {} — chunkSize={}, overlap={}, embeddings={}",
				entity.getSyncId(), chunkSize, chunkOverlap, embeddingModel != null ? "enabled" : "disabled");

		try {
			// Initial scroll search
			String searchEndpoint = destUrl.endsWith("/")
					? destUrl + docIndexName + "/_search?scroll=5m"
					: destUrl + "/" + docIndexName + "/_search?scroll=5m";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			String searchBody = "{\"size\":" + batchSize + ",\"query\":{\"match_all\":{}}}";
			HttpEntity<String> request = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					searchEndpoint, HttpMethod.POST, request, String.class);

			if (response.getBody() == null) {
				return 0;
			}

			Map<String, Object> result = objectMapper.readValue(response.getBody(),
					new TypeReference<Map<String, Object>>() {});
			String scrollId = (String) result.get("_scroll_id");

			// Get total for progress tracking
			Map<String, Object> hitsWrapper = (Map<String, Object>) result.get("hits");
			long totalDocs = 0;
			if (hitsWrapper != null) {
				Object totalObj = hitsWrapper.get("total");
				if (totalObj instanceof Map) {
					Object val = ((Map<String, Object>) totalObj).get("value");
					if (val instanceof Number) {
						totalDocs = ((Number) val).longValue();
					}
				}
			}

			long processedDocs = 0;

			while (true) {
				if (!isSyncActive(entity.getSyncId(), syncToken)) {
					log.info("RAG processing for {} superseded, aborting", entity.getSyncId());
					clearScroll(destUrl, username, password, scrollId);
					return chunksIndexed;
				}

				List<Map<String, Object>> hitList = extractHits(result);
				if (hitList == null || hitList.isEmpty()) {
					break;
				}

				// Delete existing RAG chunks for documents in this batch to prevent
				// stale chunks surviving when a document shrinks (fewer chunks than before).
				// The subsequent index operations will recreate chunks with fresh content.
				List<String> batchDocIds = new ArrayList<>();
				for (Map<String, Object> hit : hitList) {
					batchDocIds.add(String.valueOf(hit.get("_id")));
				}
				deleteChunksByDocIds(destUrl, username, password, ragIndexName, batchDocIds);

				// Collect chunk documents before flushing — enables batch embedding
				List<String> pendingChunkIds = new ArrayList<>();
				List<Map<String, Object>> pendingChunkDocs = new ArrayList<>();
				List<String> pendingChunkTexts = new ArrayList<>();

				String bulkEndpoint = destUrl.endsWith("/") ? destUrl + "_bulk" : destUrl + "/_bulk";

				for (Map<String, Object> hit : hitList) {
					String docId = String.valueOf(hit.get("_id"));
					Map<String, Object> docSource = (Map<String, Object>) hit.get("_source");
					if (docSource == null) {
						continue;
					}

					// Map CMIS metadata to normalized names
					String title = firstNonEmpty(docSource, "cm:title", "cmis:name", "cmis:contentStreamFileName");
					String fileName = firstNonEmpty(docSource, "cmis:contentStreamFileName", "cmis:name");
					String mimeType = firstNonEmpty(docSource, "cmis:contentStreamMimeType", "mime-type");
					String createdBy = firstNonEmpty(docSource, "cmis:createdBy");
					String objectId = firstNonEmpty(docSource, "cmis:objectId");

					// ── Check if this file type is processable by Tika ──
					// Skip content extraction for non-processable types (images, videos, etc.)
					// These documents keep their metadata in _document but get no RAG chunks.
					if (!isTikaProcessable(mimeType, fileName)) {
						log.info("Skipping RAG for '{}' (mime={}) — not a Tika-processable type",
								fileName, mimeType);
						processedDocs++;
						continue;
					}

					// ── Read Tika-extracted text from document index ──
					// MCF Tika transformation connector already extracted text during crawl,
					// so the content field has clean readable text (not raw binary).
					String content = docSource.get("content") != null
							? String.valueOf(docSource.get("content")) : "";
					// Normalize: replace non-breaking spaces and other Unicode whitespace
					content = content.replace('\u00a0', ' ')
							.replace('\u2028', '\n')   // Unicode line separator
							.replace('\u2029', '\n')   // Unicode paragraph separator
							.replace("\u0000", "");
					log.debug("RAG: read {} chars from document index for '{}' ({})",
							content.length(), fileName, mimeType);

					// Skip if extraction yielded nothing useful
					if (content == null || content.trim().length() < 50) {
						log.warn("Skipping '{}' — Tika extracted too little text ({} chars)",
								fileName, content == null ? 0 : content.trim().length());
						processedDocs++;
						continue;
					}
					log.debug("Tika extracted {} chars from '{}' ({})", content.length(), fileName, mimeType);

					// Resolved authorities (usernames with access to this document)
					Object authorities = docSource.get("authorities");

					// Chunk content with overlap for better retrieval
					List<String> chunks = chunkTextWithOverlap(content, chunkSize, chunkOverlap);

					for (int i = 0; i < chunks.size(); i++) {
						String chunkId = docId + "_chunk_" + i;

						// Sanitize chunk: strip null bytes and control chars
						// that could cause OpenSearch mapper_parsing_exception
						String chunkContent = chunks.get(i)
								.replace("\u0000", "")
								.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

						// Build chunk document as Map
						Map<String, Object> chunkDoc = new LinkedHashMap<>();
						chunkDoc.put("chunk_id", chunkId);
						chunkDoc.put("doc_id", docId);
						chunkDoc.put("content", chunkContent);
						chunkDoc.put("chunk_index", i);
						chunkDoc.put("file_title", title);

						Map<String, Object> metadata = new LinkedHashMap<>();
						metadata.put("file_name", fileName);
						metadata.put("mime_type", mimeType);
						metadata.put("created_by", createdBy);
						metadata.put("object_id", objectId);
						metadata.put("total_chunks", chunks.size());
						// Required by VectorStore retriever's FilterExpression
						metadata.put("workspace_id", workspaceId != null ? workspaceId : "1");
						metadata.put("enabled", true);
						chunkDoc.put("metadata", metadata);

						// Resolved authorities — only usernames, no raw ACL tokens
						if (authorities != null) {
							chunkDoc.put("authorities", authorities);
						}

						pendingChunkIds.add(chunkId);
						pendingChunkDocs.add(chunkDoc);
						pendingChunkTexts.add(chunkContent);

						// Flush when batch is large enough (by count or estimated byte size)
						// Each chunk with 1024-dim float vector ≈ 12KB of JSON, so check size
						int estimatedBytes = pendingChunkTexts.stream().mapToInt(String::length).sum() * 2
								+ pendingChunkDocs.size() * 12_000; // overhead per doc (vector + metadata)
						if (pendingChunkDocs.size() >= MAX_CHUNKS_PER_BULK
								|| estimatedBytes >= MAX_BULK_BYTES) {
							int accepted = flushRagBatch(ragIndexName, bulkEndpoint, username, password,
									pendingChunkIds, pendingChunkDocs, pendingChunkTexts, embeddingModel);
							chunksIndexed += accepted;
							pendingChunkIds.clear();
							pendingChunkDocs.clear();
							pendingChunkTexts.clear();
						}
					}
					processedDocs++;
				}

				// Flush remaining chunks from this scroll batch
				if (!pendingChunkDocs.isEmpty()) {
					int accepted = flushRagBatch(ragIndexName, bulkEndpoint, username, password,
							pendingChunkIds, pendingChunkDocs, pendingChunkTexts, embeddingModel);
					chunksIndexed += accepted;
				}

				// Update progress
				if (totalDocs > 0) {
					int ragProgress = (int) Math.min(95, 5 + (processedDocs * 90 / totalDocs));
					entity.setRagProgress(ragProgress);
					entity.setRagDocs(chunksIndexed);
					entity.setGmtModified(new Date());
					this.updateById(entity);
				}

				// Scroll to next batch
				String scrollEndpoint = destUrl.endsWith("/")
						? destUrl + "_search/scroll" : destUrl + "/_search/scroll";
				String scrollBody = "{\"scroll\":\"5m\",\"scroll_id\":\"" + scrollId + "\"}";
				HttpEntity<String> scrollRequest = new HttpEntity<>(scrollBody, headers);
				response = exchangeWithRetry(scrollEndpoint, HttpMethod.POST, scrollRequest, String.class);

				if (response.getBody() == null) {
					break;
				}
				result = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {});
				scrollId = (String) result.get("_scroll_id");
			}

			// Clear scroll context
			clearScroll(destUrl, username, password, scrollId);
		}
		catch (Exception e) {
			log.error("RAG population failed for index '{}': {}", ragIndexName, e.getMessage(), e);
			throw new RuntimeException("RAG chunking failed: " + e.getMessage(), e);
		}

		return chunksIndexed;
	}

	/**
	 * Flush a batch of chunk documents to the RAG index, optionally generating
	 * vector embeddings before indexing.
	 *
	 * <p>When {@code embeddingModel} is non-null, the method converts chunk texts
	 * into Spring AI {@link Document} objects, calls
	 * {@link EmbeddingModel#embed(List, EmbeddingOptions, org.springframework.ai.embedding.BatchingStrategy)}
	 * to generate vectors, and attaches each vector as the {@code embedding} field.
	 * This ensures the knn_vector field in the RAG index is populated for
	 * similarity search.</p>
	 *
	 * @return the number of chunks accepted by OpenSearch
	 */
	private int flushRagBatch(String ragIndexName, String bulkEndpoint,
			String username, String password,
			List<String> chunkIds, List<Map<String, Object>> chunkDocs,
			List<String> chunkTexts, EmbeddingModel embeddingModel) {

		// Generate embeddings if model is available
		List<float[]> embeddings = null;
		if (embeddingModel != null && !chunkTexts.isEmpty()) {
			try {
				List<Document> springDocs = new ArrayList<>(chunkTexts.size());
				for (String text : chunkTexts) {
					springDocs.add(new Document(text));
				}
				embeddings = embeddingModel.embed(springDocs,
						EmbeddingOptions.builder().build(),
						new DefaultBatchingStrategy());
				log.debug("Generated {} embeddings (dim={}) for RAG batch",
						embeddings.size(), embeddings.isEmpty() ? 0 : embeddings.get(0).length);
			}
			catch (Exception e) {
				log.warn("Embedding generation failed for batch of {} chunks, indexing without vectors: {}",
						chunkTexts.size(), e.getMessage());
				embeddings = null;
			}
		}

		// Build bulk JSON
		StringBuilder bulk = new StringBuilder();
		for (int i = 0; i < chunkDocs.size(); i++) {
			Map<String, Object> chunkDoc = chunkDocs.get(i);
			String chunkId = chunkIds.get(i);

			// Attach embedding vector if available
			if (embeddings != null && i < embeddings.size()) {
				chunkDoc.put("embedding", embeddings.get(i));
			}

			// Action line
			bulk.append("{\"index\":{\"_index\":\"").append(ragIndexName)
					.append("\",\"_id\":\"").append(escapeJsonString(chunkId)).append("\"}}\n");
			// Document line
			try {
				bulk.append(objectMapper.writeValueAsString(chunkDoc)).append("\n");
			}
			catch (JsonProcessingException e) {
				log.debug("Failed to serialize chunk {}: {}", chunkId, e.getMessage());
			}
		}

		if (bulk.length() == 0) {
			return 0;
		}

		int accepted = sendBulkAndCountSuccess(bulkEndpoint, bulk.toString(), username, password);
		log.debug("RAG bulk flush: {}/{} chunks accepted (buffer {}KB)",
				accepted, chunkDocs.size(), bulk.length() / 1024);
		return accepted;
	}

	/**
	 * Chunk text with overlap between chunks.
	 * <p>Overlap ensures that sentences spanning chunk boundaries are not lost,
	 * improving similarity search recall. Each chunk starts {@code overlap} characters
	 * before the end of the previous chunk, snapping to a sentence/paragraph boundary.</p>
	 */
	private List<String> chunkTextWithOverlap(String text, int maxChunkSize, int overlap) {
		if (text == null || text.isEmpty()) {
			return List.of();
		}
		// Normalize whitespace for consistent chunking
		text = text.replaceAll("[ \\t]{2,}", " ");
		text = text.replaceAll("(\\r?\\n){3,}", "\n\n");
		text = text.trim();

		if (text.length() <= maxChunkSize) {
			return List.of(text);
		}

		List<String> chunks = new ArrayList<>();
		int pos = 0;

		while (pos < text.length()) {
			int end = Math.min(pos + maxChunkSize, text.length());
			if (end < text.length()) {
				// Try to break at paragraph boundary
				int breakAt = text.lastIndexOf("\n\n", end);
				if (breakAt <= pos) {
					// Try sentence boundary (.!? followed by space/newline)
					breakAt = findSentenceBreak(text, pos, end);
				}
				if (breakAt > pos) {
					end = breakAt;
				}
			}

			String chunk = text.substring(pos, end).trim();
			if (!chunk.isEmpty()) {
				chunks.add(chunk);
			}

			// Advance with overlap
			int advance = end - pos;
			if (advance <= overlap) {
				// Avoid infinite loops when overlap >= chunk length
				pos = end;
			}
			else {
				pos = end - overlap;
				// Snap overlap start to a sentence or paragraph boundary
				int snapPos = text.indexOf("\n\n", pos);
				if (snapPos > 0 && snapPos < end) {
					pos = snapPos;
				}
				else {
					int sentSnap = findSentenceStart(text, pos);
					if (sentSnap > 0) {
						pos = sentSnap;
					}
				}
			}
		}

		return chunks;
	}

	/**
	 * Find the last sentence-ending boundary before {@code end} that is after {@code start}.
	 */
	private int findSentenceBreak(String text, int start, int end) {
		int best = -1;
		for (int i = end - 1; i > start; i--) {
			char c = text.charAt(i);
			if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length()
					&& (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\n')) {
				best = i + 1;
				break;
			}
		}
		return best;
	}

	/**
	 * Find the start of the next sentence at or after {@code pos}.
	 */
	private int findSentenceStart(String text, int pos) {
		for (int i = pos; i < text.length() - 1; i++) {
			char c = text.charAt(i);
			if ((c == '.' || c == '!' || c == '?') && i + 1 < text.length()
					&& (text.charAt(i + 1) == ' ' || text.charAt(i + 1) == '\n')) {
				return i + 2; // start after the space
			}
		}
		return pos; // no sentence boundary found
	}

	/**
	 * Extract hit list from an OpenSearch search result.
	 */
	@SuppressWarnings("unchecked")
	private List<Map<String, Object>> extractHits(Map<String, Object> searchResult) {
		Map<String, Object> hits = (Map<String, Object>) searchResult.get("hits");
		if (hits == null) {
			return null;
		}
		return (List<Map<String, Object>>) hits.get("hits");
	}

	/**
	 * Clear an OpenSearch scroll context.
	 */
	private void clearScroll(String destUrl, String username, String password, String scrollId) {
		if (scrollId == null) {
			return;
		}
		try {
			String endpoint = destUrl.endsWith("/")
					? destUrl + "_search/scroll" : destUrl + "/_search/scroll";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);
			String body = "{\"scroll_id\":\"" + scrollId + "\"}";
			HttpEntity<String> request = new HttpEntity<>(body, headers);
			exchangeWithRetry(endpoint, HttpMethod.DELETE, request, String.class);
		}
		catch (Exception e) {
			log.debug("Could not clear scroll context: {}", e.getMessage());
		}
	}

	/**
	 * Delete all RAG chunks whose {@code doc_id} matches any of the given document IDs.
	 * This prevents stale chunks from surviving when a document's content shrinks
	 * (producing fewer chunks than before) on a re-sync.
	 *
	 * <p>Uses OpenSearch {@code _delete_by_query} with a {@code terms} filter for
	 * efficient batch deletion. Silently ignores errors (the RAG index may not exist
	 * yet on the first sync run).</p>
	 */
	private void deleteChunksByDocIds(String destUrl, String username, String password,
			String ragIndexName, List<String> docIds) {
		if (docIds == null || docIds.isEmpty()) {
			return;
		}
		try {
			String endpoint = destUrl.endsWith("/")
					? destUrl + ragIndexName + "/_delete_by_query"
					: destUrl + "/" + ragIndexName + "/_delete_by_query";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Build terms query for all doc_ids in this batch
			StringBuilder termsArray = new StringBuilder("[");
			for (int i = 0; i < docIds.size(); i++) {
				if (i > 0) {
					termsArray.append(",");
				}
				termsArray.append("\"").append(docIds.get(i).replace("\"", "\\\"")).append("\"");
			}
			termsArray.append("]");

			String body = "{\"query\":{\"terms\":{\"doc_id\":" + termsArray + "}}}";
			HttpEntity<String> request = new HttpEntity<>(body, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					endpoint, HttpMethod.POST, request, String.class);
			if (response.getBody() != null) {
				Map<String, Object> result = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {});
				Object deleted = result.get("deleted");
				if (deleted != null && ((Number) deleted).longValue() > 0) {
					log.info("Deleted {} stale RAG chunks for {} documents in '{}'",
							deleted, docIds.size(), ragIndexName);
				}
			}
		}
		catch (Exception e) {
			log.debug("Could not delete old RAG chunks (index may not exist yet): {}", e.getMessage());
		}
	}

	/**
	 * Remove the {@code content} field from ALL documents in the _document index.
	 * This saves significant storage when full-text search on the document index
	 * is not needed (the RAG index has chunked content for search).
	 * Uses _update_by_query with a painless script.
	 */
	private void removeContentFromDocumentIndex(String destUrl, String username, String password,
			String indexName) {
		try {
			String endpoint = destUrl.endsWith("/")
					? destUrl + indexName + "/_update_by_query?conflicts=proceed&wait_for_completion=true"
					: destUrl + "/" + indexName + "/_update_by_query?conflicts=proceed&wait_for_completion=true";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			String body = "{"
					+ "\"script\":{\"source\":\"ctx._source.remove('content')\",\"lang\":\"painless\"},"
					+ "\"query\":{\"exists\":{\"field\":\"content\"}}"
					+ "}";
			HttpEntity<String> request = new HttpEntity<>(body, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					endpoint, HttpMethod.POST, request, String.class);
			if (response.getBody() != null) {
				Map<String, Object> result = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {});
				Object updated = result.get("updated");
				log.info("Removed 'content' field from {} documents in '{}' (full_text_search=false)",
						updated, indexName);
			}
		}
		catch (Exception e) {
			log.warn("Failed to remove content from document index '{}': {}", indexName, e.getMessage());
		}
	}

	/**
	 * Return the first non-empty string value from the source map for the given keys.
	 */
	private String firstNonEmpty(Map<String, Object> source, String... keys) {
		for (String key : keys) {
			Object val = source.get(key);
			if (val != null && !String.valueOf(val).isEmpty() && !"null".equals(String.valueOf(val))) {
				return String.valueOf(val);
			}
		}
		return "";
	}

	/**
	 * Check whether a document's MIME type or file extension indicates that
	 * Apache Tika can extract meaningful full-text content from it.
	 * <p>
	 * Returns {@code true} for documents like PDF, Word, Excel, PowerPoint,
	 * plain text, HTML, source code, etc. Returns {@code false} for images,
	 * audio, video, and other binary formats where Tika would only extract
	 * minimal metadata.
	 *
	 * @param mimeType the document's MIME type (e.g. "application/pdf")
	 * @param fileName the file name, used to extract extension as a fallback
	 * @return true if the file should be processed for full-text RAG
	 */
	public static boolean isTikaProcessable(String mimeType, String fileName) {
		// Check MIME type first
		if (mimeType != null && !mimeType.isEmpty()) {
			String mime = mimeType.toLowerCase().trim();
			if (TIKA_PROCESSABLE_MIME_TYPES.contains(mime)) {
				return true;
			}
			// Also accept any text/* MIME type not explicitly listed
			if (mime.startsWith("text/")) {
				return true;
			}
		}

		// Fallback: check file extension (handles application/octet-stream or missing MIME)
		if (fileName != null && !fileName.isEmpty()) {
			int dot = fileName.lastIndexOf('.');
			if (dot >= 0 && dot < fileName.length() - 1) {
				String ext = fileName.substring(dot + 1).toLowerCase();
				return TIKA_PROCESSABLE_EXTENSIONS.contains(ext);
			}
		}

		// MIME unknown and no extension — skip content extraction to be safe
		return false;
	}

	/**
	 * Escape a string for safe inclusion in hand-built JSON (action lines only).
	 */
	private String escapeJsonString(String input) {
		if (input == null) {
			return "";
		}
		return input.replace("\\", "\\\\")
				.replace("\"", "\\\"")
				.replace("\n", "\\n")
				.replace("\r", "\\r");
	}

	/**
	 * Send a bulk request to OpenSearch and count how many items were accepted
	 * (status < 300). Logs a warning if some items failed.
	 */
	@SuppressWarnings("unchecked")
	private int sendBulkAndCountSuccess(String bulkEndpoint, String bulkBody,
			String username, String password) {
		HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
		// Use UTF-8 charset explicitly — default ISO-8859-1 breaks for non-ASCII chars
		// like \u00a0 (NBSP) from Tika-extracted text, causing json_parse_exception
		headers.setContentType(new MediaType("application", "x-ndjson", StandardCharsets.UTF_8));

		HttpEntity<String> request = new HttpEntity<>(bulkBody, headers);
		ResponseEntity<String> response = exchangeWithRetry(
				bulkEndpoint, HttpMethod.POST, request, String.class);

		if (response.getBody() == null) {
			return 0;
		}

		try {
			Map<String, Object> result = objectMapper.readValue(response.getBody(),
					new TypeReference<Map<String, Object>>() {});
			List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
			if (items == null) {
				return 0;
			}

			int accepted = 0;
			int failed = 0;
			String firstError = null;
			String firstFailedId = null;
			for (Map<String, Object> item : items) {
				Map<String, Object> op = (Map<String, Object>) item.values().iterator().next();
				int status = op.get("status") instanceof Number ? ((Number) op.get("status")).intValue() : 999;
				if (status < 300) {
					accepted++;
				}
				else {
					failed++;
					if (firstError == null) {
						if (op.get("error") != null) {
							firstError = String.valueOf(op.get("error"));
						}
						firstFailedId = String.valueOf(op.get("_id"));
					}
				}
			}

			if (failed > 0) {
				log.warn("Bulk request: {}/{} items failed. First failed id='{}', error: {}", failed, items.size(),
						firstFailedId,
						firstError != null ? firstError.substring(0, Math.min(firstError.length(), 500)) : "unknown");
			}

			return accepted;
		}
		catch (Exception e) {
			log.warn("Failed to parse bulk response: {}", e.getMessage());
			return 0;
		}
	}

	private long parseLong(String value, long defaultValue) {
		if (value == null || value.isEmpty()) {
			return defaultValue;
		}
		try {
			return Long.parseLong(value);
		}
		catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	// Old createOpenSearchIndex and createRagIndex removed — use indexSchemaFactory

	@Override
	public Map<String, Object> getSyncStatus(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		Map<String, Object> status = new LinkedHashMap<>();
		status.put("sync_id", entity.getSyncId());
		status.put("status", entity.getStatus());
		status.put("index_name", entity.getIndexName());
		status.put("authority_index_name", entity.getAuthorityIndexName());
		status.put("rag_index_name", entity.getRagIndexName());
		status.put("index_progress", entity.getIndexProgress());
		status.put("rag_progress", entity.getRagProgress());
		status.put("total_docs", entity.getTotalDocs());
		status.put("indexed_docs", entity.getIndexedDocs());
		status.put("rag_docs", entity.getRagDocs());
		status.put("failed_docs", entity.getFailedDocs());
		status.put("error_message", entity.getErrorMessage());
		status.put("last_sync_time", entity.getLastSyncTime());

		// Compute live authority count from OpenSearch
		long authCount = 0;
		if (StringUtils.isNotBlank(entity.getDestinationId()) && StringUtils.isNotBlank(entity.getAuthorityIndexName())) {
			DestinationEntity dest = findDestination(entity.getDestinationId());
			if (dest != null) {
				Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
				String destUrl = getConfigString(destConfig, "url", "");
				String destUsername = getConfigString(destConfig, "username", "");
				String destPassword = getConfigString(destConfig, "password", "");
				authCount = countOpenSearchDocs(destUrl, destUsername, destPassword, entity.getAuthorityIndexName());
			}
		}
		status.put("authority_count", authCount);

		// Overall progress: index=40%, authority=10%, RAG=50%
		int authProgress = authCount > 0 ? 100 : (
				"authority_syncing".equals(entity.getStatus()) ? 50 : (
						"rag_processing".equals(entity.getStatus()) || "completed".equals(entity.getStatus()) ? 100 : 0));
		int overallProgress = (int) (entity.getIndexProgress() * 0.4 + authProgress * 0.1 + entity.getRagProgress() * 0.5);
		status.put("overall_progress", overallProgress);

		return status;
	}

	@Override
	public void deleteSync(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		this.removeById(entity.getId());
		log.info("Deleted knowledge sync '{}'", syncId);
	}

	@Override
	public void updateSyncCron(String syncId, String cronExpression) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		entity.setSyncCron(cronExpression);
		entity.setGmtModified(new Date());
		this.updateById(entity);
	}

	// ---- Private helpers ----

	private KnowledgeSyncEntity findBySyncId(String syncId) {
		LambdaQueryWrapper<KnowledgeSyncEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(KnowledgeSyncEntity::getSyncId, syncId);
		return this.getOne(wrapper);
	}

	private SourceSystemEntity findSource(String sourceId) {
		LambdaQueryWrapper<SourceSystemEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(SourceSystemEntity::getSourceId, sourceId);
		return sourceSystemMapper.selectOne(wrapper);
	}

	private DestinationEntity findDestination(String destinationId) {
		LambdaQueryWrapper<DestinationEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(DestinationEntity::getDestinationId, destinationId)
			.ne(DestinationEntity::getStatus, -1);
		return destinationMapper.selectOne(wrapper);
	}

	/**
	 * B17: Resolved destination config (URL + credentials).
	 * Deduplicates the repeated findDestination → deserializeConfig → getConfigString x3 pattern.
	 */
	private record DestConfig(String url, String username, String password) {}

	/**
	 * Resolve destination URL and credentials from a destination ID.
	 * @throws BizException if destination is not found
	 */
	private DestConfig resolveDestConfig(String destinationId) {
		DestinationEntity dest = findDestination(destinationId);
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}
		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		return new DestConfig(
				getConfigString(destConfig, "url", ""),
				getConfigString(destConfig, "username", ""),
				getConfigString(destConfig, "password", "")
		);
	}

	private KnowledgeSync toDto(KnowledgeSyncEntity entity) {
		KnowledgeSync dto = new KnowledgeSync();
		dto.setSyncId(entity.getSyncId());
		dto.setWorkspaceId(entity.getWorkspaceId());
		dto.setKbId(entity.getKbId());
		dto.setSourceId(entity.getSourceId());
		dto.setDestinationId(entity.getDestinationId());
		dto.setSyncCron(entity.getSyncCron());
		dto.setIndexName(entity.getIndexName());
		dto.setAuthorityIndexName(entity.getAuthorityIndexName());
		dto.setRagIndexName(entity.getRagIndexName());
		dto.setMcfJobId(entity.getMcfJobId());
		dto.setStatus(entity.getStatus());
		dto.setIndexProgress(entity.getIndexProgress());
		dto.setRagProgress(entity.getRagProgress());
		dto.setTotalDocs(entity.getTotalDocs());
		dto.setIndexedDocs(entity.getIndexedDocs());
		dto.setRagDocs(entity.getRagDocs());
		dto.setFailedDocs(entity.getFailedDocs());
		dto.setErrorMessage(entity.getErrorMessage());
		dto.setLastSyncTime(entity.getLastSyncTime());
		dto.setGmtCreate(entity.getGmtCreate());
		dto.setGmtModified(entity.getGmtModified());
		dto.setCreator(entity.getCreator());
		dto.setModifier(entity.getModifier());
		return dto;
	}

	private Map<String, Object> deserializeConfig(String configJson) {
		if (StringUtils.isBlank(configJson)) {
			return new HashMap<>();
		}
		try {
			return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (JsonProcessingException e) {
			return new HashMap<>();
		}
	}

	/**
	 * Look up the KB entity from the knowledge_base table (single query for both configs).
	 * Returns null if the KB does not exist.
	 */
	private KnowledgeBaseEntity findKnowledgeBase(String kbId) {
		if (StringUtils.isBlank(kbId)) {
			return null;
		}
		LambdaQueryWrapper<KnowledgeBaseEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(KnowledgeBaseEntity::getKbId, kbId).last("LIMIT 1");
		return knowledgeBaseMapper.selectOne(wrapper);
	}

	/**
	 * Look up the KB's embedding configuration from the knowledge_base table.
	 * Returns an {@link IndexConfig} with embeddingProvider/embeddingModel populated,
	 * or {@code null} if the KB has no embedding config.
	 */
	private IndexConfig resolveEmbeddingConfig(String kbId) {
		return resolveEmbeddingConfigFromEntity(findKnowledgeBase(kbId));
	}

	/** Extract IndexConfig from an already-loaded KB entity. */
	private IndexConfig resolveEmbeddingConfigFromEntity(KnowledgeBaseEntity kb) {
		if (kb == null || StringUtils.isBlank(kb.getIndexConfig())) {
			return null;
		}
		try {
			IndexConfig cfg = objectMapper.readValue(kb.getIndexConfig(), IndexConfig.class);
			if (StringUtils.isBlank(cfg.getEmbeddingProvider()) || StringUtils.isBlank(cfg.getEmbeddingModel())) {
				return null;
			}
			return cfg;
		}
		catch (JsonProcessingException e) {
			log.warn("Failed to parse index_config for KB: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Look up the KB's process configuration (chunk size, overlap) from the knowledge_base table.
	 * Returns a {@link ProcessConfig} or {@code null} if not configured.
	 */
	private ProcessConfig resolveProcessConfig(String kbId) {
		return resolveProcessConfigFromEntity(findKnowledgeBase(kbId));
	}

	/** Extract ProcessConfig from an already-loaded KB entity. */
	private ProcessConfig resolveProcessConfigFromEntity(KnowledgeBaseEntity kb) {
		if (kb == null || StringUtils.isBlank(kb.getProcessConfig())) {
			return null;
		}
		try {
			return objectMapper.readValue(kb.getProcessConfig(), ProcessConfig.class);
		}
		catch (JsonProcessingException e) {
			log.warn("Failed to parse process_config for KB: {}", e.getMessage());
			return null;
		}
	}

	private String getConfigString(Map<String, Object> config, String key, String defaultValue) {
		if (config == null) {
			return defaultValue;
		}
		Object val = config.get(key);
		return val != null ? String.valueOf(val) : defaultValue;
	}

	@Override
	public Map<String, String> syncDocumentsOnly(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}

		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		entity.setStatus(STATUS_INDEXING);
		entity.setIndexProgress(0);
		entity.setGmtModified(new Date());
		this.updateById(entity);

		// Generate a sync token for the doc-only path
		String syncToken = UUID.randomUUID().toString();
		activeSyncTokens.put(entity.getSyncId(), syncToken);

		CompletableFuture.runAsync(
				() -> startAsyncDocSyncOnly(entity, destUrl, destUsername, destPassword, syncToken), syncExecutor);

		Map<String, String> result = new LinkedHashMap<>();
		result.put("status", "started");
		result.put("sync_id", syncId);
		result.put("phase", "sync_documents");
		return result;
	}

	protected void startAsyncDocSyncOnly(KnowledgeSyncEntity entity, String destUrl, String destUsername,
			String destPassword, String syncToken) {
		try {
			// B4: Check sync token before each phase
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Doc sync for {} superseded before start, aborting", entity.getSyncId());
				return;
			}

			indexSchemaFactory.createDocumentIndex(destUrl, destUsername, destPassword, entity.getIndexName());
			indexSchemaFactory.createAuthorityIndex(destUrl, destUsername, destPassword, entity.getAuthorityIndexName());

			if (StringUtils.isNotBlank(entity.getSourceId())) {
				SourceSystemEntity source = findSource(entity.getSourceId());
				if (source != null && StringUtils.isNotBlank(source.getMcfConnectionName())) {
					entity.setIndexProgress(5);
					entity.setGmtModified(new Date());
					this.updateById(entity);

					// Update the CMIS repo connection with the authority index name
					// so the connector knows where to sync group membership data.
					try {
						Map<String, Object> srcConfig = deserializeConfig(source.getConnectionConfig());
						srcConfig.put("authorityIndexName", entity.getAuthorityIndexName());
						mcfBridge.createRepositoryConnection(source.getMcfConnectionName(),
								source.getDescription(), source.getConnectorClass(), srcConfig);
						log.info("Updated CMIS repo connection '{}' with authorityIndexName='{}'",
								source.getMcfConnectionName(), entity.getAuthorityIndexName());
					}
					catch (Exception e) {
						log.warn("Could not update repo connection with authority index name: {}",
								e.getMessage());
					}

					// Clean up old MCF job ONLY if in error state; otherwise reuse
					// for incremental sync (preserves version tracking).
					boolean hasExistingDocJob = StringUtils.isNotBlank(entity.getMcfJobId());
					boolean reuseExistingDocJob = false;
					if (hasExistingDocJob) {
						try {
							Map<String, String> jobStatus = mcfBridge.getJobStatus(entity.getMcfJobId());
							String jobStatusStr = jobStatus != null
									? String.valueOf(jobStatus.get("status")) : "";
							if ("error".equalsIgnoreCase(jobStatusStr)) {
								log.info("Old MCF doc job {} is in error state, will recreate",
										entity.getMcfJobId());
								mcfBridge.abortJob(entity.getMcfJobId());
								mcfBridge.deleteJob(entity.getMcfJobId());
							}
							else {
								reuseExistingDocJob = true;
								log.info(
										"Reusing existing MCF doc job {} for incremental sync (status={})",
										entity.getMcfJobId(), jobStatusStr);
							}
						}
						catch (Exception e) {
							log.debug("Could not check old MCF doc job status, will recreate: {}",
									e.getMessage());
							try {
								mcfBridge.abortJob(entity.getMcfJobId());
								mcfBridge.deleteJob(entity.getMcfJobId());
							}
							catch (Exception e2) {
								log.debug("Could not clean up old MCF doc job: {}", e2.getMessage());
							}
						}
					}

					String jobId;
					if (reuseExistingDocJob) {
						jobId = entity.getMcfJobId();
						mcfBridge.startJob(jobId);
						log.info("Re-started existing MCF doc job {} for incremental sync {}",
								jobId, entity.getSyncId());
					}
					else {
						String jobDescription = "Doc Sync: " + entity.getKbId() + " / "
								+ entity.getSyncId();
						String outputConn = StringUtils.isNotBlank(source.getMcfOutputName())
								? source.getMcfOutputName() : null;
						Map<String, Object> sourceConfig = deserializeConfig(
								source.getConnectionConfig());
						String cmisQuery = getConfigString(sourceConfig, "cmisQuery",
								"SELECT * FROM cmis:document");
						log.info("Using CMIS query for doc sync {}: {}", entity.getSyncId(),
								cmisQuery);
						jobId = mcfBridge.createCrawlJob(jobDescription,
								source.getMcfConnectionName(), outputConn, cmisQuery, "cmisQuery");
						mcfBridge.startJob(jobId);
						log.info("Created and started new MCF doc job {} for sync {}", jobId,
								entity.getSyncId());
					}

					entity.setMcfJobId(jobId);
					entity.setIndexProgress(10);
					entity.setGmtModified(new Date());
					this.updateById(entity);

					pollMcfJobUntilDone(entity, syncToken);
				}
			}

			long docCount = countOpenSearchDocs(destUrl, destUsername, destPassword, entity.getIndexName());
			entity.setTotalDocs(docCount);
			entity.setIndexedDocs(docCount);
			entity.setIndexProgress(100);
			entity.setStatus(STATUS_COMPLETED);
			entity.setLastSyncTime(new Date());
			entity.setGmtModified(new Date());
			this.updateById(entity);
			log.info("Document sync only completed: syncId={}, totalDocs={}", entity.getSyncId(), docCount);
		}
		catch (Exception e) {
			log.error("Document sync only failed: syncId={}", entity.getSyncId(), e);
			entity.setStatus(STATUS_FAILED);
			entity.setErrorMessage(e.getMessage());
			entity.setGmtModified(new Date());
			this.updateById(entity);
		}
	}

	@Override
	public Map<String, String> reindexRagOnly(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		DestConfig dc = resolveDestConfig(entity.getDestinationId());

		// B15: Validate destination URL before launching async work
		if (StringUtils.isBlank(dc.url())) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_url",
					"Destination URL is blank — cannot reindex"));
		}

		entity.setStatus(STATUS_RAG_PROCESSING);
		entity.setRagProgress(0);
		entity.setGmtModified(new Date());
		this.updateById(entity);

		CompletableFuture.runAsync(
				() -> startAsyncRagOnly(entity, dc.url(), dc.username(), dc.password()), syncExecutor);

		Map<String, String> result = new LinkedHashMap<>();
		result.put("status", "started");
		result.put("sync_id", syncId);
		result.put("phase", "reindex_rag");
		return result;
	}

	protected void startAsyncRagOnly(KnowledgeSyncEntity entity, String destUrl, String destUsername,
			String destPassword) {
		try {
			// Generate a sync token for this operation
			String syncToken = UUID.randomUUID().toString();
			activeSyncTokens.put(entity.getSyncId(), syncToken);

			// B10: Single KB lookup for both embedding and process configs
			KnowledgeBaseEntity kbEntity = findKnowledgeBase(entity.getKbId());
			IndexConfig embeddingConfig = resolveEmbeddingConfigFromEntity(kbEntity);
			EmbeddingModel embeddingModel = null;
			int embeddingDim = DEFAULT_EMBEDDING_DIM;
			if (embeddingConfig != null) {
				embeddingDim = EmbeddingModelDimension.getDimension(embeddingConfig.getEmbeddingModel(), DEFAULT_EMBEDDING_DIM);
				embeddingModel = modelFactory.getEmbeddingModel(MetadataMode.EMBED, embeddingConfig);
			}

			// Resolve chunk config from the KB
			ProcessConfig processConfig = resolveProcessConfigFromEntity(kbEntity);
			int chunkSize = (processConfig != null && processConfig.getChunkSize() != null && processConfig.getChunkSize() > 0)
					? processConfig.getChunkSize() : DEFAULT_CHUNK_SIZE_CHARS;
			int chunkOverlap = (processConfig != null && processConfig.getChunkOverlap() != null && processConfig.getChunkOverlap() >= 0)
					? processConfig.getChunkOverlap() : DEFAULT_CHUNK_OVERLAP_CHARS;

			// Delete existing RAG index and recreate
			indexSchemaFactory.deleteIndex(destUrl, destUsername, destPassword, entity.getRagIndexName());
			indexSchemaFactory.createRagIndex(destUrl, destUsername, destPassword, entity.getRagIndexName(), embeddingDim);

			entity.setRagProgress(5);
			entity.setGmtModified(new Date());
			this.updateById(entity);

			long ragCount = populateRagIndex(entity, destUrl, destUsername, destPassword, syncToken,
					embeddingModel, chunkSize, chunkOverlap, kbEntity.getWorkspaceId());

			long docCount = countOpenSearchDocs(destUrl, destUsername, destPassword, entity.getIndexName());
			entity.setTotalDocs(docCount);
			entity.setIndexedDocs(docCount);
			entity.setRagDocs(ragCount);
			entity.setRagProgress(100);
			entity.setStatus(STATUS_COMPLETED);
			entity.setLastSyncTime(new Date());
			entity.setGmtModified(new Date());
			this.updateById(entity);
			log.info("RAG reindex completed: syncId={}, ragChunks={}", entity.getSyncId(), ragCount);

			// Remove content from _document if full-text search is disabled
			boolean fullTextSearch = (processConfig == null || processConfig.getFullTextSearch() == null
					|| processConfig.getFullTextSearch());
			if (!fullTextSearch) {
				log.info("full_text_search=false — removing content from document index '{}'",
						entity.getIndexName());
				removeContentFromDocumentIndex(destUrl, destUsername, destPassword, entity.getIndexName());
			}
		}
		catch (Exception e) {
			log.error("RAG reindex only failed: syncId={}", entity.getSyncId(), e);
			entity.setStatus(STATUS_FAILED);
			entity.setErrorMessage(e.getMessage());
			entity.setGmtModified(new Date());
			this.updateById(entity);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> reragDocuments(String syncId, List<String> docIds) {
		if (docIds == null || docIds.isEmpty()) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("doc_ids", "No document IDs provided"));
		}
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		DestConfig dc = resolveDestConfig(entity.getDestinationId());
		if (StringUtils.isBlank(dc.url())) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_url",
					"Destination URL is blank — cannot re-RAG"));
		}

		// Launch async re-RAG for selected documents
		CompletableFuture.runAsync(() -> {
			try {
				reragSelectedDocuments(entity, dc.url(), dc.username(), dc.password(), docIds);
			}
			catch (Exception e) {
				log.error("Re-RAG selected documents failed: syncId={}, docIds={}", syncId, docIds, e);
			}
		}, syncExecutor);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("status", "started");
		result.put("sync_id", syncId);
		result.put("doc_count", docIds.size());
		return result;
	}

	/**
	 * Re-RAG specific documents: fetch them from _document index, delete old chunks
	 * from _rag index, then re-chunk and re-embed.
	 */
	@SuppressWarnings("unchecked")
	private void reragSelectedDocuments(KnowledgeSyncEntity entity, String destUrl,
			String destUsername, String destPassword, List<String> docIds) {
		String docIndexName = entity.getIndexName();
		String ragIndexName = entity.getRagIndexName();

		// Resolve embedding model and chunk config
		KnowledgeBaseEntity kbEntity = findKnowledgeBase(entity.getKbId());
		IndexConfig embeddingConfig = resolveEmbeddingConfigFromEntity(kbEntity);
		EmbeddingModel embeddingModel = null;
		if (embeddingConfig != null) {
			embeddingModel = modelFactory.getEmbeddingModel(MetadataMode.EMBED, embeddingConfig);
		}
		ProcessConfig processConfig = resolveProcessConfigFromEntity(kbEntity);
		int chunkSize = (processConfig != null && processConfig.getChunkSize() != null && processConfig.getChunkSize() > 0)
				? processConfig.getChunkSize() : DEFAULT_CHUNK_SIZE_CHARS;
		int chunkOverlap = (processConfig != null && processConfig.getChunkOverlap() != null && processConfig.getChunkOverlap() >= 0)
				? processConfig.getChunkOverlap() : DEFAULT_CHUNK_OVERLAP_CHARS;

		// Step 1: Delete existing RAG chunks for these documents
		deleteChunksByDocIds(destUrl, destUsername, destPassword, ragIndexName, docIds);

		// Step 2: Fetch document content from _document index by IDs
		HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(destUsername, destPassword);
		headers.setContentType(MediaType.APPLICATION_JSON);

		// Build mget request
		StringBuilder idsJson = new StringBuilder("[");
		for (int i = 0; i < docIds.size(); i++) {
			if (i > 0) idsJson.append(",");
			idsJson.append("\"").append(docIds.get(i).replace("\"", "\\\"")).append("\"");
		}
		idsJson.append("]");

		String mgetEndpoint = destUrl.endsWith("/")
				? destUrl + docIndexName + "/_mget"
				: destUrl + "/" + docIndexName + "/_mget";
		String mgetBody = "{\"ids\":" + idsJson + "}";
		HttpEntity<String> mgetRequest = new HttpEntity<>(mgetBody, headers);

		try {
			ResponseEntity<String> mgetResponse = exchangeWithRetry(
					mgetEndpoint, HttpMethod.POST, mgetRequest, String.class);
			if (mgetResponse.getBody() == null) {
				log.warn("No response from mget for re-RAG, syncId={}", entity.getSyncId());
				return;
			}

			Map<String, Object> mgetResult = objectMapper.readValue(mgetResponse.getBody(),
					new TypeReference<Map<String, Object>>() {});
			List<Map<String, Object>> docs = (List<Map<String, Object>>) mgetResult.get("docs");
			if (docs == null || docs.isEmpty()) {
				log.warn("No documents found for re-RAG, syncId={}", entity.getSyncId());
				return;
			}

			// Step 3: Process each document — chunk, embed, index to RAG
			String bulkEndpoint = destUrl.endsWith("/") ? destUrl + "_bulk" : destUrl + "/_bulk";
			List<String> pendingChunkIds = new ArrayList<>();
			List<Map<String, Object>> pendingChunkDocs = new ArrayList<>();
			List<String> pendingChunkTexts = new ArrayList<>();
			long chunksIndexed = 0;

			for (Map<String, Object> doc : docs) {
				Boolean found = (Boolean) doc.get("found");
				if (found == null || !found) continue;

				String docId = String.valueOf(doc.get("_id"));
				Map<String, Object> docSource = (Map<String, Object>) doc.get("_source");
				if (docSource == null) continue;

				String title = firstNonEmpty(docSource, "cm:title", "cmis:name", "cmis:contentStreamFileName");
				String fileName = firstNonEmpty(docSource, "cmis:contentStreamFileName", "cmis:name");
				String mimeType = firstNonEmpty(docSource, "cmis:contentStreamMimeType", "mime-type");
				String createdBy = firstNonEmpty(docSource, "cmis:createdBy");
				String objectId = firstNonEmpty(docSource, "cmis:objectId");

				// Skip non-processable file types (images, videos, etc.)
				if (!isTikaProcessable(mimeType, fileName)) {
					log.info("Re-RAG: Skipping '{}' (mime={}) — not a Tika-processable type",
							fileName, mimeType);
					continue;
				}

				String content = docSource.get("content") != null
						? String.valueOf(docSource.get("content")) : "";
				content = content.replace('\u00a0', ' ')
						.replace('\u2028', '\n')
						.replace('\u2029', '\n')
						.replace("\u0000", "");

				if (content.trim().length() < 50) {
					log.warn("Re-RAG: Skipping '{}' — too little text ({} chars)", fileName, content.trim().length());
					continue;
				}

				Object authorities = docSource.get("authorities");
				List<String> chunks = chunkTextWithOverlap(content, chunkSize, chunkOverlap);

				for (int i = 0; i < chunks.size(); i++) {
					String chunkId = docId + "_chunk_" + i;
					String chunkContent = chunks.get(i)
							.replace("\u0000", "")
							.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");

					Map<String, Object> chunkDoc = new LinkedHashMap<>();
					chunkDoc.put("chunk_id", chunkId);
					chunkDoc.put("doc_id", docId);
					chunkDoc.put("content", chunkContent);
					chunkDoc.put("chunk_index", i);
					chunkDoc.put("file_title", title);

					Map<String, Object> metadata = new LinkedHashMap<>();
					metadata.put("file_name", fileName);
					metadata.put("mime_type", mimeType);
					metadata.put("created_by", createdBy);
					metadata.put("object_id", objectId);
					metadata.put("total_chunks", chunks.size());
					// Required by VectorStore retriever's FilterExpression
					metadata.put("workspace_id", kbEntity.getWorkspaceId() != null ? kbEntity.getWorkspaceId() : "1");
					metadata.put("enabled", true);
					chunkDoc.put("metadata", metadata);

					if (authorities != null) {
						chunkDoc.put("authorities", authorities);
					}

					pendingChunkIds.add(chunkId);
					pendingChunkDocs.add(chunkDoc);
					pendingChunkTexts.add(chunkContent);

					if (pendingChunkDocs.size() >= MAX_CHUNKS_PER_BULK) {
						int accepted = flushRagBatch(ragIndexName, bulkEndpoint, destUsername, destPassword,
								pendingChunkIds, pendingChunkDocs, pendingChunkTexts, embeddingModel);
						chunksIndexed += accepted;
						pendingChunkIds.clear();
						pendingChunkDocs.clear();
						pendingChunkTexts.clear();
					}
				}
				log.info("Re-RAG: processed '{}' → {} chunks", fileName, chunks.size());
			}

			// Flush remaining
			if (!pendingChunkDocs.isEmpty()) {
				int accepted = flushRagBatch(ragIndexName, bulkEndpoint, destUsername, destPassword,
						pendingChunkIds, pendingChunkDocs, pendingChunkTexts, embeddingModel);
				chunksIndexed += accepted;
			}

			// Update RAG doc count
			long totalRagCount = countOpenSearchDocs(destUrl, destUsername, destPassword, ragIndexName);
			entity.setRagDocs(totalRagCount);
			entity.setGmtModified(new Date());
			this.updateById(entity);

			log.info("Re-RAG completed for {} documents: {} new chunks indexed, total RAG chunks: {}",
					docIds.size(), chunksIndexed, totalRagCount);
		}
		catch (Exception e) {
			log.error("Re-RAG failed for syncId={}: {}", entity.getSyncId(), e.getMessage(), e);
		}
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> listSyncDocuments(String kbId, int current, int size, String query) {
		// Find sync for this KB to get destination info and index name
		KnowledgeSync sync = getSyncByKbId(kbId);
		if (sync == null) {
			Map<String, Object> empty = new LinkedHashMap<>();
			empty.put("records", List.of());
			empty.put("total", 0);
			empty.put("current", current);
			empty.put("size", size);
			return empty;
		}

		DestinationEntity dest = findDestination(sync.getDestinationId());
		if (dest == null) {
			Map<String, Object> empty = new LinkedHashMap<>();
			empty.put("records", List.of());
			empty.put("total", 0);
			empty.put("current", current);
			empty.put("size", size);
			return empty;
		}

		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		// Use the sync's actual document index name instead of wildcard patterns
		String indexName = sync.getIndexName();
		return searchOpenSearchDocs(destUrl, destUsername, destPassword, indexName, current, size, query);
	}

	/**
	 * Search OpenSearch document index for documents, returning paginated results.
	 */
	private Map<String, Object> searchOpenSearchDocs(String destUrl, String username, String password,
			String indexName, int current, int size, String queryText) {
		int from = (current - 1) * size;

		{
			try {
				String endpoint = destUrl.endsWith("/")
						? destUrl + indexName + "/_search"
						: destUrl + "/" + indexName + "/_search";
				HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(username, password);
				headers.setContentType(MediaType.APPLICATION_JSON);

				// Build search query — include both normalized and CMIS field names
				// since MCF writes raw CMIS properties (cmis:name, cm:title, etc.)
				String sourceFields = "\"_source\":[\"file_title\",\"file_name\",\"file_path\",\"file_size\",\"file_type\","
						+ "\"content\",\"created_at\",\"updated_at\",\"allow_token_document\",\"deny_token_document\","
						+ "\"cmis:name\",\"cm:title\",\"cmis:contentStreamFileName\",\"cmis:contentStreamMimeType\","
						+ "\"cmis:contentStreamLength\",\"cmis:objectId\",\"cmis:createdBy\"]";
				String searchBody;
				if (StringUtils.isNotBlank(queryText)) {
					searchBody = "{\"from\":" + from + ",\"size\":" + size
							+ ",\"query\":{\"multi_match\":{\"query\":" + objectMapper.writeValueAsString(queryText)
							+ ",\"fields\":[\"content\",\"file_title\",\"file_name\",\"file_path\",\"cmis:name\",\"cm:title\",\"cmis:contentStreamFileName\"]}}"
							+ "," + sourceFields
							+ ",\"sort\":[{\"_score\":\"desc\"},{\"cmis:name.keyword\":{\"order\":\"asc\",\"missing\":\"_last\",\"unmapped_type\":\"keyword\"}}]"
							+ ",\"track_total_hits\":true}";
				}
				else {
					searchBody = "{\"from\":" + from + ",\"size\":" + size
							+ ",\"query\":{\"match_all\":{}}"
							+ "," + sourceFields
							+ ",\"sort\":[{\"cmis:name.keyword\":{\"order\":\"asc\",\"missing\":\"_last\",\"unmapped_type\":\"keyword\"}}]"
							+ ",\"track_total_hits\":true}";
				}

				HttpEntity<String> request = new HttpEntity<>(searchBody, headers);
				ResponseEntity<String> response = exchangeWithRetry(
						endpoint, HttpMethod.POST, request, String.class);

				if (response.getBody() != null) {
					Map<String, Object> body = objectMapper.readValue(response.getBody(),
							new TypeReference<Map<String, Object>>() {
							});
					Map<String, Object> hits = (Map<String, Object>) body.get("hits");
					if (hits != null) {
						// Extract total
						Object totalObj = hits.get("total");
						long total = 0;
						if (totalObj instanceof Map) {
							Object val = ((Map<String, Object>) totalObj).get("value");
							if (val instanceof Number) {
								total = ((Number) val).longValue();
							}
						}
						else if (totalObj instanceof Number) {
							total = ((Number) totalObj).longValue();
						}

						// Extract hit records
						List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
						List<Map<String, Object>> records = new ArrayList<>();
						if (hitList != null) {
							for (Map<String, Object> hit : hitList) {
								Map<String, Object> source = (Map<String, Object>) hit.get("_source");
								if (source == null) {
									source = new HashMap<>();
								}
								Map<String, Object> record = new LinkedHashMap<>();
								record.put("doc_id", hit.get("_id"));
								record.put("kb_id", hit.get("_index") != null ? String.valueOf(hit.get("_index")) : "");
								// Use CMIS field names as fallback since MCF writes raw CMIS properties
								record.put("name", firstNonEmpty(source,
										"file_title", "cm:title", "cmis:name",
										"cmis:contentStreamFileName", "file_name", "file_path"));
								if ("".equals(record.get("name"))) {
									record.put("name", "Unknown");
								}
								record.put("format", detectFormat(
										String.valueOf(firstNonEmpty(source,
												"file_type", "cmis:contentStreamMimeType")),
										String.valueOf(firstNonEmpty(source,
												"file_name", "cmis:contentStreamFileName",
												"cmis:name", "file_title"))));
								Object fileSize = source.get("file_size");
								if (fileSize == null) {
									fileSize = source.get("cmis:contentStreamLength");
								}
								long sizeValue = 0L;
								if (fileSize instanceof Number) {
									sizeValue = ((Number) fileSize).longValue();
								}
								else if (fileSize instanceof String) {
									try {
										sizeValue = Long.parseLong(((String) fileSize).trim());
									}
									catch (NumberFormatException ignored) {
									}
								}
								record.put("size", sizeValue);
								record.put("index_status", "processed");
								record.put("enabled", true);
								record.put("path", source.getOrDefault("file_path", ""));
								record.put("source", "opensearch");
								records.add(record);
							}
						}

						Map<String, Object> result = new LinkedHashMap<>();
						result.put("records", records);
						result.put("total", total);
						result.put("current", current);
						result.put("size", size);
						return result;
					}
				}
			}
			catch (Exception e) {
				log.debug("Could not search docs in index '{}': {}", indexName, e.getMessage());
			}
		}

		// Fallback empty result
		Map<String, Object> empty = new LinkedHashMap<>();
		empty.put("records", List.of());
		empty.put("total", 0);
		empty.put("current", current);
		empty.put("size", size);
		return empty;
	}



	@Override
	public Map<String, Object> hardReset(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		// 1. Abort MCF job if running
		// Invalidate any running async thread for this sync
		activeSyncTokens.remove(entity.getSyncId());
		if (StringUtils.isNotBlank(entity.getMcfJobId())) {
			try {
				mcfBridge.abortJob(entity.getMcfJobId());
				mcfBridge.deleteJob(entity.getMcfJobId());
				log.info("Aborted and deleted MCF job {} for hard reset", entity.getMcfJobId());
			}
			catch (Exception e) {
				log.warn("Failed to abort/delete MCF job during hard reset: {}", e.getMessage());
			}
		}

		// 1b. Delete per-KB MCF output connection
		try {
			String perKbOutputConnName = "KB_" + entity.getKbId();
			mcfBridge.deleteOutputConnection(perKbOutputConnName);
		}
		catch (Exception e) {
			log.debug("Could not delete per-KB output connection: {}", e.getMessage());
		}

		// 2. Delete all 3 indices from OpenSearch
		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest != null) {
			Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
			String destUrl = getConfigString(destConfig, "url", "");
			String destUsername = getConfigString(destConfig, "username", "");
			String destPassword = getConfigString(destConfig, "password", "");

			if (StringUtils.isNotBlank(entity.getIndexName())) {
				indexSchemaFactory.deleteIndex(destUrl, destUsername, destPassword, entity.getIndexName());
			}
			if (StringUtils.isNotBlank(entity.getAuthorityIndexName())) {
				indexSchemaFactory.deleteIndex(destUrl, destUsername, destPassword, entity.getAuthorityIndexName());
			}
			if (StringUtils.isNotBlank(entity.getRagIndexName())) {
				indexSchemaFactory.deleteIndex(destUrl, destUsername, destPassword, entity.getRagIndexName());
			}
		}

		// 3. Reset entity to pending state
		entity.setStatus(STATUS_PENDING);
		entity.setMcfJobId("");
		entity.setIndexProgress(0);
		entity.setRagProgress(0);
		entity.setTotalDocs(0L);
		entity.setIndexedDocs(0L);
		entity.setRagDocs(0L);
		entity.setFailedDocs(0L);
		entity.setErrorMessage("");
		entity.setGmtModified(new Date());
		this.updateById(entity);

		log.info("Hard reset completed for sync {}", syncId);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("sync_id", syncId);
		result.put("status", "pending");
		result.put("message", "Hard reset completed. All indices deleted. Ready for fresh sync.");
		return result;
	}

	@Override
	public Map<String, Object> stopSync(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		String currentStatus = entity.getStatus();
		if ("completed".equals(currentStatus) || "failed".equals(currentStatus) || "pending".equals(currentStatus)) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("sync_id", syncId);
			result.put("status", currentStatus);
			result.put("message", "Sync is not running (status: " + currentStatus + ")");
			return result;
		}

		// Abort MCF job if running
		// Invalidate any running async thread for this sync
		activeSyncTokens.remove(entity.getSyncId());
		if (StringUtils.isNotBlank(entity.getMcfJobId())) {
			try {
				mcfBridge.abortJob(entity.getMcfJobId());
				log.info("Aborted MCF job {} for stop sync", entity.getMcfJobId());
			}
			catch (Exception e) {
				log.warn("Failed to abort MCF job during stop: {}", e.getMessage());
			}
		}

		entity.setStatus(STATUS_FAILED);
		entity.setErrorMessage("Manually stopped by user");
		entity.setGmtModified(new Date());
		this.updateById(entity);

		log.info("Sync {} stopped manually", syncId);

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("sync_id", syncId);
		result.put("status", "failed");
		result.put("message", "Sync stopped. MCF job aborted.");
		return result;
	}

	@Override
	@SuppressWarnings("unchecked")
	public Map<String, Object> browseDocumentIndex(String syncId, int current, int size, String query) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}

		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		String indexName = entity.getIndexName();
		if (StringUtils.isBlank(indexName)) {
			Map<String, Object> empty = new LinkedHashMap<>();
			empty.put("records", List.of());
			empty.put("total", 0);
			empty.put("current", current);
			empty.put("size", size);
			empty.put("index_name", "");
			return empty;
		}

		Map<String, Object> result = searchOpenSearchDocs(destUrl, destUsername, destPassword, indexName, current, size, query);
		result.put("index_name", indexName);
		result.put("authority_index_name", entity.getAuthorityIndexName());
		result.put("rag_index_name", entity.getRagIndexName());
		return result;
	}

	private String detectFormat(String fileType, String fileName) {
		if (StringUtils.isNotBlank(fileType)) {
			String upper = fileType.toUpperCase();
			if (upper.contains("PDF")) return "PDF";
			if (upper.contains("DOC") || upper.contains("WORD")) return "DOC";
			if (upper.contains("PPT") || upper.contains("PRESENTATION")) return "PPT";
			if (upper.contains("TXT") || upper.contains("TEXT")) return "TXT";
			if (upper.contains("MD") || upper.contains("MARKDOWN")) return "MD";
			if (upper.contains("XLS") || upper.contains("SPREADSHEET")) return "XLS";
			if (upper.contains("HTML")) return "HTML";
		}
		if (StringUtils.isNotBlank(fileName)) {
			String lower = fileName.toLowerCase();
			if (lower.endsWith(".pdf")) return "PDF";
			if (lower.endsWith(".doc") || lower.endsWith(".docx")) return "DOC";
			if (lower.endsWith(".ppt") || lower.endsWith(".pptx")) return "PPT";
			if (lower.endsWith(".txt")) return "TXT";
			if (lower.endsWith(".md")) return "MD";
			if (lower.endsWith(".xls") || lower.endsWith(".xlsx")) return "XLS";
			if (lower.endsWith(".html") || lower.endsWith(".htm")) return "HTML";
		}
		return "TXT";
	}

	// ─── Document Detail, Chunks, and Metadata APIs ────────────────────────

	@Override
	public Map<String, Object> getDocumentDetail(String syncId, String docId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}
		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		try {
			// Use _search with ids query instead of _doc/{id} because doc IDs can be URLs
			String endpoint = destUrl.endsWith("/")
					? destUrl + entity.getIndexName() + "/_search"
					: destUrl + "/" + entity.getIndexName() + "/_search";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);
			String searchBody = "{\"size\":1,\"query\":{\"ids\":{\"values\":["
					+ objectMapper.writeValueAsString(docId) + "]}}}";
			HttpEntity<String> request = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					endpoint, HttpMethod.POST, request, String.class);

			if (response.getBody() != null) {
				Map<String, Object> body = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {
						});
				Map<String, Object> hits = (Map<String, Object>) body.get("hits");
				List<Map<String, Object>> hitList = hits != null
						? (List<Map<String, Object>>) hits.get("hits") : null;
				Map<String, Object> source = (hitList != null && !hitList.isEmpty())
						? (Map<String, Object>) hitList.get(0).get("_source") : null;
				if (source == null) {
					source = new HashMap<>();
				}
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("doc_id", docId);
				result.put("index_name", entity.getIndexName());
				// Include all source fields for metadata display
				result.putAll(source);
				return result;
			}
		}
		catch (Exception e) {
			log.warn("Failed to get document detail for {}: {}", docId, e.getMessage());
		}
		return Map.of("doc_id", docId, "error", "Document not found");
	}

	@Override
	public Map<String, Object> getDocumentChunks(String syncId, String docId, int current, int size) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}
		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		String ragIndex = entity.getRagIndexName();
		if (StringUtils.isBlank(ragIndex)) {
			return Map.of("records", List.of(), "total", 0, "current", current, "size", size);
		}

		int from = (current - 1) * size;
		try {
			String endpoint = destUrl.endsWith("/")
					? destUrl + ragIndex + "/_search"
					: destUrl + "/" + ragIndex + "/_search";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Search RAG chunks by doc_id (keyword field storing the parent document's OpenSearch _id)
			String jsonDocId = objectMapper.writeValueAsString(docId);
			String searchBody = "{\"from\":" + from + ",\"size\":" + size
					+ ",\"query\":{\"term\":{\"doc_id\":" + jsonDocId + "}}"
					+ ",\"sort\":[{\"chunk_index\":{\"order\":\"asc\",\"missing\":\"_last\",\"unmapped_type\":\"integer\"}},"
					+ "{\"_id\":{\"order\":\"asc\"}}]"
					+ ",\"track_total_hits\":true}";

			HttpEntity<String> request = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = exchangeWithRetry(
					endpoint, HttpMethod.POST, request, String.class);

			if (response.getBody() != null) {
				Map<String, Object> body = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {
						});
				Map<String, Object> hits = (Map<String, Object>) body.get("hits");
				if (hits != null) {
					Object totalObj = hits.get("total");
					long total = 0;
					if (totalObj instanceof Map) {
						Object val = ((Map<String, Object>) totalObj).get("value");
						if (val instanceof Number) {
							total = ((Number) val).longValue();
						}
					}
					else if (totalObj instanceof Number) {
						total = ((Number) totalObj).longValue();
					}

					List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
					List<Map<String, Object>> records = new ArrayList<>();
					if (hitList != null) {
						for (Map<String, Object> hit : hitList) {
							Map<String, Object> source = (Map<String, Object>) hit.get("_source");
							if (source == null) {
								source = new HashMap<>();
							}
							Map<String, Object> record = new LinkedHashMap<>();
							record.put("chunk_id", hit.get("_id"));
							record.put("content", source.getOrDefault("content", ""));
							record.put("chunk_index", source.getOrDefault("chunk_index", 0));
							record.put("parent_doc_id", source.getOrDefault("parent_doc_id", docId));
							record.put("parent_doc_name", source.getOrDefault("parent_doc_name", ""));
							records.add(record);
						}
					}

					Map<String, Object> result = new LinkedHashMap<>();
					result.put("records", records);
					result.put("total", total);
					result.put("current", current);
					result.put("size", size);
					return result;
				}
			}
		}
		catch (Exception e) {
			log.warn("Failed to get chunks for doc {}: {}", docId, e.getMessage());
		}
		return Map.of("records", List.of(), "total", 0, "current", current, "size", size);
	}

	@Override
	public Map<String, Object> updateChunkContent(String syncId, String chunkId, String content) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}
		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		String ragIndex = entity.getRagIndexName();
		if (StringUtils.isBlank(ragIndex)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("rag_index", "RAG index not configured"));
		}

		try {
			// Use _update_by_query with ids query because chunk IDs can be URLs with special chars
			String endpoint = destUrl.endsWith("/")
					? destUrl + ragIndex + "/_update_by_query"
					: destUrl + "/" + ragIndex + "/_update_by_query";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);

			Map<String, Object> updateBody = new LinkedHashMap<>();
			updateBody.put("query", Map.of("ids", Map.of("values", List.of(chunkId))));
			updateBody.put("script", Map.of(
					"source", "ctx._source['content'] = params.content",
					"params", Map.of("content", content)));
			String body = objectMapper.writeValueAsString(updateBody);
			HttpEntity<String> request = new HttpEntity<>(body, headers);
			exchangeWithRetry(endpoint, HttpMethod.POST, request, String.class);

			return Map.of("chunk_id", chunkId, "status", "updated");
		}
		catch (Exception e) {
			log.warn("Failed to update chunk {}: {}", chunkId, e.getMessage());
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("update", "Failed to update chunk: " + e.getMessage()));
		}
	}

	@Override
	public Map<String, Object> updateDocumentMetadata(String syncId, String docId, Map<String, Object> metadata) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}
		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		try {
			// Use _update_by_query with ids query because doc IDs can be URLs with special chars
			String endpoint = destUrl.endsWith("/")
					? destUrl + entity.getIndexName() + "/_update_by_query"
					: destUrl + "/" + entity.getIndexName() + "/_update_by_query";
			HttpHeaders headers = OpenSearchUtils.buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Only allow updating safe metadata fields (not content or ACLs)
			// B7: Validate keys against safe pattern to prevent Painless script injection
			java.util.regex.Pattern safeKeyPattern = java.util.regex.Pattern.compile("^[a-zA-Z0-9_:.\\-]+$");
			Map<String, Object> safeFields = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : metadata.entrySet()) {
				String key = entry.getKey();
				// Block modification of system fields and ACL tokens
				if (!key.startsWith("allow_token_") && !key.startsWith("deny_token_")
						&& !"_id".equals(key) && !"_index".equals(key)
						&& safeKeyPattern.matcher(key).matches()) {
					safeFields.put(key, entry.getValue());
				}
				else if (!safeKeyPattern.matcher(key).matches()) {
					log.warn("Rejected unsafe metadata key '{}' — does not match allowed pattern", key);
				}
			}

			// Build painless script to set each field
			StringBuilder script = new StringBuilder();
			for (String key : safeFields.keySet()) {
				script.append("ctx._source['").append(key.replace("'", "\\'")).append("'] = params['")
						.append(key.replace("'", "\\'")).append("']; ");
			}
			Map<String, Object> updateBody = new LinkedHashMap<>();
			Map<String, Object> queryObj = Map.of("ids", Map.of("values", List.of(docId)));
			updateBody.put("query", queryObj);
			updateBody.put("script", Map.of("source", script.toString().trim(), "params", safeFields));

			String body = objectMapper.writeValueAsString(updateBody);
			HttpEntity<String> request = new HttpEntity<>(body, headers);
			exchangeWithRetry(endpoint, HttpMethod.POST, request, String.class);

			return Map.of("doc_id", docId, "status", "updated", "updated_fields", safeFields.keySet());
		}
		catch (Exception e) {
			log.warn("Failed to update document metadata for {}: {}", docId, e.getMessage());
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("update", "Failed to update metadata: " + e.getMessage()));
		}
	}

	@Override
	public Map<String, Object> downloadSourceDocument(String syncId, String docId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}

		// Must be a source-based sync
		if (StringUtils.isBlank(entity.getSourceId())) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id",
					"Download is only supported for source-based knowledge bases"));
		}

		SourceSystemEntity source = findSource(entity.getSourceId());
		if (source == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		if (StringUtils.isBlank(docId)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("doc_id", "Document ID is required"));
		}

		// Extract connection details from the source system config
		Map<String, Object> sourceConfig = deserializeConfig(source.getConnectionConfig());
		String sourceUsername = getConfigString(sourceConfig, "username", "");
		String sourcePassword = getConfigString(sourceConfig, "password", "");
		String sourceProtocol = getConfigString(sourceConfig, "protocol", "https");
		String sourceServer = getConfigString(sourceConfig, "server", "");
		String sourcePort = getConfigString(sourceConfig, "port", "443");
		String sourcePath = getConfigString(sourceConfig, "path", "");

		// Build the CMIS AtomPub content stream URL from source config + objectId.
		// For a CMIS AtomPub endpoint like {proto}://{server}:{port}{path},
		// the content stream is at {proto}://{server}:{port}{path}/content?id={objectId}
		// If docId is already a full URL (content stream URL), use it directly.
		String downloadUrl;
		String fileName;
		if (docId.startsWith("http://") || docId.startsWith("https://")) {
			// Legacy: docId is already a content stream URL
			downloadUrl = docId;
			if ("https".equalsIgnoreCase(sourceProtocol) && downloadUrl.startsWith("http://")) {
				downloadUrl = "https://" + downloadUrl.substring(7);
			}
			fileName = extractFileNameFromUrl(docId);
		}
		else {
			// docId is a CMIS objectId (e.g., "ca7b6a70-56f0-4f81-...:1.1")
			// Two-step resolution:
			// 1. Discover the real CMIS base URL from the service document's URI templates
			//    (the connection config path may differ from the actual endpoint path,
			//    e.g., /cmis/ vs /public/cmis/ in Alfresco).
			// 2. Fetch the Atom entry at {realBase}/id?id={objectId} and extract the
			//    <content src="..."> attribute which has the full content stream URL.
			String encodedId = URLEncoder.encode(docId, StandardCharsets.UTF_8);
			String serviceDocUrl = sourceProtocol + "://" + sourceServer + ":" + sourcePort + sourcePath;
			String realBase = resolveRealCmisBaseUrl(serviceDocUrl, sourceUsername, sourcePassword, sourceProtocol);
			if (realBase == null) {
				// Fallback: use the config path directly (may work for some CMIS implementations)
				realBase = serviceDocUrl;
			}
			String entryUrl = realBase + "/id?id=" + encodedId;
			log.info("Resolving CMIS content URL via Atom entry: {} (objectId: {})", entryUrl, docId);

			downloadUrl = resolveCmisContentUrlFromEntry(entryUrl, sourceUsername, sourcePassword, sourceProtocol);
			if (downloadUrl == null) {
				throw new BizException(ErrorCode.INVALID_PARAMS.toError("download",
						"Could not resolve content stream URL from CMIS entry for objectId: " + docId));
			}
			fileName = extractFileNameFromUrl(downloadUrl);
			log.info("Resolved CMIS content URL: {} (filename: {})", downloadUrl, fileName);
		}

		try {
			// Use raw HttpURLConnection to support SSL trust-all for self-signed certs
			URI uri = URI.create(downloadUrl);
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();

			configureTrustAllSsl(conn);

			conn.setRequestMethod("GET");
			conn.setConnectTimeout(30_000);
			conn.setReadTimeout(60_000);

			// Add Basic auth from source credentials
			if (StringUtils.isNotBlank(sourceUsername)) {
				String credentials = sourceUsername + ":" + sourcePassword;
				String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
				conn.setRequestProperty("Authorization", "Basic " + encoded);
			}

			int httpStatus = conn.getResponseCode();
			if (httpStatus < 200 || httpStatus >= 300) {
				String errorBody = "";
				try (InputStream errStream = conn.getErrorStream()) {
					if (errStream != null) {
						errorBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
					}
				}
				catch (Exception ignored) {
				}
				log.warn("CMIS download failed with HTTP {}: {} — {}", httpStatus, downloadUrl, errorBody);
				throw new BizException(ErrorCode.INVALID_PARAMS.toError("download",
						"Source system returned HTTP " + httpStatus));
			}

			// Read content type
			String contentType = conn.getContentType();
			if (StringUtils.isBlank(contentType)) {
				contentType = "application/octet-stream";
			}

			// Extract filename from Content-Disposition header if available
			String disposition = conn.getHeaderField("Content-Disposition");
			log.debug("Content-Disposition header: {}", disposition);
			if (StringUtils.isNotBlank(disposition) && disposition.contains("filename")) {
				// Handle both filename="name" and filename=name (with/without quotes)
				String cdFileName = disposition.replaceFirst("(?i).*filename\\*?=\"?([^\";\n]+)\"?.*", "$1").trim();
				if (StringUtils.isNotBlank(cdFileName) && !cdFileName.equals(disposition)) {
					fileName = cdFileName;
					log.debug("Extracted filename from Content-Disposition: {}", fileName);
				}
			}

			// Read content into byte array
			byte[] content;
			try (InputStream in = conn.getInputStream();
					ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				byte[] buffer = new byte[8192];
				int bytesRead;
				while ((bytesRead = in.read(buffer)) != -1) {
					baos.write(buffer, 0, bytesRead);
				}
				content = baos.toByteArray();
			}

			log.info("Downloaded source document '{}' ({} bytes) for sync {}", fileName, content.length, syncId);

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("content", content);
			result.put("contentType", contentType);
			result.put("fileName", fileName);
			return result;
		}
		catch (BizException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("Failed to download source document for sync {}: {}", syncId, e.getMessage(), e);
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("download",
					"Failed to download document: " + e.getMessage()));
		}
	}

	/**
	 * Resolve the real CMIS base URL by fetching the service document and extracting
	 * the {@code objectbyid} URI template. Alfresco (and some other vendors) expose their
	 * CMIS endpoints on a different path than the connection config path
	 * (e.g., {@code /public/cmis/} vs {@code /cmis/}). The service document URI templates
	 * contain the actual endpoint URLs.
	 *
	 * @return the real base URL (up to and including {@code /atom}), or null on failure
	 */
	private String resolveRealCmisBaseUrl(String serviceDocUrl, String username,
			String password, String sourceProtocol) {
		HttpURLConnection conn = null;
		try {
			URI uri = URI.create(serviceDocUrl);
			conn = (HttpURLConnection) uri.toURL().openConnection();
			configureTrustAllSsl(conn);
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(10_000);
			conn.setReadTimeout(10_000);
			conn.setRequestProperty("Connection", "close");

			if (StringUtils.isNotBlank(username)) {
				String credentials = username + ":" + password;
				String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
				conn.setRequestProperty("Authorization", "Basic " + encoded);
			}

			int status = conn.getResponseCode();
			if (status < 200 || status >= 300) {
				log.warn("CMIS service document fetch failed with HTTP {}: {}", status, serviceDocUrl);
				return null;
			}

			String serviceXml;
			try (InputStream in = conn.getInputStream()) {
				serviceXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}

			// Look for the objectbyid URI template:
			// <cmisra:template>http://.../atom/id?id={id}&amp;...</cmisra:template>
			// <cmisra:type>objectbyid</cmisra:type>
			java.util.regex.Matcher matcher = java.util.regex.Pattern
					.compile("<cmisra:template>([^<]+)</cmisra:template>\\s*<cmisra:type>objectbyid</cmisra:type>")
					.matcher(serviceXml);
			if (matcher.find()) {
				String templateUrl = matcher.group(1).replace("&amp;", "&");
				// Extract the base URL up to "/atom" from something like:
				// http://server:8080/.../atom/id?id={id}&filter=...
				int atomIdx = templateUrl.indexOf("/atom/");
				if (atomIdx > 0) {
					String realBase = templateUrl.substring(0, atomIdx + "/atom".length());
					// Fix protocol if needed
					if ("https".equalsIgnoreCase(sourceProtocol) && realBase.startsWith("http://")) {
						realBase = "https://" + realBase.substring(7);
					}
					log.info("Resolved real CMIS base URL from service document: {}", realBase);
					return realBase;
				}
			}

			log.warn("Could not find objectbyid URI template in CMIS service document: {}", serviceDocUrl);
			return null;
		}
		catch (Exception e) {
			log.error("Failed to resolve real CMIS base URL from {}: {}", serviceDocUrl, e.getMessage(), e);
			return null;
		}
		finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	/**
	 * Resolve the actual content stream URL from a CMIS AtomPub entry.
	 * Fetches the Atom entry XML and extracts the {@code <content src="...">} attribute
	 * which points to the real content download URL (including filename in path).
	 */
	private String resolveCmisContentUrlFromEntry(String entryUrl, String username,
			String password, String sourceProtocol) {
		try {
			URI uri = URI.create(entryUrl);
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
			configureTrustAllSsl(conn);
			conn.setRequestMethod("GET");
			conn.setConnectTimeout(15_000);
			conn.setReadTimeout(15_000);

			if (StringUtils.isNotBlank(username)) {
				String credentials = username + ":" + password;
				String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
				conn.setRequestProperty("Authorization", "Basic " + encoded);
			}

			int status = conn.getResponseCode();
			if (status < 200 || status >= 300) {
				log.warn("CMIS entry fetch failed with HTTP {}: {}", status, entryUrl);
				return null;
			}

			// Read the Atom XML response
			String atomXml;
			try (InputStream in = conn.getInputStream()) {
				atomXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}

			// Extract <content src="..."> attribute using regex (avoid XML parser dependency)
			// The content element looks like: <atom:content src="http://..."/> (with namespace prefix)
			java.util.regex.Matcher matcher = java.util.regex.Pattern
					.compile("<(?:atom:)?content[^>]+src=\"([^\"]+)\"")
					.matcher(atomXml);
			if (matcher.find()) {
				String contentUrl = matcher.group(1);
				// Un-escape XML entities
				contentUrl = contentUrl.replace("&amp;", "&");
				// Fix http:// to https:// when source uses HTTPS (reverse proxy)
				if ("https".equalsIgnoreCase(sourceProtocol) && contentUrl.startsWith("http://")) {
					contentUrl = "https://" + contentUrl.substring(7);
				}
				return contentUrl;
			}

			log.warn("No <content src=..> found in CMIS Atom entry for {}", entryUrl);
			return null;
		}
		catch (Exception e) {
			log.error("Failed to resolve CMIS content URL from entry {}: {}", entryUrl, e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Extract a reasonable filename from a CMIS content stream URL.
	 * The URL path typically contains the filename, e.g.,
	 * .../content/My%20Document.docx?id=...
	 */
	private String extractFileNameFromUrl(String url) {
		try {
			// Strip query string
			String path = url.contains("?") ? url.substring(0, url.indexOf('?')) : url;
			// Get last path segment
			String lastSegment = path.substring(path.lastIndexOf('/') + 1);
			// URL-decode
			String decoded = java.net.URLDecoder.decode(lastSegment, StandardCharsets.UTF_8);
			if (StringUtils.isNotBlank(decoded)) {
				return decoded;
			}
		}
		catch (Exception e) {
			log.debug("Could not extract filename from URL: {}", e.getMessage());
		}
		return "document";
	}

	// ── CMIS Connection Helper ──────────────────────────────────────────

	/**
	 * Data holder for resolved CMIS connection details.
	 */
	private record CmisConnectionInfo(String username, String password, String protocol,
			String server, String port, String path, String serviceDocUrl, String realBaseUrl) {
	}

	/**
	 * Resolve CMIS connection details from a sync job, including the real CMIS base URL.
	 */
	private CmisConnectionInfo resolveCmisConnection(String syncId) {
		KnowledgeSyncEntity entity = findBySyncId(syncId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("sync_id", "Sync job not found"));
		}
		if (StringUtils.isBlank(entity.getSourceId())) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id",
					"CMIS browse is only supported for source-based knowledge bases"));
		}
		SourceSystemEntity source = findSource(entity.getSourceId());
		if (source == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		Map<String, Object> sourceConfig = deserializeConfig(source.getConnectionConfig());
		String username = getConfigString(sourceConfig, "username", "");
		String password = getConfigString(sourceConfig, "password", "");
		String protocol = getConfigString(sourceConfig, "protocol", "https");
		String server = getConfigString(sourceConfig, "server", "");
		String port = getConfigString(sourceConfig, "port", "443");
		String path = getConfigString(sourceConfig, "path", "");

		String serviceDocUrl = protocol + "://" + server + ":" + port + path;
		String realBase = resolveRealCmisBaseUrl(serviceDocUrl, username, password, protocol);
		if (realBase == null) {
			realBase = serviceDocUrl;
		}
		return new CmisConnectionInfo(username, password, protocol, server, port, path, serviceDocUrl, realBase);
	}

	/**
	 * Open an authenticated HTTP connection to a CMIS URL.
	 * Uses Connection: close to avoid keep-alive pool exhaustion in Docker.
	 */
	private HttpURLConnection openCmisConnection(String url, String method, CmisConnectionInfo cmis) throws Exception {
		URI uri = URI.create(url);
		HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();
		configureTrustAllSsl(conn);
		conn.setRequestMethod(method);
		conn.setConnectTimeout(30_000);
		conn.setReadTimeout(60_000);
		conn.setRequestProperty("Connection", "close");
		conn.setRequestProperty("Accept", "application/atom+xml, application/xml, text/xml, */*");

		if (StringUtils.isNotBlank(cmis.username())) {
			String credentials = cmis.username() + ":" + cmis.password();
			String encoded = Base64.getEncoder().encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
			conn.setRequestProperty("Authorization", "Basic " + encoded);
		}
		return conn;
	}

	/**
	 * Fix protocol for URLs returned by CMIS (reverse proxy fix: http→https).
	 */
	private String fixCmisProtocol(String url, String protocol) {
		if ("https".equalsIgnoreCase(protocol) && url != null && url.startsWith("http://")) {
			return "https://" + url.substring(7);
		}
		return url;
	}

	// ── CMIS Browse Implementation ──────────────────────────────────────

	@Override
	public Map<String, Object> browseCmisFolder(String syncId, String folderId) {
		CmisConnectionInfo cmis = resolveCmisConnection(syncId);

		try {
			// If no folderId, get root folder ID from the service document
			if (StringUtils.isBlank(folderId)) {
				folderId = resolveRootFolderId(cmis);
				if (folderId == null) {
					throw new BizException(ErrorCode.INVALID_PARAMS.toError("folder",
							"Could not resolve CMIS root folder"));
				}
			}

			String encodedId = URLEncoder.encode(folderId, StandardCharsets.UTF_8);
			String childrenUrl = cmis.realBaseUrl() + "/children?id=" + encodedId;
			log.info("CMIS browse folder: {} (folderId: {})", childrenUrl, folderId);

			List<Map<String, Object>> items = new ArrayList<>();
			String folderName = "Root";

			// Fetch children feed — may be paginated
			String currentUrl = childrenUrl;
			while (currentUrl != null) {
				HttpURLConnection conn = openCmisConnection(currentUrl, "GET", cmis);
				try {
					log.info("CMIS browse: connecting to {}", currentUrl);
					int status = conn.getResponseCode();
					log.info("CMIS browse: got HTTP {}", status);
					if (status < 200 || status >= 300) {
						String errBody = "";
						try (InputStream errStream = conn.getErrorStream()) {
							if (errStream != null)
								errBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
						}
						catch (Exception ignored) {
						}
						throw new BizException(ErrorCode.INVALID_PARAMS.toError("cmis_browse",
								"CMIS returned HTTP " + status + ": " + errBody));
					}

					String atomXml;
					try (InputStream in = conn.getInputStream()) {
						atomXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
					}
					log.info("CMIS browse: read {} bytes of Atom XML", atomXml.length());

					// Parse the folder name from <atom:title> at feed level
					java.util.regex.Matcher feedTitle = java.util.regex.Pattern
							.compile("<(?:atom:)?feed[^>]*>[\\s\\S]*?<(?:atom:)?title[^>]*>([^<]+)</(?:atom:)?title>")
							.matcher(atomXml);
					if (feedTitle.find() && "Root".equals(folderName)) {
						folderName = feedTitle.group(1).trim();
					}

					// Parse each <atom:entry>
					java.util.regex.Pattern entryPattern = java.util.regex.Pattern
							.compile("<(?:atom:)?entry>(.*?)</(?:atom:)?entry>", java.util.regex.Pattern.DOTALL);
					java.util.regex.Matcher entryMatcher = entryPattern.matcher(atomXml);

					while (entryMatcher.find()) {
						String entry = entryMatcher.group(1);
						Map<String, Object> item = parseCmisEntry(entry, cmis.protocol());
						if (item != null) {
							items.add(item);
						}
					}

					// Check for next page link: <atom:link rel="next" href="..."/>
					java.util.regex.Matcher nextLink = java.util.regex.Pattern
							.compile("<(?:atom:)?link[^>]+rel=\"next\"[^>]+href=\"([^\"]+)\"")
							.matcher(atomXml);
					if (nextLink.find()) {
						currentUrl = fixCmisProtocol(nextLink.group(1).replace("&amp;", "&"), cmis.protocol());
					}
					else {
						currentUrl = null;
					}
				}
				finally {
					conn.disconnect();
				}
			}

			// Sort: folders first, then by name
			items.sort((a, b) -> {
				boolean aFolder = "cmis:folder".equals(a.get("baseType"));
				boolean bFolder = "cmis:folder".equals(b.get("baseType"));
				if (aFolder != bFolder) return aFolder ? -1 : 1;
				String aName = String.valueOf(a.getOrDefault("name", ""));
				String bName = String.valueOf(b.getOrDefault("name", ""));
				return aName.compareToIgnoreCase(bName);
			});

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("folderId", folderId);
			result.put("folderName", folderName);
			result.put("items", items);
			result.put("totalItems", items.size());
			return result;
		}
		catch (BizException e) {
			throw e;
		}
		catch (Exception e) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("cmis_browse",
					"Failed to browse CMIS folder: " + e.getMessage()));
		}
	}

	/**
	 * Parse a single CMIS AtomPub entry into a map of properties.
	 */
	private Map<String, Object> parseCmisEntry(String entryXml, String protocol) {
		Map<String, Object> item = new LinkedHashMap<>();

		// Extract CMIS properties using regex
		extractCmisProperty(entryXml, "cmis:objectId", item, "objectId");
		extractCmisProperty(entryXml, "cmis:name", item, "name");
		extractCmisProperty(entryXml, "cmis:baseTypeId", item, "baseType");
		extractCmisProperty(entryXml, "cmis:objectTypeId", item, "objectType");
		extractCmisProperty(entryXml, "cmis:contentStreamMimeType", item, "mimeType");
		extractCmisProperty(entryXml, "cmis:contentStreamLength", item, "size");
		extractCmisProperty(entryXml, "cmis:contentStreamFileName", item, "fileName");
		extractCmisProperty(entryXml, "cmis:createdBy", item, "createdBy");
		extractCmisProperty(entryXml, "cmis:lastModifiedBy", item, "lastModifiedBy");
		extractCmisProperty(entryXml, "cmis:creationDate", item, "creationDate");
		extractCmisProperty(entryXml, "cmis:lastModificationDate", item, "lastModificationDate");
		extractCmisProperty(entryXml, "cmis:parentId", item, "parentId");

		// Extract <atom:title> as fallback name
		if (!item.containsKey("name") || item.get("name") == null) {
			java.util.regex.Matcher titleMatcher = java.util.regex.Pattern
					.compile("<(?:atom:)?title[^>]*>([^<]+)</(?:atom:)?title>")
					.matcher(entryXml);
			if (titleMatcher.find()) {
				item.put("name", titleMatcher.group(1).trim());
			}
		}

		// Extract content stream URL
		java.util.regex.Matcher contentMatcher = java.util.regex.Pattern
				.compile("<(?:atom:)?content[^>]+src=\"([^\"]+)\"")
				.matcher(entryXml);
		if (contentMatcher.find()) {
			item.put("contentUrl", fixCmisProtocol(contentMatcher.group(1).replace("&amp;", "&"), protocol));
		}

		// Convert size to long if present
		if (item.containsKey("size") && item.get("size") instanceof String sizeStr) {
			try {
				item.put("size", Long.parseLong(sizeStr));
			}
			catch (NumberFormatException ignored) {
			}
		}

		// Mark as folder or document
		String baseType = (String) item.getOrDefault("baseType", "");
		item.put("isFolder", "cmis:folder".equals(baseType));

		return item.containsKey("objectId") ? item : null;
	}

	/**
	 * Extract a CMIS property value from an Atom entry XML.
	 * Handles both propertyString, propertyId, propertyInteger, propertyDateTime, etc.
	 */
	private void extractCmisProperty(String xml, String propertyDefId, Map<String, Object> target, String key) {
		// Pattern matches: <cmis:propertyXxx propertyDefinitionId="cmis:name" ...><cmis:value>...</cmis:value>
		java.util.regex.Matcher matcher = java.util.regex.Pattern
				.compile("<cmis:property[^>]+propertyDefinitionId=\"" +
						java.util.regex.Pattern.quote(propertyDefId) +
						"\"[^>]*>\\s*<cmis:value>([^<]*)</cmis:value>",
						java.util.regex.Pattern.DOTALL)
				.matcher(xml);
		if (matcher.find()) {
			String value = matcher.group(1).trim();
			if (StringUtils.isNotBlank(value)) {
				target.put(key, value);
			}
		}
	}

	/**
	 * Resolve the root folder ID from the CMIS service document.
	 */
	private String resolveRootFolderId(CmisConnectionInfo cmis) {
		HttpURLConnection conn = null;
		try {
			conn = openCmisConnection(cmis.serviceDocUrl(), "GET", cmis);
			int status = conn.getResponseCode();
			if (status < 200 || status >= 300) {
				log.warn("Failed to fetch CMIS service document for root folder: HTTP {}", status);
				return null;
			}

			String xml;
			try (InputStream in = conn.getInputStream()) {
				xml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
			}

			// Look for <cmisra:rootFolderId> or <cmis:rootFolderId>
			java.util.regex.Matcher matcher = java.util.regex.Pattern
					.compile("<(?:cmisra:|cmis:)?rootFolderId>([^<]+)</")
					.matcher(xml);
			if (matcher.find()) {
				String rootId = matcher.group(1).trim();
				log.info("Resolved CMIS root folder ID: {}", rootId);
				return rootId;
			}

			log.warn("Could not find rootFolderId in CMIS service document");
			return null;
		}
		catch (Exception e) {
			log.error("Failed to resolve root folder ID: {}", e.getMessage(), e);
			return null;
		}
		finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
	}

	@Override
	public Map<String, Object> uploadCmisDocument(String syncId, String folderId, String fileName,
			String contentType, byte[] content) {
		CmisConnectionInfo cmis = resolveCmisConnection(syncId);

		if (StringUtils.isBlank(folderId)) {
			folderId = resolveRootFolderId(cmis);
			if (folderId == null) {
				throw new BizException(ErrorCode.INVALID_PARAMS.toError("folder", "Could not resolve root folder"));
			}
		}
		if (StringUtils.isBlank(fileName)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("fileName", "File name is required"));
		}
		if (content == null || content.length == 0) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("content", "File content is empty"));
		}

		try {
			String encodedFolderId = URLEncoder.encode(folderId, StandardCharsets.UTF_8);
			String childrenUrl = cmis.realBaseUrl() + "/children?id=" + encodedFolderId;
			log.info("CMIS upload to folder: {} (fileName: {}, size: {} bytes)", childrenUrl, fileName, content.length);

			// Build multipart Atom entry for CMIS document creation
			String boundary = "----CmisUploadBoundary" + System.currentTimeMillis();
			String atomEntry = buildCmisCreateDocumentAtom(fileName, contentType, content);

			// For AtomPub, we POST an Atom entry with embedded base64 content
			HttpURLConnection conn = openCmisConnection(childrenUrl, "POST", cmis);
			try {
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "application/atom+xml;type=entry");

				try (java.io.OutputStream out = conn.getOutputStream()) {
					out.write(atomEntry.getBytes(StandardCharsets.UTF_8));
					out.flush();
				}

				int status = conn.getResponseCode();
				if (status < 200 || status >= 300) {
					String errBody = "";
					try (InputStream errStream = conn.getErrorStream()) {
						if (errStream != null) errBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
					}
					catch (Exception ignored) {
					}
					throw new BizException(ErrorCode.INVALID_PARAMS.toError("upload",
							"CMIS upload failed with HTTP " + status + ": " + errBody));
				}

				String responseXml;
				try (InputStream in = conn.getInputStream()) {
					responseXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}

				// Parse the response to get the created object ID
				Map<String, Object> created = parseCmisEntry(responseXml, cmis.protocol());
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("status", "success");
				result.put("objectId", created != null ? created.get("objectId") : null);
				result.put("name", fileName);
				result.put("message", "Document uploaded successfully");
				return result;
			}
			finally {
				conn.disconnect();
			}
		}
		catch (BizException e) {
			throw e;
		}
		catch (Exception e) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("upload",
					"Failed to upload document: " + e.getMessage()));
		}
	}

	/**
	 * Build a CMIS AtomPub entry XML for creating a new document with inline base64 content.
	 */
	private String buildCmisCreateDocumentAtom(String name, String mimeType, byte[] content) {
		String base64Content = Base64.getEncoder().encodeToString(content);
		return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
				"<atom:entry xmlns:atom=\"http://www.w3.org/2005/Atom\" " +
				"xmlns:cmis=\"http://docs.oasis-open.org/ns/cmis/core/200908/\" " +
				"xmlns:cmisra=\"http://docs.oasis-open.org/ns/cmis/restatom/200908/\">\n" +
				"  <atom:title>" + escapeXml(name) + "</atom:title>\n" +
				"  <cmisra:object>\n" +
				"    <cmis:properties>\n" +
				"      <cmis:propertyId propertyDefinitionId=\"cmis:objectTypeId\">" +
				"<cmis:value>cmis:document</cmis:value></cmis:propertyId>\n" +
				"      <cmis:propertyString propertyDefinitionId=\"cmis:name\">" +
				"<cmis:value>" + escapeXml(name) + "</cmis:value></cmis:propertyString>\n" +
				"    </cmis:properties>\n" +
				"  </cmisra:object>\n" +
				"  <cmisra:content>\n" +
				"    <cmisra:mediatype>" + escapeXml(mimeType) + "</cmisra:mediatype>\n" +
				"    <cmisra:base64>" + base64Content + "</cmisra:base64>\n" +
				"  </cmisra:content>\n" +
				"</atom:entry>";
	}

	@Override
	public Map<String, Object> deleteCmisObject(String syncId, String objectId, boolean allVersions) {
		CmisConnectionInfo cmis = resolveCmisConnection(syncId);

		if (StringUtils.isBlank(objectId)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("objectId", "Object ID is required"));
		}

		try {
			String encodedId = URLEncoder.encode(objectId, StandardCharsets.UTF_8);
			String deleteUrl = cmis.realBaseUrl() + "/id?id=" + encodedId + "&allVersions=" + allVersions;
			log.info("CMIS delete object: {} (objectId: {})", deleteUrl, objectId);

			HttpURLConnection conn = openCmisConnection(deleteUrl, "DELETE", cmis);
			try {
				int status = conn.getResponseCode();

				if (status < 200 || status >= 300) {
					String errBody = "";
					try (InputStream errStream = conn.getErrorStream()) {
						if (errStream != null) errBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
					}
					catch (Exception ignored) {
					}
					throw new BizException(ErrorCode.INVALID_PARAMS.toError("delete",
							"CMIS delete failed with HTTP " + status + ": " + errBody));
				}

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("status", "success");
				result.put("objectId", objectId);
				result.put("message", "Object deleted successfully");
				return result;
			}
			finally {
				conn.disconnect();
			}
		}
		catch (BizException e) {
			throw e;
		}
		catch (Exception e) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("delete",
					"Failed to delete CMIS object: " + e.getMessage()));
		}
	}

	@Override
	public Map<String, Object> renameCmisObject(String syncId, String objectId, String newName) {
		CmisConnectionInfo cmis = resolveCmisConnection(syncId);

		if (StringUtils.isBlank(objectId)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("objectId", "Object ID is required"));
		}
		if (StringUtils.isBlank(newName)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("newName", "New name is required"));
		}

		try {
			String encodedId = URLEncoder.encode(objectId, StandardCharsets.UTF_8);
			String entryUrl = cmis.realBaseUrl() + "/id?id=" + encodedId;
			log.info("CMIS rename object: {} → {} (objectId: {})", entryUrl, newName, objectId);

			// Build Atom entry with updated cmis:name property
			String atomEntry = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
					"<atom:entry xmlns:atom=\"http://www.w3.org/2005/Atom\" " +
					"xmlns:cmis=\"http://docs.oasis-open.org/ns/cmis/core/200908/\" " +
					"xmlns:cmisra=\"http://docs.oasis-open.org/ns/cmis/restatom/200908/\">\n" +
					"  <cmisra:object>\n" +
					"    <cmis:properties>\n" +
					"      <cmis:propertyString propertyDefinitionId=\"cmis:name\">" +
					"<cmis:value>" + escapeXml(newName) + "</cmis:value></cmis:propertyString>\n" +
					"    </cmis:properties>\n" +
					"  </cmisra:object>\n" +
					"</atom:entry>";

			HttpURLConnection conn = openCmisConnection(entryUrl, "PUT", cmis);
			try {
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "application/atom+xml;type=entry");

				try (java.io.OutputStream out = conn.getOutputStream()) {
					out.write(atomEntry.getBytes(StandardCharsets.UTF_8));
					out.flush();
				}

				int status = conn.getResponseCode();
				if (status < 200 || status >= 300) {
					String errBody = "";
					try (InputStream errStream = conn.getErrorStream()) {
						if (errStream != null) errBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
					}
					catch (Exception ignored) {
					}
					throw new BizException(ErrorCode.INVALID_PARAMS.toError("rename",
							"CMIS rename failed with HTTP " + status + ": " + errBody));
				}

				Map<String, Object> result = new LinkedHashMap<>();
				result.put("status", "success");
				result.put("objectId", objectId);
				result.put("name", newName);
				result.put("message", "Object renamed successfully");
				return result;
			}
			finally {
				conn.disconnect();
			}
		}
		catch (BizException e) {
			throw e;
		}
		catch (Exception e) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("rename",
					"Failed to rename CMIS object: " + e.getMessage()));
		}
	}

	@Override
	public Map<String, Object> createCmisFolder(String syncId, String parentFolderId, String folderName) {
		CmisConnectionInfo cmis = resolveCmisConnection(syncId);

		if (StringUtils.isBlank(parentFolderId)) {
			parentFolderId = resolveRootFolderId(cmis);
			if (parentFolderId == null) {
				throw new BizException(ErrorCode.INVALID_PARAMS.toError("folder", "Could not resolve root folder"));
			}
		}
		if (StringUtils.isBlank(folderName)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("folderName", "Folder name is required"));
		}

		try {
			String encodedParentId = URLEncoder.encode(parentFolderId, StandardCharsets.UTF_8);
			String childrenUrl = cmis.realBaseUrl() + "/children?id=" + encodedParentId;
			log.info("CMIS create folder: {} in parent {} (name: {})", childrenUrl, parentFolderId, folderName);

			// Build Atom entry for folder creation
			String atomEntry = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
					"<atom:entry xmlns:atom=\"http://www.w3.org/2005/Atom\" " +
					"xmlns:cmis=\"http://docs.oasis-open.org/ns/cmis/core/200908/\" " +
					"xmlns:cmisra=\"http://docs.oasis-open.org/ns/cmis/restatom/200908/\">\n" +
					"  <atom:title>" + escapeXml(folderName) + "</atom:title>\n" +
					"  <cmisra:object>\n" +
					"    <cmis:properties>\n" +
					"      <cmis:propertyId propertyDefinitionId=\"cmis:objectTypeId\">" +
					"<cmis:value>cmis:folder</cmis:value></cmis:propertyId>\n" +
					"      <cmis:propertyString propertyDefinitionId=\"cmis:name\">" +
					"<cmis:value>" + escapeXml(folderName) + "</cmis:value></cmis:propertyString>\n" +
					"    </cmis:properties>\n" +
					"  </cmisra:object>\n" +
					"</atom:entry>";

			HttpURLConnection conn = openCmisConnection(childrenUrl, "POST", cmis);
			try {
				conn.setDoOutput(true);
				conn.setRequestProperty("Content-Type", "application/atom+xml;type=entry");

				try (java.io.OutputStream out = conn.getOutputStream()) {
					out.write(atomEntry.getBytes(StandardCharsets.UTF_8));
					out.flush();
				}

				int status = conn.getResponseCode();
				if (status < 200 || status >= 300) {
					String errBody = "";
					try (InputStream errStream = conn.getErrorStream()) {
						if (errStream != null) errBody = new String(errStream.readAllBytes(), StandardCharsets.UTF_8);
					}
					catch (Exception ignored) {
					}
					throw new BizException(ErrorCode.INVALID_PARAMS.toError("createFolder",
							"CMIS folder creation failed with HTTP " + status + ": " + errBody));
				}

				String responseXml;
				try (InputStream in = conn.getInputStream()) {
					responseXml = new String(in.readAllBytes(), StandardCharsets.UTF_8);
				}

				Map<String, Object> created = parseCmisEntry(responseXml, cmis.protocol());
				Map<String, Object> result = new LinkedHashMap<>();
				result.put("status", "success");
				result.put("objectId", created != null ? created.get("objectId") : null);
				result.put("name", folderName);
				result.put("message", "Folder created successfully");
				return result;
			}
			finally {
				conn.disconnect();
			}
		}
		catch (BizException e) {
			throw e;
		}
		catch (Exception e) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("createFolder",
					"Failed to create CMIS folder: " + e.getMessage()));
		}
	}

	/**
	 * Parse all entries from an Atom feed XML into a list of maps.
	 */
	private List<Map<String, Object>> parseCmisFeed(String atomXml, String protocol) {
		List<Map<String, Object>> items = new ArrayList<>();
		java.util.regex.Pattern entryPattern = java.util.regex.Pattern
				.compile("<(?:atom:)?entry>(.*?)</(?:atom:)?entry>", java.util.regex.Pattern.DOTALL);
		java.util.regex.Matcher entryMatcher = entryPattern.matcher(atomXml);
		while (entryMatcher.find()) {
			Map<String, Object> item = parseCmisEntry(entryMatcher.group(1), protocol);
			if (item != null) {
				items.add(item);
			}
		}
		return items;
	}

	/**
	 * Escape special characters for XML content.
	 */
	private String escapeXml(String value) {
		if (value == null) return "";
		return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&apos;");
	}

}
