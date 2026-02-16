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

package com.alibaba.cloud.ai.studio.admin.builder.controller;

import com.alibaba.cloud.ai.studio.admin.builder.annotation.ApiModelAttribute;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.destination.DestinationService;
import com.alibaba.cloud.ai.studio.runtime.domain.BaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.destination.Destination;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Controller for managing destination systems (e.g., OpenSearch). Provides REST endpoints
 * for destination CRUD and connection testing.
 *
 * @since 1.0.0.3
 */
@RestController
@Tag(name = "destination")
@RequestMapping("/console/v1/destinations")
public class DestinationController {

	private final DestinationService destinationService;

	public DestinationController(DestinationService destinationService) {
		this.destinationService = destinationService;
	}

	/**
	 * Lists available destination provider types.
	 */
	@GetMapping("/provider-types")
	public Result<List<Map<String, String>>> getProviderTypes() {
		RequestContext context = RequestContextHolder.getRequestContext();
		List<Map<String, String>> types = destinationService.getProviderTypes();
		return Result.success(context.getRequestId(), types);
	}

	/**
	 * Creates a new destination.
	 */
	@PostMapping
	public Result<String> createDestination(@RequestBody Destination destination) {
		RequestContext context = RequestContextHolder.getRequestContext();

		if (Objects.isNull(destination)) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("destination"));
		}
		if (StringUtils.isBlank(destination.getName())) {
			throw new BizException(ErrorCode.MISSING_PARAMS.toError("name"));
		}

		String id = destinationService.createDestination(destination);
		return Result.success(context.getRequestId(), id);
	}

	/**
	 * Updates an existing destination.
	 */
	@PutMapping("/{destinationId}")
	public Result<Void> updateDestination(@PathVariable String destinationId, @RequestBody Destination destination) {
		RequestContext context = RequestContextHolder.getRequestContext();

		destination.setDestinationId(destinationId);
		destinationService.updateDestination(destination);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Deletes a destination.
	 */
	@DeleteMapping("/{destinationId}")
	public Result<Void> deleteDestination(@PathVariable String destinationId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		destinationService.deleteDestination(destinationId);
		return Result.success(context.getRequestId(), null);
	}

	/**
	 * Gets a destination by ID.
	 */
	@GetMapping("/{destinationId}")
	public Result<Destination> getDestination(@PathVariable String destinationId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Destination destination = destinationService.getDestination(destinationId);
		return Result.success(context.getRequestId(), destination);
	}

	/**
	 * Lists destinations with pagination.
	 */
	@GetMapping
	public Result<PagingList<Destination>> listDestinations(@ApiModelAttribute BaseQuery query) {
		RequestContext context = RequestContextHolder.getRequestContext();
		PagingList<Destination> list = destinationService.listDestinations(query);
		return Result.success(context.getRequestId(), list);
	}

	/**
	 * Tests connection for a saved destination.
	 */
	@PostMapping("/{destinationId}/test-connection")
	public Result<Map<String, String>> testConnection(@PathVariable String destinationId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, String> result = destinationService.testConnection(destinationId);
		return Result.success(context.getRequestId(), result);
	}

	/**
	 * Tests connection with inline config (before saving).
	 */
	@PostMapping("/test-connection")
	public Result<Map<String, String>> testConnectionInline(@RequestBody Destination destination) {
		RequestContext context = RequestContextHolder.getRequestContext();
		Map<String, String> result = destinationService.testConnectionInline(destination);
		return Result.success(context.getRequestId(), result);
	}

}
