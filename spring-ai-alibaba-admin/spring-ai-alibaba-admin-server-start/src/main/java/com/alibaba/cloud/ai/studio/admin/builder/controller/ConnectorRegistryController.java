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

import com.alibaba.cloud.ai.studio.core.source.ConnectorRegistryService;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST API for connector service discovery and registration.
 *
 * <p>External connector microservices (CMIS, REST API, etc.) self-register
 * via these endpoints. The Admin App UI queries registered connectors
 * to show them as available source types.</p>
 *
 * <p>Registration lifecycle:
 * <ol>
 *   <li>Connector starts → POST /register</li>
 *   <li>Every 30s → POST /heartbeat</li>
 *   <li>Connector stops → POST /{id}/deregister</li>
 * </ol>
 * </p>
 */
@Slf4j
@RestController
@RequestMapping("/console/v1/connectors")
@Tag(name = "Connector Registry", description = "Dynamic connector service discovery")
public class ConnectorRegistryController {

	private final ConnectorRegistryService registryService;

	public ConnectorRegistryController(ConnectorRegistryService registryService) {
		this.registryService = registryService;
	}

	/**
	 * Register a connector microservice.
	 * Called by connector on startup.
	 */
	@PostMapping("/register")
	@Operation(summary = "Register a connector service")
	public Result<Map<String, String>> register(@RequestBody Map<String, Object> registration) {
		Map<String, String> result = registryService.register(registration);
		return Result.success(result);
	}

	/**
	 * Process heartbeat from a connector.
	 * Called every 30s to indicate the connector is alive.
	 */
	@PostMapping("/heartbeat")
	@Operation(summary = "Connector heartbeat")
	public Result<Map<String, String>> heartbeat(@RequestBody Map<String, Object> payload) {
		Map<String, String> result = registryService.heartbeat(payload);
		return Result.success(result);
	}

	/**
	 * Deregister a connector.
	 * Called by connector on graceful shutdown.
	 */
	@PostMapping("/{connectorId}/deregister")
	@Operation(summary = "Deregister a connector service")
	public Result<String> deregister(@PathVariable String connectorId) {
		registryService.deregister(connectorId);
		return Result.success("deregistered");
	}

	/**
	 * List all registered connectors (with real-time status).
	 */
	@GetMapping
	@Operation(summary = "List registered connectors")
	public Result<List<Map<String, Object>>> listConnectors() {
		return Result.success(registryService.listConnectors());
	}

	/**
	 * Get connector types from registered connectors.
	 * Returned alongside MCF connector types to populate the UI dropdown.
	 */
	@GetMapping("/types")
	@Operation(summary = "Get connector types from registered services")
	public Result<List<Map<String, String>>> getConnectorTypes() {
		return Result.success(registryService.getRegisteredConnectorTypes());
	}

}
