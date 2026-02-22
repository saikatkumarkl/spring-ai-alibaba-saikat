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

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Registry for dynamically discovered connectors (CMIS, REST API, etc.).
 *
 * <p>External connector microservices self-register on startup, send periodic
 * heartbeats, and deregister on shutdown. The Admin App uses this registry
 * to determine available connector types and route crawl/test operations
 * to the appropriate connector service instead of ManifoldCF.</p>
 *
 * <p>Connectors that miss heartbeats beyond {@link #HEARTBEAT_TIMEOUT_MS}
 * are automatically marked as OFFLINE and cleaned up.</p>
 *
 * <p>Matching logic: When a source system has a connectorClass like
 * {@code org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnector},
 * we match it against registered connectors by connector_type (e.g., "cmis").
 * A mapping from MCF class names to connector types is maintained so that
 * sources created in the UI (which use MCF class names) are routed correctly
 * to external connectors.</p>
 */
@Slf4j
@Service
public class ConnectorRegistryService {

	/** Heartbeat timeout: connector is considered OFFLINE after this duration (ms). */
	private static final long HEARTBEAT_TIMEOUT_MS = 90_000; // 90 seconds (3 missed heartbeats)

	/**
	 * Mapping from ManifoldCF class names to connector types.
	 * When the frontend creates a source using an MCF class name, we look up
	 * the connector type and match against registered connectors.
	 */
	private static final Map<String, String> MCF_CLASS_TO_TYPE = Map.of(
		"org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnector", "cmis",
		"org.apache.manifoldcf.crawler.connectors.restapi.RestApiRepositoryConnector", "rest_api"
	);

	/**
	 * Reverse mapping: connector type → MCF class name.
	 * The frontend identifies connectors by MCF class name (used for vendor presets,
	 * field definitions, ACL support checks). Registered connectors must appear with
	 * the MCF class name so the frontend's existing logic works unchanged.
	 */
	private static final Map<String, String> TYPE_TO_MCF_CLASS = Map.of(
		"cmis", "org.apache.manifoldcf.crawler.connectors.cmis.CmisRepositoryConnector",
		"rest_api", "org.apache.manifoldcf.crawler.connectors.restapi.RestApiRepositoryConnector"
	);

	/** In-memory registry: connectorId → registration data. */
	private final ConcurrentHashMap<String, Map<String, Object>> connectors = new ConcurrentHashMap<>();

	/** Last heartbeat timestamp per connector. */
	private final ConcurrentHashMap<String, Instant> heartbeats = new ConcurrentHashMap<>();

	/**
	 * Register a connector.
	 *
	 * @param registration connector metadata (must include connectorId, type, baseUrl)
	 * @return registration result
	 */
	public Map<String, String> register(Map<String, Object> registration) {
		// Connector sends snake_case JSON keys; normalize to simple keys for internal use
		String connectorId = getStringAny(registration, "connector_id", "connectorId");
		String type = getStringAny(registration, "connector_type", "type");
		String connectorClass = getStringAny(registration, "connector_class", "class");
		String baseUrl = getStringAny(registration, "base_url", "baseUrl");
		String name = getStringAny(registration, "connector_name", "name");
		String healthUrl = getStringAny(registration, "health_url", "healthUrl");
		String configSchemaRaw = registration.containsKey("config_schema") ? "present" : null;

		if (connectorId == null || type == null || baseUrl == null) {
			throw new IllegalArgumentException("connector_id, connector_type, and base_url are required");
		}

		// Store with normalized keys for consistent internal lookup
		Map<String, Object> normalized = new LinkedHashMap<>(registration);
		normalized.put("connectorId", connectorId);
		normalized.put("type", type);
		normalized.put("class", connectorClass);
		normalized.put("baseUrl", baseUrl);
		normalized.put("name", name);
		normalized.put("healthUrl", healthUrl);
		normalized.put("status", "ONLINE");
		normalized.put("registeredAt", Instant.now().toString());
		connectors.put(connectorId, normalized);
		heartbeats.put(connectorId, Instant.now());

		log.info("Connector registered: id={}, type={}, class={}, baseUrl={}", connectorId, type, connectorClass, baseUrl);

		Map<String, String> result = new LinkedHashMap<>();
		result.put("status", "registered");
		result.put("connectorId", connectorId);
		return result;
	}

	/**
	 * Process a heartbeat from a connector.
	 */
	public Map<String, String> heartbeat(Map<String, Object> payload) {
		String connectorId = getStringAny(payload, "connector_id", "connectorId");
		if (connectorId == null || !connectors.containsKey(connectorId)) {
			Map<String, String> result = new LinkedHashMap<>();
			result.put("status", "unknown");
			result.put("message", "Connector not registered. Please re-register.");
			return result;
		}

		heartbeats.put(connectorId, Instant.now());
		Map<String, Object> existing = connectors.get(connectorId);
		existing.put("status", "ONLINE");

		Map<String, String> result = new LinkedHashMap<>();
		result.put("status", "ok");
		return result;
	}

	/**
	 * Deregister a connector (called on shutdown).
	 */
	public void deregister(String connectorId) {
		connectors.remove(connectorId);
		heartbeats.remove(connectorId);
		log.info("Connector deregistered: id={}", connectorId);
	}

	/**
	 * List all registered connectors (excludes stale ones).
	 */
	public List<Map<String, Object>> listConnectors() {
		evictStale();
		return new ArrayList<>(connectors.values());
	}

	/**
	 * Find a registered connector by connector class/type.
	 * Returns the first ONLINE connector matching the given class.
	 *
	 * <p>Matching strategy (in order):
	 * <ol>
	 *   <li>Direct match: connectorClass equals registered connector's class</li>
	 *   <li>Direct match: connectorClass equals registered connector's type</li>
	 *   <li>MCF translation: connectorClass is an MCF class name → resolve to type → match</li>
	 * </ol>
	 *
	 * @param connectorClass the connector class (MCF class name or connector microservice class)
	 * @return connector registration or null
	 */
	public Map<String, Object> findByClass(String connectorClass) {
		evictStale();
		for (Map<String, Object> reg : connectors.values()) {
			String regClass = getString(reg, "class");
			String regType = getString(reg, "type");
			String status = getString(reg, "status");

			if (!"ONLINE".equals(status)) {
				continue;
			}

			// Direct match by class name or type
			if (connectorClass.equals(regClass) || connectorClass.equals(regType)) {
				return reg;
			}

			// MCF class name → connector type translation
			String resolvedType = MCF_CLASS_TO_TYPE.get(connectorClass);
			if (resolvedType != null && resolvedType.equals(regType)) {
				return reg;
			}
		}
		return null;
	}

	/**
	 * Find a registered connector by type (e.g., "cmis").
	 */
	public Map<String, Object> findByType(String type) {
		evictStale();
		for (Map<String, Object> reg : connectors.values()) {
			if (type.equals(getString(reg, "type")) && "ONLINE".equals(getString(reg, "status"))) {
				return reg;
			}
		}
		return null;
	}

	/**
	 * Get connector types from registered connectors (for combining with MCF types).
	 */
	public List<Map<String, String>> getRegisteredConnectorTypes() {
		evictStale();
		return connectors.values()
			.stream()
			.filter(r -> "ONLINE".equals(getString(r, "status")))
			.map(r -> {
				Map<String, String> typeInfo = new LinkedHashMap<>();
				String connType = getString(r, "type");
				// Frontend identifies connectors by MCF class name (used for vendor presets,
				// field definitions, ACL support checks). Translate type → MCF class so the
				// frontend's existing ConnectorType / CLS logic works unchanged.
				String mcfClass = TYPE_TO_MCF_CLASS.getOrDefault(connType, getString(r, "class"));
				typeInfo.put("class_name", mcfClass);
				typeInfo.put("description", getString(r, "name"));
				typeInfo.put("type", connType);
				typeInfo.put("version", getString(r, "version"));
				typeInfo.put("source", "connector-service"); // distinguishes from MCF
				return typeInfo;
			})
			.collect(Collectors.toList());
	}

	/**
	 * Check if there is an active connector for the given connector class.
	 */
	public boolean hasActiveConnector(String connectorClass) {
		return findByClass(connectorClass) != null;
	}

	/**
	 * Get the base URL for a connector by class.
	 */
	public String getBaseUrl(String connectorClass) {
		Map<String, Object> reg = findByClass(connectorClass);
		return reg != null ? getString(reg, "baseUrl") : null;
	}

	// ── Internal ─────────────────────────────────────────────────────────

	private void evictStale() {
		Instant cutoff = Instant.now().minusMillis(HEARTBEAT_TIMEOUT_MS);
		heartbeats.forEach((id, lastSeen) -> {
			if (lastSeen.isBefore(cutoff)) {
				Map<String, Object> reg = connectors.get(id);
				if (reg != null) {
					reg.put("status", "OFFLINE");
					log.warn("Connector '{}' marked OFFLINE (no heartbeat since {})", id, lastSeen);
				}
			}
		});
	}

	private String getString(Map<String, Object> map, String key) {
		Object val = map.get(key);
		return val != null ? val.toString() : null;
	}

	/** Try multiple key names (for snake_case/camelCase compatibility). */
	private String getStringAny(Map<String, Object> map, String... keys) {
		for (String key : keys) {
			Object val = map.get(key);
			if (val != null) {
				return val.toString();
			}
		}
		return null;
	}

}
