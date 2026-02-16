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

import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.source.SourceSystemService;
import com.alibaba.cloud.ai.studio.runtime.domain.BaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.source.SourceSystem;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.admin.builder.annotation.ApiModelAttribute;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for managing source systems (ManifoldCF-backed document crawlers). Provides
 * REST endpoints for source CRUD, connection testing, sync management, and scheduling.
 *
 * @since 1.0.0.3
 */
@RestController
@Tag(name = "source_system")
@RequestMapping("/console/v1/source-systems")
public class SourceSystemController {

	private final SourceSystemService sourceSystemService;

	public SourceSystemController(SourceSystemService sourceSystemService) {
		this.sourceSystemService = sourceSystemService;
	}

	/**
	 * Lists available connector types from ManifoldCF.
	 * @return List of connector type names and class names
	 */
	@GetMapping("/connector-types")
	public Result<List<Map<String, String>>> getConnectorTypes() {
		RequestContext context = RequestContextHolder.getRequestContext();
		List<Map<String, String>> types = sourceSystemService.getConnectorTypes();
		return Result.success(context.getRequestId(), types);
	}

	/**
	 * Creates a new source system and its ManifoldCF connection.
	 * @param source Source system configuration
	 * @return Created source ID
	 */
	@PostMapping
	public Result<String> createSourceSystem(@RequestBody SourceSystem source) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(source)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source"));
		}
		if (StringUtils.isBlank(source.getName())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("name"));
		}
		if (StringUtils.isBlank(source.getConnectorType())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("connector_type"));
		}
		if (StringUtils.isBlank(source.getConnectorClass())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("connector_class"));
		}
		if (source.getConnectionConfig() == null || source.getConnectionConfig().isEmpty()) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("connection_config"));
		}

		String sourceId = sourceSystemService.createSourceSystem(source);
		return Result.success(context.getRequestId(), sourceId);
	}

	/**
	 * Updates an existing source system.
	 * @param sourceId ID of the source to update
	 * @param source Updated configuration
	 * @return Success result
	 */
	@PutMapping("/{sourceId}")
	public Result<Void> updateSourceSystem(@PathVariable("sourceId") String sourceId,
			@RequestBody SourceSystem source) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		source.setSourceId(sourceId);
		sourceSystemService.updateSourceSystem(source);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Deletes a source system and its ManifoldCF connection/job.
	 * @param sourceId ID of the source to delete
	 * @return Success result
	 */
	@DeleteMapping("/{sourceId}")
	public Result<Void> deleteSourceSystem(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		sourceSystemService.deleteSourceSystem(sourceId);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Gets a source system by ID, with live MCF status refresh.
	 * @param sourceId ID of the source
	 * @return Source system details
	 */
	@GetMapping("/{sourceId}")
	public Result<SourceSystem> getSourceSystem(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		SourceSystem source = sourceSystemService.getSourceSystem(sourceId);
		return Result.success(context.getRequestId(), source);
	}

	/**
	 * Lists source systems with pagination and optional keyword filter.
	 * @param query Pagination and keyword query
	 * @return Paginated list of source systems
	 */
	@GetMapping
	public Result<PagingList<SourceSystem>> listSourceSystems(@ApiModelAttribute BaseQuery query) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(query)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("query"));
		}
		PagingList<SourceSystem> sources = sourceSystemService.listSourceSystems(query);
		return Result.success(context.getRequestId(), sources);
	}

	/**
	 * Tests the ManifoldCF connection for a source system.
	 * @param sourceId ID of the source to test
	 * @return Test results from ManifoldCF
	 */
	@PostMapping("/{sourceId}/test-connection")
	public Result<Map<String, String>> testConnection(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		Map<String, String> result = sourceSystemService.testConnection(sourceId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Starts a document sync (crawl job) for a source system.
	 * @param sourceId ID of the source
	 * @param body Optional request body with "query" field for custom crawl query
	 * @return The ManifoldCF job ID
	 */
	@PostMapping("/{sourceId}/sync")
	public Result<String> startSync(@PathVariable("sourceId") String sourceId,
			@RequestBody(required = false) Map<String, String> body) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		String query = (body != null) ? body.get("query") : null;
		String jobId = sourceSystemService.startSync(sourceId, query);
		return Result.success(context.getRequestId(), jobId);
	}

	/**
	 * Gets the current sync status for a source system.
	 * @param sourceId ID of the source
	 * @return Sync status from ManifoldCF
	 */
	@GetMapping("/{sourceId}/sync-status")
	public Result<Map<String, String>> getSyncStatus(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		Map<String, String> status = sourceSystemService.getSyncStatus(sourceId);
		return Result.success(context.getRequestId(), status);
	}

	/**
	 * Aborts a running sync job.
	 * @param sourceId ID of the source
	 * @return Success result
	 */
	@PostMapping("/{sourceId}/abort")
	public Result<Void> abortSync(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		sourceSystemService.abortSync(sourceId);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Updates the sync schedule (cron expression) for a source system.
	 * @param sourceId ID of the source
	 * @param body Request body with "cron" field
	 * @return Success result
	 */
	@PutMapping("/{sourceId}/schedule")
	public Result<Void> updateSyncSchedule(@PathVariable("sourceId") String sourceId,
			@RequestBody Map<String, String> body) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		String cron = body.get("cron");
		if (StringUtils.isBlank(cron)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("cron"));
		}
		sourceSystemService.updateSyncSchedule(sourceId, cron);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Enable a source system after validating connection and ACL configuration. The
	 * source must pass connection test and (if configured) ACL Group API validation
	 * before it can be enabled.
	 * @param sourceId ID of the source
	 * @return Validation result with status PASS/WARN/FAIL
	 */
	@PostMapping("/{sourceId}/enable")
	public Result<Map<String, String>> enableSourceSystem(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		Map<String, String> result = sourceSystemService.enableSourceSystem(sourceId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Test the Group/User API configuration for ACL enforcement. Returns PASS, WARN, or
	 * FAIL with details about the groups and members retrieved.
	 * @param sourceId ID of the source
	 * @return Test result map with status, result, groups_found, members_sample
	 */
	@PostMapping("/{sourceId}/test-group-api")
	public Result<Map<String, String>> testGroupApi(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		Map<String, String> result = sourceSystemService.testGroupApi(sourceId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Test a query against the source system. For CMIS connectors, runs a CMIS query.
	 * For REST API connectors, calls the seed endpoint. For groupApi/userApi, tests those
	 * API endpoints. Returns item count and top 10 sample results.
	 * @param sourceId ID of the source
	 * @param body Request body with "test_type" (query|groupApi|userApi) and optional
	 * "query" for custom query string
	 * @return Map with count, items[], status, message
	 */
	@PostMapping("/{sourceId}/test-query")
	public Result<Map<String, Object>> testQuery(@PathVariable("sourceId") String sourceId,
			@RequestBody(required = false) Map<String, String> body) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		String testType = (body != null) ? body.getOrDefault("test_type", "query") : "query";
		String queryOverride = (body != null) ? body.get("query") : null;
		Map<String, Object> result = sourceSystemService.testQuery(sourceId, testType, queryOverride);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Copy an existing source system. Creates a duplicate with " (Copy)" appended to the
	 * name. The copy starts in Draft status (status=0).
	 * @param sourceId ID of the source to copy
	 * @return The new source ID
	 */
	@PostMapping("/{sourceId}/copy")
	public Result<String> copySourceSystem(@PathVariable("sourceId") String sourceId) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (StringUtils.isBlank(sourceId)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("source_id"));
		}
		String newSourceId = sourceSystemService.copySourceSystem(sourceId);
		return Result.success(context.getRequestId(), newSourceId);
	}

}
