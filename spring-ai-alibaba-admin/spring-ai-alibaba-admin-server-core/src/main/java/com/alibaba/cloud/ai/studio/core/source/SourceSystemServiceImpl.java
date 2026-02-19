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

package com.alibaba.cloud.ai.studio.core.source;

import com.alibaba.cloud.ai.studio.core.base.entity.SourceSystemEntity;
import com.alibaba.cloud.ai.studio.core.base.mapper.SourceSystemMapper;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.runtime.domain.BaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.source.SourceSystem;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of SourceSystemService. Manages source system lifecycle and
 * bridges with ManifoldCF for crawling operations.
 */
@Slf4j
@Service
public class SourceSystemServiceImpl extends ServiceImpl<SourceSystemMapper, SourceSystemEntity>
		implements SourceSystemService {

	private final ManifoldCFBridgeService mcfBridge;

	private final ObjectMapper objectMapper;

	public SourceSystemServiceImpl(ManifoldCFBridgeService mcfBridge) {
		this.mcfBridge = mcfBridge;
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public String createSourceSystem(SourceSystem source) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String workspaceId = context.getWorkspaceId();
		String accountId = context.getAccountId();

		// Check name uniqueness within workspace
		LambdaQueryWrapper<SourceSystemEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(SourceSystemEntity::getWorkspaceId, workspaceId).eq(SourceSystemEntity::getName, source.getName());
		if (this.count(wrapper) > 0) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("name", "Source system name already exists"));
		}

		String sourceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

		// Try to create ManifoldCF repository connection (best-effort).
		// If ManifoldCF is unavailable, we still save the source as a draft.
		// The MCF connection will be created/re-pushed when the user tests
		// the connection or enables the source (testConnection re-pushes config).
		String mcfConnectionName = "src_" + sourceId;
		try {
			mcfBridge.createRepositoryConnection(mcfConnectionName, source.getDescription(),
					source.getConnectorClass(), source.getConnectionConfig());
		}
		catch (Exception e) {
			log.warn("Could not create MCF connection '{}' (ManifoldCF may be unavailable): {}. "
					+ "Source will be saved as draft; MCF connection will be created on test/sync.",
					mcfConnectionName, e.getMessage());
		}

		// Save to database
		SourceSystemEntity entity = new SourceSystemEntity();
		entity.setSourceId(sourceId);
		entity.setWorkspaceId(workspaceId);
		entity.setName(source.getName());
		entity.setDescription(source.getDescription());
		entity.setConnectorType(source.getConnectorType());
		entity.setConnectorClass(source.getConnectorClass());
		entity.setStatus(0); // Draft — must pass validation via enable endpoint
		entity.setConnectionConfig(serializeConfig(source.getConnectionConfig()));
		entity.setMcfConnectionName(mcfConnectionName);
		entity.setMcfOutputName("OpenSearch");
		entity.setDocsTotal(0L);
		entity.setDocsProcessed(0L);
		entity.setDocsFailed(0L);
		entity.setGmtCreate(new Date());
		entity.setGmtModified(new Date());
		entity.setCreator(accountId);
		entity.setModifier(accountId);

		this.save(entity);
		log.info("Created source system '{}' (sourceId={}, mcf={})", source.getName(), sourceId, mcfConnectionName);

		return sourceId;
	}

	@Override
	public void updateSourceSystem(SourceSystem source) {
		RequestContext context = RequestContextHolder.getRequestContext();

		SourceSystemEntity entity = findBySourceId(source.getSourceId());
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		if (StringUtils.isNotBlank(source.getName())) {
			entity.setName(source.getName());
		}
		if (source.getDescription() != null) {
			entity.setDescription(source.getDescription());
		}
		if (source.getConnectionConfig() != null) {
			entity.setConnectionConfig(serializeConfig(source.getConnectionConfig()));
			// Update MCF connection too
			try {
				mcfBridge.createRepositoryConnection(entity.getMcfConnectionName(), entity.getDescription(),
						entity.getConnectorClass(), source.getConnectionConfig());
			}
			catch (Exception e) {
				log.warn("Failed to update MCF connection: {}", e.getMessage());
			}
		}
		if (source.getSyncCron() != null) {
			entity.setSyncCron(source.getSyncCron());
		}

		entity.setGmtModified(new Date());
		entity.setModifier(context.getAccountId());
		this.updateById(entity);
	}

	@Override
	public void deleteSourceSystem(String sourceId) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			return;
		}

		// Abort and delete MCF job if exists
		if (StringUtils.isNotBlank(entity.getMcfJobId())) {
			try {
				mcfBridge.abortJob(entity.getMcfJobId());
				mcfBridge.deleteJob(entity.getMcfJobId());
			}
			catch (Exception e) {
				log.warn("Failed to delete MCF job: {}", e.getMessage());
			}
		}

		// Delete MCF connection
		if (StringUtils.isNotBlank(entity.getMcfConnectionName())) {
			mcfBridge.deleteRepositoryConnection(entity.getMcfConnectionName());
		}

		this.removeById(entity.getId());
		log.info("Deleted source system '{}'", sourceId);
	}

	@Override
	public SourceSystem getSourceSystem(String sourceId) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		// Refresh job status from MCF
		if (StringUtils.isNotBlank(entity.getMcfJobId())) {
			try {
				Map<String, String> status = mcfBridge.getJobStatus(entity.getMcfJobId());
				entity.setMcfJobStatus(status.getOrDefault("status", entity.getMcfJobStatus()));

				String processed = status.get("documents_processed");
				if (processed != null) {
					entity.setDocsProcessed(Long.parseLong(processed));
				}
				String inQueue = status.get("documents_in_queue");
				String outstanding = status.get("documents_outstanding");
				if (inQueue != null && outstanding != null) {
					entity.setDocsTotal(
							entity.getDocsProcessed() + Long.parseLong(inQueue) + Long.parseLong(outstanding));
				}
				this.updateById(entity);
			}
			catch (Exception e) {
				log.debug("Could not refresh job status: {}", e.getMessage());
			}
		}

		return toDTO(entity);
	}

	@Override
	public PagingList<SourceSystem> listSourceSystems(BaseQuery query) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String workspaceId = context.getWorkspaceId();

		LambdaQueryWrapper<SourceSystemEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(SourceSystemEntity::getWorkspaceId, workspaceId);
		if (StringUtils.isNotBlank(query.getName())) {
			wrapper.like(SourceSystemEntity::getName, query.getName());
		}
		wrapper.orderByDesc(SourceSystemEntity::getGmtCreate);

		Page<SourceSystemEntity> page = new Page<>(query.getCurrent(), query.getSize());
		Page<SourceSystemEntity> result = this.page(page, wrapper);

		List<SourceSystem> items = result.getRecords().stream().map(this::toDTO).collect(Collectors.toList());

		return new PagingList<>((int) result.getCurrent(), (int) result.getSize(), result.getTotal(), items);
	}

	@Override
	public List<Map<String, String>> getConnectorTypes() {
		return mcfBridge.getConnectorTypes();
	}

	@Override
	public Map<String, String> testConnection(String sourceId) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		// Re-push current config to MCF before testing — ensures MCF connection
		// reflects the latest saved config (fixes stale config after updates)
		try {
			Map<String, Object> currentConfig = deserializeConfig(entity.getConnectionConfig());
			if (!currentConfig.isEmpty()) {
				mcfBridge.createRepositoryConnection(entity.getMcfConnectionName(), entity.getDescription(),
						entity.getConnectorClass(), currentConfig);
				log.info("Re-pushed config to MCF connection '{}' before testing", entity.getMcfConnectionName());
			}
		}
		catch (Exception e) {
			log.warn("Failed to re-push config to MCF connection '{}': {}", entity.getMcfConnectionName(),
					e.getMessage());
		}

		Map<String, String> result = mcfBridge.testConnection(entity.getMcfConnectionName());
		String testStatus = result.getOrDefault("result", "");
		entity.setTestResult(testStatus.contains("Connection working") ? "PASS" : "FAIL");
		entity.setGmtModified(new Date());
		this.updateById(entity);

		return result;
	}

	@Override
	public String startSync(String sourceId, String query) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		// Delete old job if exists
		if (StringUtils.isNotBlank(entity.getMcfJobId())) {
			try {
				mcfBridge.abortJob(entity.getMcfJobId());
				mcfBridge.deleteJob(entity.getMcfJobId());
			}
			catch (Exception e) {
				log.warn("Could not clean up old job: {}", e.getMessage());
			}
		}

		// Ensure MCF repository connection exists (may not have been created
		// if ManifoldCF was unavailable at source creation time)
		Map<String, Object> currentConfig = deserializeConfig(entity.getConnectionConfig());
		if (!currentConfig.isEmpty()) {
			mcfBridge.createRepositoryConnection(entity.getMcfConnectionName(), entity.getDescription(),
					entity.getConnectorClass(), currentConfig);
		}

		// Create and start new job
		String jobDescription = "Crawl: " + entity.getName();
		String jobId = mcfBridge.createCrawlJob(jobDescription, entity.getMcfConnectionName(),
				entity.getMcfOutputName(), query);

		mcfBridge.startJob(jobId);

		entity.setMcfJobId(jobId);
		entity.setMcfJobStatus("starting");
		entity.setDocsProcessed(0L);
		entity.setDocsFailed(0L);
		entity.setErrorMessage(null);
		entity.setGmtModified(new Date());
		this.updateById(entity);

		log.info("Started sync for source '{}' (jobId={})", entity.getName(), jobId);
		return jobId;
	}

	@Override
	public Map<String, String> getSyncStatus(String sourceId) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		if (StringUtils.isBlank(entity.getMcfJobId())) {
			Map<String, String> noJob = new HashMap<>();
			noJob.put("status", "idle");
			noJob.put("message", "No sync job has been started");
			return noJob;
		}

		Map<String, String> status = mcfBridge.getJobStatus(entity.getMcfJobId());

		// Update local record
		String jobStatus = status.getOrDefault("status", "unknown");
		entity.setMcfJobStatus(jobStatus);
		String processed = status.get("documents_processed");
		if (processed != null) {
			entity.setDocsProcessed(Long.parseLong(processed));
		}
		if ("done".equals(jobStatus) || "completed".equals(jobStatus)) {
			entity.setLastSyncTime(new Date());
		}
		entity.setGmtModified(new Date());
		this.updateById(entity);

		return status;
	}

	@Override
	public void abortSync(String sourceId) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			return;
		}
		if (StringUtils.isNotBlank(entity.getMcfJobId())) {
			mcfBridge.abortJob(entity.getMcfJobId());
			entity.setMcfJobStatus("aborting");
			entity.setGmtModified(new Date());
			this.updateById(entity);
		}
	}

	@Override
	public void updateSyncSchedule(String sourceId, String cronExpression) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}
		entity.setSyncCron(cronExpression);
		entity.setGmtModified(new Date());
		this.updateById(entity);
	}

	@Override
	public Map<String, String> enableSourceSystem(String sourceId) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		Map<String, String> validationResult = new HashMap<>();

		// Step 1: Test connection
		Map<String, String> connResult = mcfBridge.testConnection(entity.getMcfConnectionName());
		String connStatus = connResult.getOrDefault("result", "");
		boolean connPassed = connStatus.contains("Connection working");

		if (!connPassed) {
			validationResult.put("status", "FAIL");
			validationResult.put("connection_result", connStatus);
			validationResult.put("message", "Cannot enable: connection test failed");
			entity.setTestResult("FAIL");
			entity.setGmtModified(new Date());
			this.updateById(entity);
			return validationResult;
		}

		validationResult.put("connection_result", "Connection working");

		// Step 2: Check if ACL enforcement is configured (groupApiUrl present in config)
		Map<String, Object> config = deserializeConfig(entity.getConnectionConfig());
		String groupApiUrl = config.get("groupApiUrl") != null ? String.valueOf(config.get("groupApiUrl")) : "";

		if (!groupApiUrl.isEmpty()) {
			// ACL is configured — test Group API
			Map<String, String> groupResult = mcfBridge.testGroupApi(entity.getMcfConnectionName(), config);
			String groupStatus = groupResult.getOrDefault("status", "FAIL");

			validationResult.put("acl_result", groupResult.getOrDefault("result", ""));
			validationResult.put("acl_status", groupStatus);

			if ("FAIL".equals(groupStatus)) {
				validationResult.put("status", "FAIL");
				validationResult.put("message",
						"Cannot enable: connection works but ACL Group API validation failed");
				entity.setTestResult("FAIL");
				entity.setStatus(0);
				entity.setGmtModified(new Date());
				this.updateById(entity);
				return validationResult;
			}
			// WARN is acceptable — groups API works, members may have issues
		}

		// All checks passed — enable the source
		entity.setStatus(1);
		entity.setTestResult("PASS");
		entity.setGmtModified(new Date());
		this.updateById(entity);

		validationResult.put("status", "PASS");
		validationResult.put("message", "Source enabled successfully");
		log.info("Enabled source system '{}' (sourceId={})", entity.getName(), sourceId);
		return validationResult;
	}

	@Override
	public Map<String, String> testGroupApi(String sourceId) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		Map<String, Object> config = deserializeConfig(entity.getConnectionConfig());
		return mcfBridge.testGroupApi(entity.getMcfConnectionName(), config);
	}

	@Override
	public Map<String, Object> testQuery(String sourceId, String testType, String queryOverride) {
		SourceSystemEntity entity = findBySourceId(sourceId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		Map<String, Object> config = deserializeConfig(entity.getConnectionConfig());
		return mcfBridge.testQuery(config, entity.getConnectorClass(), testType, queryOverride);
	}

	@Override
	public String copySourceSystem(String sourceId) {
		SourceSystemEntity original = findBySourceId(sourceId);
		if (original == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("source_id", "Source system not found"));
		}

		SourceSystem copy = new SourceSystem();
		copy.setName(original.getName() + " (Copy)");
		copy.setDescription(original.getDescription());
		copy.setConnectorType(original.getConnectorType());
		copy.setConnectorClass(original.getConnectorClass());
		copy.setConnectionConfig(deserializeConfig(original.getConnectionConfig()));

		return createSourceSystem(copy);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private SourceSystemEntity findBySourceId(String sourceId) {
		LambdaQueryWrapper<SourceSystemEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(SourceSystemEntity::getSourceId, sourceId);
		return this.getOne(wrapper);
	}

	private SourceSystem toDTO(SourceSystemEntity entity) {
		SourceSystem dto = new SourceSystem();
		dto.setSourceId(entity.getSourceId());
		dto.setWorkspaceId(entity.getWorkspaceId());
		dto.setName(entity.getName());
		dto.setDescription(entity.getDescription());
		dto.setConnectorType(entity.getConnectorType());
		dto.setConnectorClass(entity.getConnectorClass());
		dto.setStatus(entity.getStatus());
		dto.setConnectionConfig(deserializeConfig(entity.getConnectionConfig()));
		dto.setTestResult(entity.getTestResult());
		dto.setMcfConnectionName(entity.getMcfConnectionName());
		dto.setMcfOutputName(entity.getMcfOutputName());
		dto.setMcfJobId(entity.getMcfJobId());
		dto.setMcfJobStatus(entity.getMcfJobStatus());
		dto.setLastSyncTime(entity.getLastSyncTime());
		dto.setSyncCron(entity.getSyncCron());
		dto.setDocsTotal(entity.getDocsTotal());
		dto.setDocsProcessed(entity.getDocsProcessed());
		dto.setDocsFailed(entity.getDocsFailed());
		dto.setErrorMessage(entity.getErrorMessage());
		dto.setGmtCreate(entity.getGmtCreate());
		dto.setGmtModified(entity.getGmtModified());
		dto.setCreator(entity.getCreator());
		dto.setModifier(entity.getModifier());
		return dto;
	}

	private String serializeConfig(Map<String, Object> config) {
		if (config == null) {
			return null;
		}
		try {
			return objectMapper.writeValueAsString(config);
		}
		catch (JsonProcessingException e) {
			return "{}";
		}
	}

	private Map<String, Object> deserializeConfig(String json) {
		if (json == null || json.isEmpty()) {
			return new HashMap<>();
		}
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (JsonProcessingException e) {
			return new HashMap<>();
		}
	}

}
