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
import com.alibaba.cloud.ai.studio.core.base.entity.KnowledgeSyncEntity;
import com.alibaba.cloud.ai.studio.core.base.entity.SourceSystemEntity;
import com.alibaba.cloud.ai.studio.core.base.mapper.DestinationMapper;
import com.alibaba.cloud.ai.studio.core.base.mapper.KnowledgeSyncMapper;
import com.alibaba.cloud.ai.studio.core.base.mapper.SourceSystemMapper;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.rag.KnowledgeSyncService;
import com.alibaba.cloud.ai.studio.core.rag.index.KnowledgeIndexSchema;
import com.alibaba.cloud.ai.studio.core.rag.index.KnowledgeIndexSchemaFactory;
import com.alibaba.cloud.ai.studio.core.source.ManifoldCFBridgeService;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase.KnowledgeSync;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
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
import org.springframework.scheduling.annotation.Async;
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

	private final ObjectMapper objectMapper;

	private final RestTemplate restTemplate;

	/**
	 * Tracks active sync tokens per syncId. When a new sync starts (or a hard
	 * reset / stop occurs), the old token is replaced or removed.  Async threads
	 * check their token before each DB write — if the token has changed, the
	 * thread knows it has been superseded and should stop immediately.
	 */
	private final ConcurrentHashMap<String, String> activeSyncTokens = new ConcurrentHashMap<>();

	public KnowledgeSyncServiceImpl(SourceSystemMapper sourceSystemMapper, DestinationMapper destinationMapper,
			ManifoldCFBridgeService mcfBridge, KnowledgeIndexSchemaFactory indexSchemaFactory) {
		this.sourceSystemMapper = sourceSystemMapper;
		this.destinationMapper = destinationMapper;
		this.mcfBridge = mcfBridge;
		this.indexSchemaFactory = indexSchemaFactory;
		this.objectMapper = new ObjectMapper();
		this.restTemplate = new RestTemplate();
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
		entity.setStatus("pending");
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
			entity.setStatus("failed");
			entity.setErrorMessage(errorMsg);
			entity.setGmtModified(new Date());
			this.updateById(entity);
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("connectivity", errorMsg));
		}

		// Update status to indexing and clear stale error messages
		entity.setStatus("indexing");
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
		CompletableFuture.runAsync(() -> startAsyncSync(entity, destUrl, destUsername, destPassword, syncToken));

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
		long timeoutSeconds = 60;
		List<String> failures = Collections.synchronizedList(new ArrayList<>());
		ExecutorService executor = Executors.newFixedThreadPool(3);

		try {
			// Check 1: Internal system (ManifoldCF)
			Future<?> mcfFuture = executor.submit(() -> {
				try {
					checkManifoldCFConnectivity();
				}
				catch (Exception e) {
					failures.add("Internal system (ManifoldCF): " + summarizeError(e));
				}
			});

			// Check 2: Target (OpenSearch)
			Future<?> targetFuture = executor.submit(() -> {
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
				sourceFuture = executor.submit(() -> {
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
			executor.shutdownNow();
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
		RestTemplate timeoutRt = createTimeoutRestTemplate();
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
					entity.setStatus("indexing");
					entity.setIndexProgress(5);
					entity.setGmtModified(new Date());
					this.updateById(entity);

					// Clean up old MCF job if exists
					if (StringUtils.isNotBlank(entity.getMcfJobId())) {
						try {
							mcfBridge.abortJob(entity.getMcfJobId());
							mcfBridge.deleteJob(entity.getMcfJobId());
						}
						catch (Exception e) {
							log.debug("Could not clean up old MCF job: {}", e.getMessage());
						}
					}

					// Step 2a: Create a per-KB MCF output connection so MCF writes
					// directly to our enforced index name (e.g., "2023070559378198529_document")
					// instead of the shared "OpenSearch" connection's default index.
					String perKbOutputConnName = "KB_" + entity.getKbId();
					String perKbOutputDesc = "KB " + entity.getKbId() + " document index: "
							+ entity.getIndexName();
					mcfBridge.createOutputConnection(perKbOutputConnName, perKbOutputDesc,
							entity.getIndexName(), entity.getAuthorityIndexName());
					log.info("Created per-KB MCF output connection '{}' -> index '{}'",
							perKbOutputConnName, entity.getIndexName());

					// Step 2b: Ensure Tika transformation connection exists
					// MCF will extract text from binary docs (PDF, DOCX, PPTX, etc.)
					// during crawl so the document index has clean text for full-text search.
					mcfBridge.ensureTikaTransformationConnection();

					// Step 2c: Create and start MCF crawl job with Tika pipeline:
					// CMIS Repository → [Tika text extraction] → [OpenSearch output]
					String jobDescription = "KB Sync: " + entity.getKbId() + " / " + entity.getSyncId();
					// Read CMIS query from source connection config; default to all documents
					Map<String, Object> sourceConfig = deserializeConfig(source.getConnectionConfig());
					String cmisQuery = getConfigString(sourceConfig, "cmisQuery",
							"SELECT * FROM cmis:document");
					log.info("Using CMIS query for sync {}: {}", entity.getSyncId(), cmisQuery);
					String jobId = mcfBridge.createCrawlJob(jobDescription,
							source.getMcfConnectionName(), perKbOutputConnName, cmisQuery, "cmisQuery",
							mcfBridge.getTikaConnectionName());
					mcfBridge.startJob(jobId);

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
			entity.setStatus("authority_syncing");
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

			// ---- Step 4: RAG chunking ----
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Async sync for {} superseded before RAG phase, aborting", entity.getSyncId());
				return;
			}
			indexSchemaFactory.createRagIndex(destUrl, destUsername, destPassword, entity.getRagIndexName(), 1024);
			entity.setStatus("rag_processing");
			entity.setRagProgress(5);
			entity.setGmtModified(new Date());
			this.updateById(entity);

			long ragCount = populateRagIndex(entity, destUrl, destUsername, destPassword, syncToken);
			entity.setRagDocs(ragCount);
			entity.setRagProgress(100);
			entity.setGmtModified(new Date());
			this.updateById(entity);
			log.info("RAG chunking complete: {} chunks in index '{}'", ragCount, entity.getRagIndexName());

			// ---- Done ----
			if (!isSyncActive(entity.getSyncId(), syncToken)) {
				log.info("Async sync for {} superseded before completion, aborting", entity.getSyncId());
				return;
			}
			entity.setStatus("completed");
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
			entity.setStatus("failed");
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
		int maxPolls = 600; // 30 minutes max (600 * 3s)
		int pollCount = 0;
		int stuckCount = 0; // consecutive polls with 0 docs in a "starting up" state
		int maxStuckPolls = 40; // 40 * 3s = 2 minutes

		while (pollCount < maxPolls) {
			try {
				Thread.sleep(3000);
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
				entity.setStatus("failed");
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
			HttpHeaders headers = buildAuthHeaders(username, password);
			HttpEntity<String> request = new HttpEntity<>(null, headers);
			ResponseEntity<String> response = restTemplate.exchange(
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

			// Build JSON with members array
			StringBuilder doc = new StringBuilder();
			doc.append("{\"principal_id\":\"").append(escapeJsonString(groupToken))
					.append("\",\"principal_type\":\"group\"")
					.append(",\"display_name\":\"").append(escapeJsonString(groupToken))
					.append("\",\"member_count\":").append(members.size());

			if (!members.isEmpty()) {
				doc.append(",\"members\":[");
				boolean first = true;
				for (String m : members) {
					if (!first) doc.append(",");
					doc.append("\"").append(escapeJsonString(m)).append("\"");
					first = false;
				}
				doc.append("]");
			}

			doc.append(",\"synced_at\":\"").append(now).append("\"}");
			bulk.append(doc).append("\n");
		}

		// Index user entries with member_of
		for (String userToken : userTokens) {
			String id = userToken.toLowerCase().replace(" ", "_");
			Set<String> memberOf = userMemberOfMap.getOrDefault(userToken, Collections.emptySet());

			bulk.append("{\"index\":{\"_index\":\"").append(authIndexName)
					.append("\",\"_id\":\"").append(escapeJsonString(id)).append("\"}}\n");

			StringBuilder doc = new StringBuilder();
			doc.append("{\"principal_id\":\"").append(escapeJsonString(userToken))
					.append("\",\"principal_type\":\"user\"")
					.append(",\"display_name\":\"").append(escapeJsonString(userToken)).append("\"");

			if (!memberOf.isEmpty()) {
				doc.append(",\"member_of\":[");
				boolean first = true;
				for (String g : memberOf) {
					if (!first) doc.append(",");
					doc.append("\"").append(escapeJsonString(g)).append("\"");
					first = false;
				}
				doc.append("]");
			}

			doc.append(",\"synced_at\":\"").append(now).append("\"}");
			bulk.append(doc).append("\n");
		}

		// Execute bulk request
		if (bulk.length() > 0) {
			String endpoint = destUrl.endsWith("/") ? destUrl + "_bulk" : destUrl + "/_bulk";
			HttpHeaders headers = buildAuthHeaders(username, password);
			headers.setContentType(MediaType.valueOf("application/x-ndjson"));
			HttpEntity<String> request = new HttpEntity<>(bulk.toString(), headers);
			restTemplate.exchange(endpoint, HttpMethod.POST, request, String.class);
		}

		long totalPrincipals = groupTokens.size() + userTokens.size();
		log.info("Populated authority index '{}' with {} principals ({} groups, {} users)",
				authIndexName, totalPrincipals, groupTokens.size(), userTokens.size());

		// ---- Step 3b: Update each document with resolved authorities ----
		if (canResolveGroups) {
			updateDocumentAuthorities(destUrl, username, password, docIndexName, groupMembersMap);
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
		HttpURLConnection conn = null;
		try {
			// Build the members URL from template
			String membersUrl = baseUrl + groupMembersApiUrl.replace("{groupId}", groupToken);
			// Add memberType=PERSON filter and pagination
			membersUrl += (membersUrl.contains("?") ? "&" : "?")
					+ "where=(memberType%3D%27PERSON%27)&maxItems=1000";

			URI uri = URI.create(membersUrl);
			conn = (HttpURLConnection) uri.toURL().openConnection();

			// Trust all certs for HTTPS (source may have self-signed cert)
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

					// Parse Alfresco API response:
					// { "list": { "entries": [ { "entry": { "id": "username", "memberType": "PERSON" } } ] } }
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
						}
					}
				}
			}
			else if (httpStatus == 404) {
				log.debug("Group '{}' not found via Group Members API (HTTP 404)", groupToken);
			}
			else {
				log.warn("Group Members API returned HTTP {} for group '{}'", httpStatus, groupToken);
			}
		}
		catch (Exception e) {
			log.warn("Failed to resolve group '{}' members: {}", groupToken, e.getMessage());
		}
		finally {
			if (conn != null) {
				conn.disconnect();
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

		HttpURLConnection conn = null;
		try {
			String fullUrl = baseUrl + groupApiUrl;
			// Paginate to get all groups
			fullUrl += (fullUrl.contains("?") ? "&" : "?") + "maxItems=1000";

			URI uri = URI.create(fullUrl);
			conn = (HttpURLConnection) uri.toURL().openConnection();

			// Trust all certs for HTTPS
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
						}
					}
				}
			}
			else {
				log.warn("Groups list API returned HTTP {} — case-insensitive lookup unavailable", httpStatus);
			}
		}
		catch (Exception e) {
			log.warn("Failed to fetch groups for case mapping: {}", e.getMessage());
		}
		finally {
			if (conn != null) {
				conn.disconnect();
			}
		}
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
			String docIndexName, Map<String, Set<String>> groupMembersMap) {
		log.info("Updating document authorities in index '{}'...", docIndexName);
		int updatedCount = 0;

		try {
			// Scroll through all documents to read their allow_token_document
			String searchEndpoint = destUrl.endsWith("/")
					? destUrl + docIndexName + "/_search?scroll=2m"
					: destUrl + "/" + docIndexName + "/_search?scroll=2m";

			HttpHeaders headers = buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Fetch all docs with their allow_token_document field
			String searchBody = "{\"size\":200,\"_source\":[\"allow_token_document\"]}";
			HttpEntity<String> searchRequest = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = restTemplate.exchange(
					searchEndpoint, HttpMethod.POST, searchRequest, String.class);

			StringBuilder bulkUpdate = new StringBuilder();
			int batchSize = 0;

			while (response.getBody() != null) {
				Map<String, Object> result = objectMapper.readValue(response.getBody(),
						new TypeReference<Map<String, Object>>() {});

				String scrollId = (String) result.get("_scroll_id");
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

					// Flush bulk in batches of 200
					if (batchSize >= 200) {
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
				response = restTemplate.exchange(scrollEndpoint, HttpMethod.POST, scrollRequest, String.class);
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
	}

	/**
	 * Execute a bulk request against OpenSearch.
	 */
	private void executeBulk(String destUrl, String username, String password, String bulkBody) {
		String endpoint = destUrl.endsWith("/") ? destUrl + "_bulk" : destUrl + "/_bulk";
		HttpHeaders headers = buildAuthHeaders(username, password);
		headers.setContentType(MediaType.valueOf("application/x-ndjson"));
		HttpEntity<String> request = new HttpEntity<>(bulkBody, headers);
		restTemplate.exchange(endpoint, HttpMethod.POST, request, String.class);
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
			HttpHeaders headers = buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Terms aggregation with large size to get all unique values
			String body = "{\"size\":0,\"aggs\":{\"tokens\":{\"terms\":{\"field\":\""
					+ fieldName + "\",\"size\":10000}}}}";

			HttpEntity<String> request = new HttpEntity<>(body, headers);
			ResponseEntity<String> response = restTemplate.exchange(
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
	 * index, chunking it, and indexing to the RAG index with metadata and ACL tokens.
	 *
	 * <p>Key design: The MCF Tika transformation connector extracts text during crawl,
	 * so the document index already contains clean text in the {@code content} field.
	 * The RAG index stores chunked text for similarity search — following RAG best
	 * practices. This avoids re-downloading files from CMIS.</p>
	 */
	@SuppressWarnings("unchecked")
	private long populateRagIndex(KnowledgeSyncEntity entity, String destUrl, String username,
			String password, String syncToken) {
		String docIndexName = entity.getIndexName();
		String ragIndexName = entity.getRagIndexName();
		long chunksIndexed = 0;
		int batchSize = 50;
		int chunkSize = 1000;
		int chunkOverlap = 200;

		log.info("RAG population starting for sync {} — reading from document index, chunkSize={}, overlap={}",
				entity.getSyncId(), chunkSize, chunkOverlap);

		try {
			// Initial scroll search
			String searchEndpoint = destUrl.endsWith("/")
					? destUrl + docIndexName + "/_search?scroll=5m"
					: destUrl + "/" + docIndexName + "/_search?scroll=5m";
			HttpHeaders headers = buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);

			String searchBody = "{\"size\":" + batchSize + ",\"query\":{\"match_all\":{}}}";
			HttpEntity<String> request = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = restTemplate.exchange(
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

				StringBuilder bulk = new StringBuilder();
				int chunksInBatch = 0;
				final int MAX_CHUNKS_PER_BULK = 200;
				final int MAX_BULK_BYTES = 5 * 1024 * 1024; // 5 MB

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

						// Build chunk document as Map and serialize with ObjectMapper
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
						chunkDoc.put("metadata", metadata);

// Resolved authorities — only usernames, no raw ACL tokens
						if (authorities != null) {
							chunkDoc.put("authorities", authorities);
						}

						// Action line
						bulk.append("{\"index\":{\"_index\":\"").append(ragIndexName)
								.append("\",\"_id\":\"").append(escapeJsonString(chunkId)).append("\"}}\n");
						// Document line (use ObjectMapper for reliable JSON serialization)
						try {
							bulk.append(objectMapper.writeValueAsString(chunkDoc)).append("\n");
						}
						catch (JsonProcessingException e) {
							log.debug("Failed to serialize chunk {}: {}", chunkId, e.getMessage());
							continue;
						}
						chunksInBatch++;

						// Flush when batch is large enough to avoid 413 Request Entity Too Large
						if (chunksInBatch >= MAX_CHUNKS_PER_BULK || bulk.length() >= MAX_BULK_BYTES) {
							int accepted = sendBulkAndCountSuccess(bulkEndpoint, bulk.toString(), username, password);
							log.debug("RAG bulk flush: {}/{} chunks accepted (buffer {}KB), total {}",
									accepted, chunksInBatch, bulk.length() / 1024, chunksIndexed + accepted);
							chunksIndexed += accepted;
							bulk.setLength(0);
							chunksInBatch = 0;
						}
					}
					processedDocs++;
				}

				// Flush remaining chunks from this scroll batch
				if (chunksInBatch > 0) {
					int accepted = sendBulkAndCountSuccess(bulkEndpoint, bulk.toString(), username, password);
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
				response = restTemplate.exchange(scrollEndpoint, HttpMethod.POST, scrollRequest, String.class);

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
	 * Split text into chunks of approximately maxChunkSize characters,
	 * respecting paragraph and sentence boundaries.
	 */
	private List<String> chunkText(String text, int maxChunkSize) {
		List<String> chunks = new ArrayList<>();
		if (text == null || text.isEmpty()) {
			return chunks;
		}

		// Split by double newlines (paragraphs) first
		String[] paragraphs = text.split("\\n\\n+");
		StringBuilder currentChunk = new StringBuilder();

		for (String para : paragraphs) {
			para = para.trim();
			if (para.isEmpty()) {
				continue;
			}

			if (currentChunk.length() + para.length() + 2 <= maxChunkSize) {
				if (currentChunk.length() > 0) {
					currentChunk.append("\n\n");
				}
				currentChunk.append(para);
			}
			else {
				// Current chunk is full
				if (currentChunk.length() > 0) {
					chunks.add(currentChunk.toString());
					currentChunk = new StringBuilder();
				}

				// If paragraph itself exceeds max, split by sentences
				if (para.length() > maxChunkSize) {
					String[] sentences = para.split("(?<=[.!?])\\s+");
					for (String sentence : sentences) {
						if (currentChunk.length() + sentence.length() + 1 <= maxChunkSize) {
							if (currentChunk.length() > 0) {
								currentChunk.append(" ");
							}
							currentChunk.append(sentence);
						}
						else {
							if (currentChunk.length() > 0) {
								chunks.add(currentChunk.toString());
								currentChunk = new StringBuilder();
							}
							// If single sentence > max, force-split
							if (sentence.length() > maxChunkSize) {
								for (int i = 0; i < sentence.length(); i += maxChunkSize) {
									chunks.add(sentence.substring(i,
											Math.min(i + maxChunkSize, sentence.length())));
								}
							}
							else {
								currentChunk.append(sentence);
							}
						}
					}
				}
				else {
					currentChunk.append(para);
				}
			}
		}

		if (currentChunk.length() > 0) {
			chunks.add(currentChunk.toString());
		}

		// Ensure at least one chunk
		if (chunks.isEmpty() && !text.isEmpty()) {
			chunks.add(text.substring(0, Math.min(maxChunkSize, text.length())));
		}

		return chunks;
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
			HttpHeaders headers = buildAuthHeaders(username, password);
			headers.setContentType(MediaType.APPLICATION_JSON);
			String body = "{\"scroll_id\":\"" + scrollId + "\"}";
			HttpEntity<String> request = new HttpEntity<>(body, headers);
			restTemplate.exchange(endpoint, HttpMethod.DELETE, request, String.class);
		}
		catch (Exception e) {
			log.debug("Could not clear scroll context: {}", e.getMessage());
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
		HttpHeaders headers = buildAuthHeaders(username, password);
		// Use UTF-8 charset explicitly — default ISO-8859-1 breaks for non-ASCII chars
		// like \u00a0 (NBSP) from Tika-extracted text, causing json_parse_exception
		headers.setContentType(new MediaType("application", "x-ndjson", StandardCharsets.UTF_8));

		HttpEntity<String> request = new HttpEntity<>(bulkBody, headers);
		ResponseEntity<String> response = restTemplate.exchange(
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

	private HttpHeaders buildAuthHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		if (StringUtils.isNotBlank(username)) {
			String auth = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
			headers.set("Authorization", "Basic " + auth);
		}
		return headers;
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

		entity.setStatus("indexing");
		entity.setIndexProgress(0);
		entity.setGmtModified(new Date());
		this.updateById(entity);

		// Generate a sync token for the doc-only path
		String syncToken = UUID.randomUUID().toString();
		activeSyncTokens.put(entity.getSyncId(), syncToken);

		startAsyncDocSyncOnly(entity, destUrl, destUsername, destPassword, syncToken);

		Map<String, String> result = new LinkedHashMap<>();
		result.put("status", "started");
		result.put("sync_id", syncId);
		result.put("phase", "sync_documents");
		return result;
	}

	@Async
	protected void startAsyncDocSyncOnly(KnowledgeSyncEntity entity, String destUrl, String destUsername,
			String destPassword, String syncToken) {
		try {
			indexSchemaFactory.createDocumentIndex(destUrl, destUsername, destPassword, entity.getIndexName());
			indexSchemaFactory.createAuthorityIndex(destUrl, destUsername, destPassword, entity.getAuthorityIndexName());

			if (StringUtils.isNotBlank(entity.getSourceId())) {
				SourceSystemEntity source = findSource(entity.getSourceId());
				if (source != null && StringUtils.isNotBlank(source.getMcfConnectionName())) {
					entity.setIndexProgress(5);
					entity.setGmtModified(new Date());
					this.updateById(entity);

					if (StringUtils.isNotBlank(entity.getMcfJobId())) {
						try {
							mcfBridge.abortJob(entity.getMcfJobId());
							mcfBridge.deleteJob(entity.getMcfJobId());
						}
						catch (Exception e) {
							log.debug("Could not clean up old MCF job: {}", e.getMessage());
						}
					}

					String jobDescription = "Doc Sync: " + entity.getKbId() + " / " + entity.getSyncId();
					String outputConn = StringUtils.isNotBlank(source.getMcfOutputName())
							? source.getMcfOutputName() : null;
					// Read CMIS query from source connection config; default to all documents
					Map<String, Object> sourceConfig = deserializeConfig(source.getConnectionConfig());
					String cmisQuery = getConfigString(sourceConfig, "cmisQuery",
							"SELECT * FROM cmis:document");
					log.info("Using CMIS query for doc sync {}: {}", entity.getSyncId(), cmisQuery);
					String jobId = mcfBridge.createCrawlJob(jobDescription,
							source.getMcfConnectionName(), outputConn, cmisQuery, "cmisQuery");
					mcfBridge.startJob(jobId);

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
			entity.setStatus("completed");
			entity.setLastSyncTime(new Date());
			entity.setGmtModified(new Date());
			this.updateById(entity);
			log.info("Document sync only completed: syncId={}, totalDocs={}", entity.getSyncId(), docCount);
		}
		catch (Exception e) {
			log.error("Document sync only failed: syncId={}", entity.getSyncId(), e);
			entity.setStatus("failed");
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

		DestinationEntity dest = findDestination(entity.getDestinationId());
		if (dest == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}

		Map<String, Object> destConfig = deserializeConfig(dest.getConnectionConfig());
		String destUrl = getConfigString(destConfig, "url", "");
		String destUsername = getConfigString(destConfig, "username", "");
		String destPassword = getConfigString(destConfig, "password", "");

		entity.setStatus("rag_processing");
		entity.setRagProgress(0);
		entity.setGmtModified(new Date());
		this.updateById(entity);

		startAsyncRagOnly(entity, destUrl, destUsername, destPassword);

		Map<String, String> result = new LinkedHashMap<>();
		result.put("status", "started");
		result.put("sync_id", syncId);
		result.put("phase", "reindex_rag");
		return result;
	}

	@Async
	protected void startAsyncRagOnly(KnowledgeSyncEntity entity, String destUrl, String destUsername,
			String destPassword) {
		try {
			// Generate a sync token for this operation
			String syncToken = UUID.randomUUID().toString();
			activeSyncTokens.put(entity.getSyncId(), syncToken);

			// Delete existing RAG index and recreate
			indexSchemaFactory.deleteIndex(destUrl, destUsername, destPassword, entity.getRagIndexName());
			indexSchemaFactory.createRagIndex(destUrl, destUsername, destPassword, entity.getRagIndexName(), 1024);

			entity.setRagProgress(5);
			entity.setGmtModified(new Date());
			this.updateById(entity);

			long ragCount = populateRagIndex(entity, destUrl, destUsername, destPassword, syncToken);

			long docCount = countOpenSearchDocs(destUrl, destUsername, destPassword, entity.getIndexName());
			entity.setTotalDocs(docCount);
			entity.setIndexedDocs(docCount);
			entity.setRagDocs(ragCount);
			entity.setRagProgress(100);
			entity.setStatus("completed");
			entity.setLastSyncTime(new Date());
			entity.setGmtModified(new Date());
			this.updateById(entity);
			log.info("RAG reindex completed: syncId={}, ragChunks={}", entity.getSyncId(), ragCount);
		}
		catch (Exception e) {
			log.error("RAG reindex only failed: syncId={}", entity.getSyncId(), e);
			entity.setStatus("failed");
			entity.setErrorMessage(e.getMessage());
			entity.setGmtModified(new Date());
			this.updateById(entity);
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
				HttpHeaders headers = buildAuthHeaders(username, password);
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
				ResponseEntity<String> response = restTemplate.exchange(
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
								record.put("kb_id", kbIdFromIndex(String.valueOf(hit.get("_index"))));
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
								record.put("size", fileSize instanceof Number
										? ((Number) fileSize).longValue() : 0L);
								record.put("index_status", "completed");
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

	private String kbIdFromIndex(String indexName) {
		// The index name may be "manifoldcf" or the KB-specific one
		return indexName != null ? indexName : "";
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
		entity.setStatus("pending");
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

		entity.setStatus("failed");
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
			HttpHeaders headers = buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);
			String searchBody = "{\"size\":1,\"query\":{\"ids\":{\"values\":["
					+ objectMapper.writeValueAsString(docId) + "]}}}";
			HttpEntity<String> request = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = restTemplate.exchange(
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
			HttpHeaders headers = buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Search RAG chunks by doc_id (keyword field storing the parent document's OpenSearch _id)
			String jsonDocId = objectMapper.writeValueAsString(docId);
			String searchBody = "{\"from\":" + from + ",\"size\":" + size
					+ ",\"query\":{\"term\":{\"doc_id\":" + jsonDocId + "}}"
					+ ",\"sort\":[{\"chunk_index\":{\"order\":\"asc\",\"missing\":\"_last\",\"unmapped_type\":\"integer\"}},"
					+ "{\"_id\":{\"order\":\"asc\"}}]"
					+ ",\"track_total_hits\":true}";

			HttpEntity<String> request = new HttpEntity<>(searchBody, headers);
			ResponseEntity<String> response = restTemplate.exchange(
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
			HttpHeaders headers = buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);

			Map<String, Object> updateBody = new LinkedHashMap<>();
			updateBody.put("query", Map.of("ids", Map.of("values", List.of(chunkId))));
			updateBody.put("script", Map.of(
					"source", "ctx._source['content'] = params.content",
					"params", Map.of("content", content)));
			String body = objectMapper.writeValueAsString(updateBody);
			HttpEntity<String> request = new HttpEntity<>(body, headers);
			restTemplate.exchange(endpoint, HttpMethod.POST, request, String.class);

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
			HttpHeaders headers = buildAuthHeaders(destUsername, destPassword);
			headers.setContentType(MediaType.APPLICATION_JSON);

			// Only allow updating safe metadata fields (not content or ACLs)
			Map<String, Object> safeFields = new LinkedHashMap<>();
			for (Map.Entry<String, Object> entry : metadata.entrySet()) {
				String key = entry.getKey();
				// Block modification of system fields and ACL tokens
				if (!key.startsWith("allow_token_") && !key.startsWith("deny_token_")
						&& !"_id".equals(key) && !"_index".equals(key)) {
					safeFields.put(key, entry.getValue());
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
			restTemplate.exchange(endpoint, HttpMethod.POST, request, String.class);

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

		// The doc_id IS the CMIS content stream URL
		if (StringUtils.isBlank(docId)) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("doc_id", "Document ID is required"));
		}

		// Get source credentials from source system config
		Map<String, Object> sourceConfig = deserializeConfig(source.getConnectionConfig());
		String sourceUsername = getConfigString(sourceConfig, "username", "");
		String sourcePassword = getConfigString(sourceConfig, "password", "");
		String sourceProtocol = getConfigString(sourceConfig, "protocol", "https");

		// The doc_id URL from CMIS may use http:// even if the server uses https (reverse proxy).
		// If source protocol is https, rewrite http:// to https:// in the download URL.
		String downloadUrl = docId;
		if ("https".equalsIgnoreCase(sourceProtocol) && downloadUrl.startsWith("http://")) {
			downloadUrl = "https://" + downloadUrl.substring(7);
			log.debug("Rewrote download URL to HTTPS: {}", downloadUrl);
		}

		// Extract filename from the URL path for Content-Disposition
		String fileName = extractFileNameFromUrl(docId);

		try {
			// Use raw HttpURLConnection to support SSL trust-all for self-signed certs
			URI uri = URI.create(downloadUrl);
			HttpURLConnection conn = (HttpURLConnection) uri.toURL().openConnection();

			// Trust all certs for HTTPS (source may have self-signed cert)
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
				log.warn("CMIS download failed with HTTP {}: {}", httpStatus, downloadUrl);
				throw new BizException(ErrorCode.INVALID_PARAMS.toError("download",
						"Source system returned HTTP " + httpStatus));
			}

			// Read content type
			String contentType = conn.getContentType();
			if (StringUtils.isBlank(contentType)) {
				contentType = "application/octet-stream";
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

}
