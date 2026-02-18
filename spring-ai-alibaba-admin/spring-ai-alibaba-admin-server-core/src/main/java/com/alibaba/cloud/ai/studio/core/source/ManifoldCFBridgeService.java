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

import com.alibaba.cloud.ai.studio.core.rag.impl.KnowledgeSyncServiceImpl;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Bridge service to ManifoldCF REST API.
 * Handles creating repository connections, output connections, and jobs via MCF.
 */
@Slf4j
@Service
public class ManifoldCFBridgeService {

	private final RestTemplate restTemplate;

	private final ObjectMapper objectMapper;

	@Value("${manifoldcf.api.url:http://manifoldcf:8345/mcf-api-service}")
	private String mcfApiUrl;

	@Value("${manifoldcf.opensearch.output:OpenSearch}")
	private String defaultOutputConnection;

	@Value("${manifoldcf.api.username:admin}")
	private String mcfApiUsername;

	@Value("${manifoldcf.api.password:admin}")
	private String mcfApiPassword;

	@Value("${manifoldcf.opensearch.url:http://opensearch:9200/}")
	private String mcfOpensearchUrl;

	public ManifoldCFBridgeService() {
		this.restTemplate = new RestTemplate();
		this.objectMapper = new ObjectMapper();

		// Add Basic Auth interceptor for all MCF API calls
		this.restTemplate.getInterceptors().add(mcfBasicAuthInterceptor());
	}

	/**
	 * Creates a ClientHttpRequestInterceptor that adds Basic Auth
	 * headers to requests targeting the ManifoldCF API.
	 */
	private ClientHttpRequestInterceptor mcfBasicAuthInterceptor() {
		return (request, body, execution) -> {
			// Only add auth for MCF API calls (not for external group API calls, etc.)
			String uri = request.getURI().toString();
			if (uri.contains("/mcf-api-service/")) {
				String credentials = mcfApiUsername + ":" + mcfApiPassword;
				String encoded = Base64.getEncoder()
					.encodeToString(credentials.getBytes(StandardCharsets.UTF_8));
				request.getHeaders().set("Authorization", "Basic " + encoded);
			}
			return execution.execute(request, body);
		};
	}

	// ── Connector Types ──────────────────────────────────────────────────

	/**
	 * Get all available repository connector types from ManifoldCF.
	 */
	public List<Map<String, String>> getConnectorTypes() {
		try {
			String url = mcfApiUrl + "/json/repositoryconnectors";
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			JsonNode root = objectMapper.readTree(response.getBody());
			JsonNode connectors = root.get("repositoryconnector");

			List<Map<String, String>> result = new ArrayList<>();
			if (connectors != null && connectors.isArray()) {
				for (JsonNode connector : connectors) {
					JsonNode children = connector.get("_children_");
					if (children != null) {
						Map<String, String> entry = new HashMap<>();
						for (JsonNode child : children) {
							String type = child.get("_type_").asText();
							String value = child.get("_value_").asText();
							entry.put(type, value);
						}
						result.add(entry);
					}
				}
			}
			return result;
		}
		catch (Exception e) {
			log.error("Failed to get connector types from ManifoldCF", e);
			return Collections.emptyList();
		}
	}

	// ── Repository Connections ───────────────────────────────────────────

	/**
	 * Sanitize connection config values before sending to ManifoldCF. Strips protocol
	 * prefixes from server fields and normalizes protocol values.
	 */
	private Map<String, Object> sanitizeConfig(Map<String, Object> config) {
		if (config == null) {
			return null;
		}
		Map<String, Object> sanitized = new LinkedHashMap<>(config);

		// Strip "://" from protocol (e.g., "https://" → "https")
		Object protocol = sanitized.get("protocol");
		if (protocol instanceof String p) {
			p = p.replaceAll("://.*", "").trim();
			if (p.isEmpty()) {
				p = "https";
			}
			sanitized.put("protocol", p);
		}

		// Strip leading "http://" or "https://" from server (e.g.,
		// "https://host.com" → "host.com")
		Object server = sanitized.get("server");
		if (server instanceof String s) {
			s = s.replaceAll("^https?://", "").trim();
			// Also strip trailing slashes
			s = s.replaceAll("/+$", "");
			sanitized.put("server", s);
		}

		// Ensure port is numeric
		Object port = sanitized.get("port");
		if (port instanceof String p) {
			p = p.replaceAll("[^0-9]", "");
			if (p.isEmpty()) {
				p = "443";
			}
			sanitized.put("port", p);
		}

		return sanitized;
	}

	/**
	 * Create a repository connection in ManifoldCF.
	 */
	public String createRepositoryConnection(String name, String description, String connectorClass,
			Map<String, Object> config) {
		try {
			// Sanitize config values to prevent issues like doubled protocols
			Map<String, Object> safeConfig = sanitizeConfig(config);

			// Check if connection already exists — use isnew=false for updates
			boolean exists = connectionExists(name);

			ObjectNode payload = objectMapper.createObjectNode();
			ArrayNode children = objectMapper.createArrayNode();

			addChild(children, "isnew", exists ? "false" : "true");
			addChild(children, "name", name);
			addChild(children, "class_name", connectorClass);
			addChild(children, "max_connections", "10");
			if (description != null) {
				addChild(children, "description", description);
			}

			// Build configuration parameters
			ObjectNode configNode = objectMapper.createObjectNode();
			ArrayNode params = objectMapper.createArrayNode();
			if (safeConfig != null) {
				for (Map.Entry<String, Object> entry : safeConfig.entrySet()) {
					ObjectNode param = objectMapper.createObjectNode();
					param.put("_value_", String.valueOf(entry.getValue()));
					param.put("_attribute_name", entry.getKey());
					params.add(param);
				}
			}
			configNode.set("_PARAMETER_", params);

			ObjectNode configChild = objectMapper.createObjectNode();
			configChild.put("_type_", "configuration");
			configChild.set("_PARAMETER_", params);
			children.add(configChild);

			payload.set("_children_", children);
			ObjectNode wrapper = objectMapper.createObjectNode();
			wrapper.set("repositoryconnection", payload);

			String url = mcfApiUrl + "/json/repositoryconnections/" + urlEncode(name);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(wrapper), headers);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
			log.info("Created MCF repository connection '{}': {}", name, response.getStatusCode());
			return name;
		}
		catch (Exception e) {
			log.error("Failed to create repository connection '{}': {}", name, e.getMessage(), e);
			throw new RuntimeException("Failed to create ManifoldCF connection: " + e.getMessage(), e);
		}
	}

	/**
	 * Test a repository connection in ManifoldCF.
	 * MCF status API may return either:
	 *   - {"repositoryconnectionstatus":{"_children_":[{"_type_":"result","_value_":"..."}]}}
	 *   - {"check_result":"Connection working"}  (simplified format)
	 */
	public Map<String, String> testConnection(String connectionName) {
		try {
			String url = mcfApiUrl + "/json/status/repositoryconnections/" + urlEncode(connectionName);
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			JsonNode root = objectMapper.readTree(response.getBody());

			Map<String, String> result = new HashMap<>();

			// Try the _children_ format first
			JsonNode children = root.path("repositoryconnectionstatus").path("_children_");
			if (children.isArray() && children.size() > 0) {
				for (JsonNode child : children) {
					String type = child.get("_type_").asText();
					String value = child.get("_value_").asText();
					result.put(type, value);
				}
				return result;
			}

			// Fallback: simplified format {"check_result": "Connection working"}
			JsonNode checkResult = root.get("check_result");
			if (checkResult != null) {
				result.put("result", checkResult.asText());
				return result;
			}

			// Try to extract any meaningful field from the response
			Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
			while (fields.hasNext()) {
				Map.Entry<String, JsonNode> field = fields.next();
				if (field.getValue().isTextual()) {
					result.put(field.getKey(), field.getValue().asText());
				}
			}

			if (result.isEmpty()) {
				result.put("result", "Unknown response format from ManifoldCF");
			}
			return result;
		}
		catch (Exception e) {
			log.error("Failed to test connection '{}': {}", connectionName, e.getMessage());
			Map<String, String> error = new HashMap<>();
			error.put("result", "Connection failed: " + e.getMessage());
			return error;
		}
	}

	/**
	 * Check if a repository connection exists in ManifoldCF.
	 */
	private boolean connectionExists(String name) {
		try {
			String url = mcfApiUrl + "/json/repositoryconnections/" + urlEncode(name);
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
				JsonNode root = objectMapper.readTree(response.getBody());
				// MCF returns {"repositoryconnection":{...}} when it exists
				return root.has("repositoryconnection");
			}
			return false;
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Test the Group API configuration for a repository connection.
	 * This calls ManifoldCF's processConfigurationPost with _testGroupApi=true trigger,
	 * which runs the CMIS connector's testGroupApiConnection() method.
	 *
	 * Since MCF's group API test is only available via form POST (JSP), we instead
	 * make the HTTP calls directly from the admin backend using the connection's config.
	 */
	public Map<String, String> testGroupApi(String connectionName, Map<String, Object> config) {
		Map<String, String> result = new HashMap<>();

		Map<String, Object> safeConfig = sanitizeConfig(config);
		String protocol = getConfigString(safeConfig, "protocol", "https");
		String server = getConfigString(safeConfig, "server", "");
		String port = getConfigString(safeConfig, "port", "443");
		String username = getConfigString(safeConfig, "username", "");
		String password = getConfigString(safeConfig, "password", "");
		String groupApiUrl = getConfigString(safeConfig, "groupApiUrl", "");
		String groupMembersApiUrl = getConfigString(safeConfig, "groupMembersApiUrl", "");
		String vendor = getConfigString(safeConfig, "cmisVendor", "other");

		if (groupApiUrl.isEmpty()) {
			result.put("result", "FAIL|Group API URL is not configured");
			result.put("status", "FAIL");
			return result;
		}
		if (groupMembersApiUrl.isEmpty()) {
			result.put("result", "FAIL|Group Members API URL is not configured");
			result.put("status", "FAIL");
			return result;
		}
		if (server.isEmpty()) {
			result.put("result", "FAIL|Server hostname is not configured");
			result.put("status", "FAIL");
			return result;
		}

		String baseUrl = protocol + "://" + server + ":" + port;
		String groupsFullUrl = baseUrl + groupApiUrl;

		// Test Groups API
		try {
			ResponseEntity<String> groupsResponse = makeAuthenticatedGet(groupsFullUrl, username, password);
			if (groupsResponse.getStatusCode().is2xxSuccessful()) {
				String body = groupsResponse.getBody();
				if (body != null && !body.trim().isEmpty()) {
					result.put("groups_api", "OK");
					result.put("groups_response_preview", truncateString(body, 500));

					// Test Members API with first group from the response
					String testGroupId = extractFirstGroupId(body);
					String membersFullUrl = baseUrl + groupMembersApiUrl;
					// Replace placeholder with the discovered group ID
					membersFullUrl = membersFullUrl.replace("{groupId}", testGroupId);
					try {
						ResponseEntity<String> membersResponse = makeAuthenticatedGet(membersFullUrl, username,
								password);
						if (membersResponse.getStatusCode().is2xxSuccessful()) {
							String membersBody = membersResponse.getBody();
							if (membersBody != null && !membersBody.trim().isEmpty()) {
								result.put("members_api", "OK");
								result.put("members_response_preview", truncateString(membersBody, 500));
								result.put("result", "PASS|Groups API and Members API are responding correctly");
								result.put("status", "PASS");
							}
							else {
								result.put("members_api", "WARN");
								result.put("result",
										"WARN|Groups API works but Members API returned empty response");
								result.put("status", "WARN");
							}
						}
						else {
							result.put("members_api", "WARN");
							result.put("result",
									"WARN|Groups API works but Members API returned HTTP "
											+ membersResponse.getStatusCode().value());
							result.put("status", "WARN");
						}
					}
					catch (Exception e) {
						result.put("members_api", "WARN");
						result.put("result",
								"WARN|Groups API works but Members API failed: " + e.getMessage());
						result.put("status", "WARN");
					}
				}
				else {
					result.put("groups_api", "FAIL");
					result.put("result", "FAIL|Groups API returned empty response");
					result.put("status", "FAIL");
				}
			}
			else {
				result.put("groups_api", "FAIL");
				result.put("result",
						"FAIL|Groups API returned HTTP " + groupsResponse.getStatusCode().value());
				result.put("status", "FAIL");
			}
		}
		catch (Exception e) {
			result.put("groups_api", "FAIL");
			result.put("result", "FAIL|Groups API error: " + e.getMessage()
					+ "\nURL: " + groupsFullUrl
					+ "\nPlease check credentials, server address, and API URL path.");
			result.put("status", "FAIL");
		}

		return result;
	}

	/**
	 * Test only the Groups list API endpoint. Returns group entries as items.
	 */
	private Map<String, Object> testGroupsListApi(Map<String, Object> config) {
		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> safeConfig = sanitizeConfig(config);

		String protocol = getConfigString(safeConfig, "protocol", "https");
		String server = getConfigString(safeConfig, "server", "");
		String port = getConfigString(safeConfig, "port", "443");
		String username = getConfigString(safeConfig, "username", "");
		String password = getConfigString(safeConfig, "password", "");
		String groupApiUrl = getConfigString(safeConfig, "groupApiUrl", "");

		if (groupApiUrl.isEmpty()) {
			result.put("status", "FAIL");
			result.put("message", "Group API URL is not configured");
			return result;
		}
		if (server.isEmpty()) {
			result.put("status", "FAIL");
			result.put("message", "Server hostname is not configured");
			return result;
		}

		String baseUrl = protocol + "://" + server + ":" + port;
		// Add pagination param to get more groups (default may only return 100)
		String fullUrl = baseUrl + groupApiUrl;
		if (!fullUrl.contains("maxItems=") && !fullUrl.contains("skipCount=")) {
			fullUrl += (fullUrl.contains("?") ? "&" : "?") + "maxItems=1000";
		}

		try {
			ResponseEntity<String> response = makeAuthenticatedGet(fullUrl, username, password);
			if (response.getStatusCode().is2xxSuccessful()) {
				String body = response.getBody();
				if (body != null && !body.trim().isEmpty()) {
					result.put("status", "PASS");
					result.put("message", "Groups API is responding correctly");
					parseGroupApiItems(result, body);
				}
				else {
					result.put("status", "FAIL");
					result.put("message", "Groups API returned empty response");
				}
			}
			else {
				result.put("status", "FAIL");
				result.put("message", "Groups API returned HTTP " + response.getStatusCode().value());
			}
		}
		catch (Exception e) {
			result.put("status", "FAIL");
			result.put("message", "Groups API error: " + e.getMessage()
					+ "\nURL: " + fullUrl);
		}
		return result;
	}

	/**
	 * Test only the Group Members API endpoint using the first group from the Groups
	 * list.
	 */
	private Map<String, Object> testGroupMembersApi(Map<String, Object> config) {
		Map<String, Object> result = new LinkedHashMap<>();
		Map<String, Object> safeConfig = sanitizeConfig(config);

		String protocol = getConfigString(safeConfig, "protocol", "https");
		String server = getConfigString(safeConfig, "server", "");
		String port = getConfigString(safeConfig, "port", "443");
		String username = getConfigString(safeConfig, "username", "");
		String password = getConfigString(safeConfig, "password", "");
		String groupApiUrl = getConfigString(safeConfig, "groupApiUrl", "");
		String groupMembersApiUrl = getConfigString(safeConfig, "groupMembersApiUrl", "");

		if (groupMembersApiUrl.isEmpty()) {
			result.put("status", "FAIL");
			result.put("message", "Group Members API URL is not configured");
			return result;
		}
		if (server.isEmpty()) {
			result.put("status", "FAIL");
			result.put("message", "Server hostname is not configured");
			return result;
		}

		String baseUrl = protocol + "://" + server + ":" + port;

		try {
			// First fetch groups to get a test group ID
			String testGroupId = "GROUP_EVERYONE";
			if (!groupApiUrl.isEmpty()) {
				String groupsUrl = baseUrl + groupApiUrl;
				ResponseEntity<String> groupsResp = makeAuthenticatedGet(groupsUrl, username, password);
				if (groupsResp.getStatusCode().is2xxSuccessful() && groupsResp.getBody() != null) {
					testGroupId = extractFirstGroupId(groupsResp.getBody());
				}
			}

			// Now test the members endpoint
			String membersUrl = baseUrl + groupMembersApiUrl.replace("{groupId}", testGroupId);
			ResponseEntity<String> membersResp = makeAuthenticatedGet(membersUrl, username, password);

			if (membersResp.getStatusCode().is2xxSuccessful()) {
				String body = membersResp.getBody();
				if (body != null && !body.trim().isEmpty()) {
					result.put("status", "PASS");
					result.put("message",
							"Members API is responding correctly (tested with group: " + testGroupId + ")");
					parseMembersApiItems(result, body);
				}
				else {
					result.put("status", "WARN");
					result.put("message", "Members API returned empty response for group: " + testGroupId);
				}
			}
			else {
				result.put("status", "FAIL");
				result.put("message", "Members API returned HTTP " + membersResp.getStatusCode().value()
						+ " for group: " + testGroupId);
			}
		}
		catch (Exception e) {
			result.put("status", "FAIL");
			result.put("message", "Members API error: " + e.getMessage());
		}
		return result;
	}

	/**
	 * Parse Alfresco Groups API JSON response into items list.
	 */
	private void parseGroupApiItems(Map<String, Object> result, String body) {
		List<Map<String, Object>> items = new ArrayList<>();
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> root = objectMapper.readValue(body, Map.class);
			if (root.containsKey("list")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> list = (Map<String, Object>) root.get("list");
				if (list != null) {
					// Get pagination info
					if (list.containsKey("pagination")) {
						@SuppressWarnings("unchecked")
						Map<String, Object> pagination = (Map<String, Object>) list.get("pagination");
						if (pagination != null && pagination.containsKey("totalItems")) {
							result.put("count", ((Number) pagination.get("totalItems")).intValue());
						}
					}
					if (list.containsKey("entries")) {
						@SuppressWarnings("unchecked")
						java.util.List<Map<String, Object>> entries = (java.util.List<Map<String, Object>>) list
							.get("entries");
						if (entries != null) {
							for (int i = 0; i < entries.size(); i++) {
								@SuppressWarnings("unchecked")
								Map<String, Object> entry = (Map<String, Object>) entries.get(i).get("entry");
								if (entry != null) {
									Map<String, Object> item = new LinkedHashMap<>();
									item.put("name", entry.getOrDefault("displayName",
											entry.getOrDefault("id", "")));
									item.put("objectId", entry.getOrDefault("id", ""));
									item.put("type", entry.getOrDefault("isRoot", false).equals(true) ? "Root Group"
											: "Group");
									items.add(item);
								}
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("Failed to parse groups API response: {}", e.getMessage());
		}
		if (!result.containsKey("count")) {
			result.put("count", items.size());
		}
		result.put("items", items);
	}

	/**
	 * Parse Alfresco Group Members API JSON response into items list.
	 */
	private void parseMembersApiItems(Map<String, Object> result, String body) {
		List<Map<String, Object>> items = new ArrayList<>();
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> root = objectMapper.readValue(body, Map.class);
			if (root.containsKey("list")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> list = (Map<String, Object>) root.get("list");
				if (list != null) {
					if (list.containsKey("pagination")) {
						@SuppressWarnings("unchecked")
						Map<String, Object> pagination = (Map<String, Object>) list.get("pagination");
						if (pagination != null && pagination.containsKey("totalItems")) {
							result.put("count", ((Number) pagination.get("totalItems")).intValue());
						}
					}
					if (list.containsKey("entries")) {
						@SuppressWarnings("unchecked")
						java.util.List<Map<String, Object>> entries = (java.util.List<Map<String, Object>>) list
							.get("entries");
						if (entries != null) {
							for (int i = 0; i < entries.size() && i < 10; i++) {
								@SuppressWarnings("unchecked")
								Map<String, Object> entry = (Map<String, Object>) entries.get(i).get("entry");
								if (entry != null) {
									Map<String, Object> item = new LinkedHashMap<>();
									String displayName = String
										.valueOf(entry.getOrDefault("displayName", entry.getOrDefault("id", "")));
									String type = String.valueOf(entry.getOrDefault("memberType", "UNKNOWN"));
									item.put("name", displayName);
									item.put("objectId", entry.getOrDefault("id", ""));
									item.put("type", type);
									items.add(item);
								}
							}
						}
					}
				}
			}
		}
		catch (Exception e) {
			log.warn("Failed to parse members API response: {}", e.getMessage());
		}
		if (!result.containsKey("count")) {
			result.put("count", items.size());
		}
		result.put("items", items);
	}

	private ResponseEntity<String> makeAuthenticatedGet(String url, String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		if (username != null && !username.isEmpty()) {
			String auth = Base64.getEncoder()
				.encodeToString((username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
			headers.set("Authorization", "Basic " + auth);
		}
		HttpEntity<String> entity = new HttpEntity<>(headers);
		return restTemplate.exchange(url, HttpMethod.GET, entity, String.class);
	}

	private String getConfigString(Map<String, Object> config, String key, String defaultValue) {
		if (config == null) {
			return defaultValue;
		}
		Object val = config.get(key);
		return val != null ? String.valueOf(val) : defaultValue;
	}

	private String truncateString(String s, int maxLen) {
		if (s == null) {
			return "";
		}
		return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
	}

	/**
	 * Format file size in bytes to human-readable string (KB, MB, GB).
	 */
	private String formatFileSize(long bytes) {
		if (bytes < 1024) {
			return bytes + " B";
		}
		else if (bytes < 1024 * 1024) {
			return String.format("%.1f KB", bytes / 1024.0);
		}
		else if (bytes < 1024L * 1024 * 1024) {
			return String.format("%.1f MB", bytes / (1024.0 * 1024));
		}
		else {
			return String.format("%.1f GB", bytes / (1024.0 * 1024 * 1024));
		}
	}

	/**
	 * Extract the first group ID from a Groups API JSON response. Tries Alfresco
	 * format (list.entries[0].entry.id), then generic array format. Falls back to
	 * "GROUP_EVERYONE" if parsing fails.
	 */
	private String extractFirstGroupId(String groupsJson) {
		try {
			@SuppressWarnings("unchecked")
			Map<String, Object> root = objectMapper.readValue(groupsJson, Map.class);
			// Alfresco format: { list: { entries: [ { entry: { id: "GROUP_xxx" } } ] } }
			if (root.containsKey("list")) {
				@SuppressWarnings("unchecked")
				Map<String, Object> list = (Map<String, Object>) root.get("list");
				if (list != null && list.containsKey("entries")) {
					@SuppressWarnings("unchecked")
					java.util.List<Map<String, Object>> entries = (java.util.List<Map<String, Object>>) list
						.get("entries");
					if (entries != null && !entries.isEmpty()) {
						@SuppressWarnings("unchecked")
						Map<String, Object> entry = (Map<String, Object>) entries.get(0).get("entry");
						if (entry != null && entry.containsKey("id")) {
							return String.valueOf(entry.get("id"));
						}
					}
				}
			}
			// Generic array format: [ { id: "..." } ] or { groups: [ { id: "..." } ] }
			if (root.containsKey("groups")) {
				@SuppressWarnings("unchecked")
				java.util.List<Map<String, Object>> groups = (java.util.List<Map<String, Object>>) root
					.get("groups");
				if (groups != null && !groups.isEmpty() && groups.get(0).containsKey("id")) {
					return String.valueOf(groups.get(0).get("id"));
				}
			}
		}
		catch (Exception ignored) {
			// Fall through to default
		}
		return "GROUP_EVERYONE";
	}

	// ── Test Query ──────────────────────────────────────────────────────

	/**
	 * Test a CMIS query, REST API seed endpoint, or Group/User API by calling the source
	 * directly. Returns item count and top N sample results.
	 * @param config The connection configuration map
	 * @param testType "query" (CMIS query / REST seed), "groupApi"/"group_api", or "userApi"/"user_api"
	 * @param queryOverride Optional query override (e.g. custom CMIS query)
	 * @return Map with count, items[], status
	 */
	public Map<String, Object> testQuery(Map<String, Object> config, String connectorClass, String testType,
			String queryOverride) {
		Map<String, Object> result = new LinkedHashMap<>();
		try {
			// Normalize test type: accept both camelCase and snake_case
			String normalizedTestType = testType;
			if ("group_api".equals(testType)) {
				normalizedTestType = "groupApi";
			}
			else if ("user_api".equals(testType)) {
				normalizedTestType = "userApi";
			}
			else if ("cmis_query".equals(testType)) {
				normalizedTestType = "query";
			}
			else if ("rest_seed".equals(testType)) {
				normalizedTestType = "query";
			}

			if ("groupApi".equals(normalizedTestType)) {
				// Test only the Groups list endpoint
				return testGroupsListApi(config);
			}
			else if ("userApi".equals(normalizedTestType)) {
				// Test only the Group Members (user) endpoint
				return testGroupMembersApi(config);
			}

			// Sanitize config for URL construction
			Map<String, Object> safeConfig = sanitizeConfig(config);
			String protocol = getConfigString(safeConfig, "protocol",
					getConfigString(safeConfig, "PROTOCOL", "https"));
			String server = getConfigString(safeConfig, "server",
					getConfigString(safeConfig, "SERVER", ""));
			String port = getConfigString(safeConfig, "port",
					getConfigString(safeConfig, "PORT", "443"));
			String username = getConfigString(safeConfig, "username",
					getConfigString(safeConfig, "USERNAME", ""));
			String password = getConfigString(safeConfig, "password",
					getConfigString(safeConfig, "PASSWORD", ""));

			if (server.isEmpty()) {
				result.put("status", "FAIL");
				result.put("message", "Server hostname is not configured");
				return result;
			}

			String baseUrl = protocol + "://" + server + ":" + port;

			if (isCmisConnector(connectorClass)) {
				return testCmisQuery(config, baseUrl, username, password, queryOverride);
			}
			else if (isRestApiConnector(connectorClass)) {
				return testRestApiSeed(config, baseUrl, username, password);
			}
			else {
				result.put("status", "FAIL");
				result.put("message", "Test query is not supported for this connector type");
				return result;
			}
		}
		catch (Exception e) {
			result.put("status", "FAIL");
			result.put("message", "Error: " + e.getMessage());
			return result;
		}
	}

	private boolean isCmisConnector(String cls) {
		return cls != null && cls.contains("CmisRepositoryConnector");
	}

	private boolean isRestApiConnector(String cls) {
		return cls != null && cls.contains("RestApiRepositoryConnector");
	}

	/**
	 * Test a CMIS query by calling the CMIS query endpoint via HTTP.
	 * Uses the CMIS Browser binding query URL: POST /cmis/browser?cmisselector=query
	 */
	private Map<String, Object> testCmisQuery(Map<String, Object> config, String baseUrl,
			String username, String password, String queryOverride) {
		Map<String, Object> result = new LinkedHashMap<>();
		String path = getConfigString(config, "path", "");
		String binding = getConfigString(config, "binding", "atom");
		String repositoryId = getConfigString(config, "repositoryId", "-default-");
		String cmisQuery = queryOverride;
		if (cmisQuery == null || cmisQuery.isEmpty()) {
			cmisQuery = getConfigString(config, "cmisQuery", "SELECT * FROM cmis:document");
		}
		if (cmisQuery.isEmpty()) {
			cmisQuery = "SELECT * FROM cmis:document";
		}

		// Apply maxFileSize filter if configured (value is in bytes)
		long maxFileSize = 0;
		try {
			String maxFileSizeStr = getConfigString(config, "maxFileSize", "0");
			maxFileSize = Long.parseLong(maxFileSizeStr);
		}
		catch (NumberFormatException ignored) {
		}
		if (maxFileSize > 0) {
			String sizeFilter = "cmis:contentStreamLength <= " + maxFileSize;
			String queryUpper = cmisQuery.toUpperCase();
			if (queryUpper.contains(" WHERE ")) {
				// Append to existing WHERE clause
				cmisQuery = cmisQuery + " AND " + sizeFilter;
			}
			else {
				// Add WHERE clause
				cmisQuery = cmisQuery + " WHERE " + sizeFilter;
			}
			log.info("CMIS query with file size filter: {}", cmisQuery);
		}

		try {
			// Use CMIS browser binding query endpoint if possible
			// Build a query URL: {base}{path}?cmisselector=query&q={query}&maxItems=10
			String queryUrl;
			if ("browser".equals(binding)) {
				// Browser binding: POST to root folder URL with cmisselector=query
				queryUrl = baseUrl + path + "/" + urlEncode(repositoryId) + "/root?cmisselector=query"
						+ "&q=" + urlEncode(cmisQuery);
			}
			else {
				// AtomPub binding: use the CMIS query feed
				// For Alfresco: /alfresco/api/-default-/cmis/versions/1.1/atom/query (POST)
				queryUrl = baseUrl + path;
				if (!queryUrl.endsWith("/")) {
					queryUrl += "/";
				}
				// Try to build AtomPub query by POSTing CMIS query XML
				return testCmisQueryAtomPub(queryUrl, username, password, cmisQuery);
			}

			ResponseEntity<String> response = makeAuthenticatedGet(queryUrl, username, password);
			if (response.getStatusCode().is2xxSuccessful()) {
				String body = response.getBody();
				result.put("status", "PASS");
				parseAndAddItems(result, body, 10);
			}
			else {
				result.put("status", "FAIL");
				result.put("message", "CMIS query returned HTTP " + response.getStatusCode().value());
			}
		}
		catch (Exception e) {
			result.put("status", "FAIL");
			result.put("message", "CMIS query failed: " + e.getMessage());
		}
		return result;
	}

	/**
	 * Execute a CMIS query via AtomPub binding (POST with CMIS query XML).
	 * First fetches the service document to discover the query collection URL,
	 * then POSTs the CMIS query XML to that URL.
	 */
	private Map<String, Object> testCmisQueryAtomPub(String serviceDocUrl, String username, String password,
			String cmisQuery) {
		Map<String, Object> result = new LinkedHashMap<>();
		try {
			// Step 1: Fetch the service document to find the query collection URL
			String queryCollectionUrl = discoverQueryCollectionUrl(serviceDocUrl, username, password);
			if (queryCollectionUrl == null || queryCollectionUrl.isEmpty()) {
				result.put("status", "FAIL");
				result.put("message", "Could not discover CMIS query collection URL from service document at: "
						+ serviceDocUrl);
				return result;
			}

			log.info("Discovered CMIS query collection URL: {}", queryCollectionUrl);

			// Step 2: If the discovered URL uses http:// but we connected via https://,
			// rewrite to https:// (common with reverse proxies)
			if (serviceDocUrl.startsWith("https://") && queryCollectionUrl.startsWith("http://")) {
				queryCollectionUrl = "https://" + queryCollectionUrl.substring("http://".length());
				log.info("Rewrote query URL to HTTPS: {}", queryCollectionUrl);
			}

			// Step 3: Build CMIS Atom query XML
			String queryXml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
					+ "<cmis:query xmlns:cmis=\"http://docs.oasis-open.org/ns/cmis/core/200908/\">"
					+ "<cmis:statement>" + escapeXml(cmisQuery) + "</cmis:statement>"
					+ "<cmis:skipCount>0</cmis:skipCount>"
					+ "<cmis:searchAllVersions>false</cmis:searchAllVersions>"
					+ "</cmis:query>";

			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(new MediaType("application", "cmisquery+xml", StandardCharsets.UTF_8));
			if (username != null && !username.isEmpty()) {
				String auth = Base64.getEncoder()
						.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
				headers.set("Authorization", "Basic " + auth);
			}

			HttpEntity<String> requestEntity = new HttpEntity<>(queryXml, headers);

			// Step 4: POST to query collection URL
			ResponseEntity<String> response = restTemplate.exchange(
					queryCollectionUrl, HttpMethod.POST, requestEntity, String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				String body = response.getBody();
				result.put("status", "PASS");
				parseAtomFeedItems(result, body, 10);
			}
			else {
				result.put("status", "FAIL");
				result.put("message", "CMIS query returned HTTP " + response.getStatusCode().value());
			}
		}
		catch (Exception e) {
			result.put("status", "FAIL");
			result.put("message", "CMIS AtomPub query failed: " + e.getMessage());
		}
		return result;
	}

	/**
	 * Discover the CMIS query collection URL from the AtomPub service document.
	 * Parses the service doc XML to find the collection with collectionType "query".
	 */
	private String discoverQueryCollectionUrl(String serviceDocUrl, String username, String password) {
		try {
			// Remove trailing slash for clean service doc fetch
			String url = serviceDocUrl;
			if (url.endsWith("/")) {
				url = url.substring(0, url.length() - 1);
			}

			ResponseEntity<String> response = makeAuthenticatedGet(url, username, password);
			if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
				log.warn("Failed to fetch CMIS service document from: {}", url);
				return null;
			}

			String body = response.getBody();
			// Parse the service document to find the query collection URL
			// Look for: <app:collection href="..."><cmisra:collectionType>query</cmisra:collectionType>
			// Use simple string parsing to avoid adding XML parser dependency
			int queryTypeIdx = body.indexOf(">query</");
			if (queryTypeIdx < 0) {
				// Try alternate formats
				queryTypeIdx = body.indexOf(">query<");
			}
			if (queryTypeIdx < 0) {
				log.warn("Could not find query collection in CMIS service document");
				return null;
			}

			// Search backwards from the query type to find the collection href
			int collectionStart = body.lastIndexOf("<app:collection", queryTypeIdx);
			if (collectionStart < 0) {
				collectionStart = body.lastIndexOf("<collection", queryTypeIdx);
			}
			if (collectionStart < 0) {
				log.warn("Could not find collection element for query in service document");
				return null;
			}

			// Extract href attribute
			String segment = body.substring(collectionStart, queryTypeIdx);
			int hrefStart = segment.indexOf("href=\"");
			if (hrefStart < 0) {
				hrefStart = segment.indexOf("href='");
			}
			if (hrefStart < 0) {
				log.warn("Could not find href in query collection element");
				return null;
			}

			hrefStart += 6; // skip 'href="'
			char quoteChar = segment.charAt(hrefStart - 1);
			if (quoteChar != '"' && quoteChar != '\'') {
				quoteChar = '"';
			}
			int hrefEnd = segment.indexOf(quoteChar, hrefStart);
			if (hrefEnd < 0) {
				hrefEnd = segment.indexOf('"', hrefStart);
			}
			if (hrefEnd < 0) {
				log.warn("Could not parse href value from query collection element");
				return null;
			}

			return segment.substring(hrefStart, hrefEnd);
		}
		catch (Exception e) {
			log.error("Error discovering CMIS query collection URL: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Parse an Atom feed response (XML) from a CMIS query and extract document entries.
	 */
	private void parseAtomFeedItems(Map<String, Object> result, String body, int maxItems) {
		if (body == null || body.trim().isEmpty()) {
			result.put("count", 0);
			result.put("items", List.of());
			result.put("message", "Empty response");
			return;
		}

		try {
			// Parse Atom feed to extract entries
			List<Map<String, Object>> items = new ArrayList<>();

			// Extract total count from <cmisra:numItems> or count entries
			int totalCount = 0;
			int numItemsIdx = body.indexOf("numItems>");
			if (numItemsIdx >= 0) {
				int valStart = body.indexOf(">", numItemsIdx) + 1;
				int valEnd = body.indexOf("<", valStart);
				if (valEnd > valStart) {
					try {
						totalCount = Integer.parseInt(body.substring(valStart, valEnd).trim());
					}
					catch (NumberFormatException ignored) {
					}
				}
			}

			// Find all <atom:entry> or <entry> elements
			String entryTag = body.contains("<atom:entry") ? "<atom:entry" : "<entry";
			String entryCloseTag = body.contains("</atom:entry>") ? "</atom:entry>" : "</entry>";
			int pos = 0;
			while (pos < body.length() && items.size() < maxItems) {
				int entryStart = body.indexOf(entryTag, pos);
				if (entryStart < 0) break;
				int entryEnd = body.indexOf(entryCloseTag, entryStart);
				if (entryEnd < 0) break;
				entryEnd += entryCloseTag.length();

				String entry = body.substring(entryStart, entryEnd);
				Map<String, Object> item = new LinkedHashMap<>();

				// Extract common CMIS properties
				extractAtomProperty(entry, "cmis:name", item, "name");
				extractAtomProperty(entry, "cmis:objectId", item, "objectId");
				extractAtomProperty(entry, "cmis:objectTypeId", item, "type");
				extractAtomProperty(entry, "cmis:contentStreamMimeType", item, "mimeType");
				extractAtomProperty(entry, "cmis:contentStreamLength", item, "size");
				// Format size to human-readable KB/MB
				if (item.containsKey("size")) {
					try {
						long sizeBytes = Long.parseLong(String.valueOf(item.get("size")));
						item.put("size", formatFileSize(sizeBytes));
					}
					catch (NumberFormatException ignored) {
					}
				}
				extractAtomProperty(entry, "cmis:createdBy", item, "createdBy");
				extractAtomProperty(entry, "cmis:lastModifiedBy", item, "modifiedBy");
				extractAtomProperty(entry, "cmis:lastModificationDate", item, "modified");

				// Fallback: extract <atom:title>
				if (!item.containsKey("name")) {
					String title = extractXmlElement(entry, "title");
					if (title != null && !title.isEmpty()) {
						item.put("name", title);
					}
				}

				// Tag each item with whether its MIME type is Tika-processable
				String mime = item.containsKey("mimeType") ? String.valueOf(item.get("mimeType")) : "";
				String name = item.containsKey("name") ? String.valueOf(item.get("name")) : "";
				item.put("processable", KnowledgeSyncServiceImpl.isTikaProcessable(mime, name) ? "Yes" : "No");

				if (!item.isEmpty()) {
					items.add(item);
				}
				pos = entryEnd;
			}

			if (totalCount == 0) {
				totalCount = items.size();
			}
			result.put("count", totalCount);
			result.put("items", items);

			// Summarize processable vs non-processable
			long processableCount = items.stream()
					.filter(i -> "Yes".equals(i.get("processable"))).count();
			long nonProcessableCount = items.size() - processableCount;
			result.put("processableCount", processableCount);
			result.put("nonProcessableCount", nonProcessableCount);

			if (!items.isEmpty()) {
				String msg = "Found " + totalCount + " document(s)";
				if (nonProcessableCount > 0) {
					msg += " — " + nonProcessableCount + " not processable for full-text search (images, videos, etc.)";
				}
				result.put("message", msg);
			}
			else {
				result.put("message", "No documents found (query returned empty result set)");
				result.put("raw_preview", truncateString(body, 2000));
			}
		}
		catch (Exception e) {
			result.put("count", 0);
			result.put("items", List.of());
			result.put("raw_preview", truncateString(body, 2000));
			result.put("message", "Response received but could not parse Atom feed: " + e.getMessage());
		}
	}

	/**
	 * Extract a CMIS property value from an Atom entry XML fragment.
	 */
	private void extractAtomProperty(String entry, String propertyDefId, Map<String, Object> item,
			String outputKey) {
		// Look for propertyDefinitionId="cmis:name" then extract <cmis:value>
		String searchStr = "propertyDefinitionId=\"" + propertyDefId + "\"";
		int propIdx = entry.indexOf(searchStr);
		if (propIdx < 0) return;

		// Find <cmis:value> after this property
		int valueStart = entry.indexOf("<cmis:value>", propIdx);
		if (valueStart < 0) {
			valueStart = entry.indexOf(":value>", propIdx);
		}
		if (valueStart < 0) return;
		valueStart = entry.indexOf(">", valueStart) + 1;

		int valueEnd = entry.indexOf("</", valueStart);
		if (valueEnd < 0) return;

		String value = entry.substring(valueStart, valueEnd).trim();
		if (!value.isEmpty()) {
			item.put(outputKey, value);
		}
	}

	/**
	 * Extract a simple XML element value (handles namespace prefixes).
	 */
	private String extractXmlElement(String xml, String localName) {
		// Try with namespace prefix (atom:title, cmis:value, etc.)
		for (String prefix : new String[]{"atom:", "cmis:", "cmisra:", ""}) {
			String open = "<" + prefix + localName;
			int start = xml.indexOf(open);
			if (start >= 0) {
				int gt = xml.indexOf(">", start);
				if (gt < 0) continue;
				int end = xml.indexOf("</", gt);
				if (end < 0) continue;
				return xml.substring(gt + 1, end).trim();
			}
		}
		return null;
	}

	/**
	 * Test a REST API seed endpoint by calling it and parsing the response.
	 */
	private Map<String, Object> testRestApiSeed(Map<String, Object> config, String baseUrl,
			String username, String password) {
		Map<String, Object> result = new LinkedHashMap<>();
		String basePath = getConfigString(config, "BASEPATH", "");
		String seedEndpoint = getConfigString(config, "SEEDENDPOINT", "");
		String authType = getConfigString(config, "AUTHTYPE", "none");
		String apiKey = getConfigString(config, "APIKEY", "");
		String apiKeyHeader = getConfigString(config, "APIKEYHEADER", "Authorization");
		String pageSize = getConfigString(config, "PAGESIZE", "10");
		String limitParam = getConfigString(config, "LIMITPARAM", "limit");
		String itemsPath = getConfigString(config, "ITEMSPATH", "$.results");

		if (seedEndpoint.isEmpty()) {
			result.put("status", "FAIL");
			result.put("message", "Seed endpoint is not configured");
			return result;
		}

		String fullUrl = baseUrl + basePath + seedEndpoint;
		// Add page size limit
		String separator = fullUrl.contains("?") ? "&" : "?";
		int limit = Math.min(Integer.parseInt(pageSize.isEmpty() ? "10" : pageSize), 10);
		fullUrl = fullUrl + separator + limitParam + "=" + limit;

		try {
			HttpHeaders headers = new HttpHeaders();
			// Add auth based on type
			switch (authType.toLowerCase()) {
				case "basic":
					if (!username.isEmpty()) {
						String auth = Base64.getEncoder()
								.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
						headers.set("Authorization", "Basic " + auth);
					}
					break;
				case "bearer":
					if (!apiKey.isEmpty()) {
						headers.set("Authorization", "Bearer " + apiKey);
					}
					break;
				case "apikey":
					if (!apiKey.isEmpty()) {
						headers.set(apiKeyHeader.isEmpty() ? "Authorization" : apiKeyHeader, apiKey);
					}
					break;
				case "oauth2":
					if (!apiKey.isEmpty()) {
						headers.set("Authorization", "Bearer " + apiKey);
					}
					break;
				default:
					break;
			}

			// Add custom headers
			String customHeaders = getConfigString(config, "CUSTOMHEADERS", "");
			if (!customHeaders.isEmpty()) {
				for (String line : customHeaders.split("\n")) {
					String[] parts = line.split("=", 2);
					if (parts.length == 2) {
						headers.set(parts[0].trim(), parts[1].trim());
					}
				}
			}

			HttpEntity<String> entity = new HttpEntity<>(headers);
			ResponseEntity<String> response = restTemplate.exchange(fullUrl, HttpMethod.GET, entity, String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				String body = response.getBody();
				result.put("status", "PASS");
				result.put("url", fullUrl);
				parseAndAddItems(result, body, 10);
			}
			else {
				result.put("status", "FAIL");
				result.put("message", "REST API returned HTTP " + response.getStatusCode().value());
			}
		}
		catch (Exception e) {
			result.put("status", "FAIL");
			result.put("message", "REST API call failed: " + e.getMessage() + "\nURL: " + fullUrl);
		}
		return result;
	}

	/**
	 * Parse a JSON or XML response body and extract items for preview.
	 */
	private void parseAndAddItems(Map<String, Object> result, String body, int maxItems) {
		if (body == null || body.trim().isEmpty()) {
			result.put("count", 0);
			result.put("items", List.of());
			result.put("message", "Empty response");
			return;
		}

		try {
			// Try JSON first
			JsonNode root = objectMapper.readTree(body);
			List<Map<String, Object>> items = new ArrayList<>();

			if (root.isArray()) {
				int count = root.size();
				result.put("count", count);
				for (int i = 0; i < Math.min(count, maxItems); i++) {
					items.add(flattenNode(root.get(i)));
				}
			}
			else if (root.isObject()) {
				// Try to find an array within common wrapper fields
				JsonNode arrayNode = findArrayInObject(root);
				if (arrayNode != null) {
					int count = arrayNode.size();
					// Check for total count field
					JsonNode totalNode = root.get("total");
					if (totalNode == null) {
						totalNode = root.get("totalItems");
					}
					if (totalNode == null) {
						totalNode = root.get("numItems");
					}
					if (totalNode == null) {
						totalNode = root.path("paging").get("totalItems");
					}
					if (totalNode == null) {
						totalNode = root.path("list").path("pagination").get("totalItems");
					}
					result.put("count", totalNode != null ? totalNode.asInt(count) : count);
					for (int i = 0; i < Math.min(count, maxItems); i++) {
						items.add(flattenNode(arrayNode.get(i)));
					}
				}
				else {
					// Single object
					result.put("count", 1);
					items.add(flattenNode(root));
				}
			}
			result.put("items", items);
		}
		catch (Exception e) {
			// Not JSON — try to parse as XML feed (Atom)
			result.put("count", 0);
			result.put("items", List.of());
			result.put("raw_preview", truncateString(body, 2000));
			if (!result.containsKey("message")) {
				result.put("message", "Response received (could not parse as JSON)");
			}
		}
	}

	/**
	 * Find the first array field inside a JSON object (common in API responses).
	 */
	private JsonNode findArrayInObject(JsonNode obj) {
		// Check common wrapper field names
		String[] commonFields = { "results", "entries", "items", "data", "records", "list",
				"values", "objects", "documents", "content", "elements", "rows", "hits" };
		for (String field : commonFields) {
			JsonNode n = obj.get(field);
			if (n != null && n.isArray()) {
				return n;
			}
			// Check nested: e.g. list.entries
			if (n != null && n.isObject()) {
				for (String subField : commonFields) {
					JsonNode sub = n.get(subField);
					if (sub != null && sub.isArray()) {
						return sub;
					}
				}
			}
		}
		// Fallback: find first array field
		Iterator<Map.Entry<String, JsonNode>> fields = obj.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			if (entry.getValue().isArray() && entry.getValue().size() > 0) {
				return entry.getValue();
			}
		}
		return null;
	}

	/**
	 * Flatten a JSON node into a simple key-value map for display.
	 */
	private Map<String, Object> flattenNode(JsonNode node) {
		Map<String, Object> map = new LinkedHashMap<>();
		if (node == null) {
			return map;
		}
		if (node.isObject()) {
			Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
			int fieldCount = 0;
			while (fields.hasNext() && fieldCount < 8) {
				Map.Entry<String, JsonNode> entry = fields.next();
				JsonNode value = entry.getValue();
				if (value.isTextual()) {
					map.put(entry.getKey(), truncateString(value.asText(), 200));
				}
				else if (value.isNumber()) {
					map.put(entry.getKey(), value.numberValue());
				}
				else if (value.isBoolean()) {
					map.put(entry.getKey(), value.booleanValue());
				}
				else if (value.isNull()) {
					map.put(entry.getKey(), null);
				}
				else {
					map.put(entry.getKey(), truncateString(value.toString(), 200));
				}
				fieldCount++;
			}
		}
		else {
			map.put("value", truncateString(node.asText(), 200));
		}
		return map;
	}

	private String escapeXml(String s) {
		if (s == null) {
			return "";
		}
		return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
				.replace("\"", "&quot;").replace("'", "&apos;");
	}

	/**
	 * Delete a repository connection from ManifoldCF.
	 */
	public void deleteRepositoryConnection(String connectionName) {
		try {
			String url = mcfApiUrl + "/json/repositoryconnections/" + urlEncode(connectionName);
			restTemplate.delete(url);
			log.info("Deleted MCF repository connection '{}'", connectionName);
		}
		catch (Exception e) {
			log.warn("Failed to delete repository connection '{}': {}", connectionName, e.getMessage());
		}
	}

	/**
	 * Get details of a repository connection.
	 */
	public Map<String, Object> getRepositoryConnection(String connectionName) {
		try {
			String url = mcfApiUrl + "/json/repositoryconnections/" + urlEncode(connectionName);
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			return objectMapper.readValue(response.getBody(), new TypeReference<Map<String, Object>>() {
			});
		}
		catch (Exception e) {
			log.error("Failed to get connection '{}': {}", connectionName, e.getMessage());
			return Collections.emptyMap();
		}
	}

	// ── Output Connections ───────────────────────────────────────────────

	/**
	 * Create or update an ElasticSearch/OpenSearch output connection in ManifoldCF.
	 * This allows per-knowledge-base index naming: MCF writes directly to the
	 * enforced index name instead of the shared default output connection.
	 *
	 * @param name unique output connection name (e.g., "KB_2023070559378198529")
	 * @param description human-readable description
	 * @param indexName OpenSearch index to write documents to
	 * @return the output connection name
	 */
	public String createOutputConnection(String name, String description, String indexName) {
		try {
			String opensearchUrl = mcfOpensearchUrl;
			if (!opensearchUrl.endsWith("/")) {
				opensearchUrl += "/";
			}

			ObjectNode payload = objectMapper.createObjectNode();
			ObjectNode connNode = objectMapper.createObjectNode();
			connNode.put("name", name);
			connNode.put("class_name",
					"org.apache.manifoldcf.agents.output.elasticsearch.ElasticSearchConnector");
			connNode.put("description", description);
			connNode.put("max_connections", "10");

			ObjectNode configNode = objectMapper.createObjectNode();
			ArrayNode params = objectMapper.createArrayNode();

			ObjectNode serverParam = objectMapper.createObjectNode();
			serverParam.put("_attribute_name", "SERVERLOCATION");
			serverParam.put("_value_", opensearchUrl);
			params.add(serverParam);

			ObjectNode indexParam = objectMapper.createObjectNode();
			indexParam.put("_attribute_name", "INDEXNAME");
			indexParam.put("_value_", indexName);
			params.add(indexParam);

			ObjectNode typeParam = objectMapper.createObjectNode();
			typeParam.put("_attribute_name", "INDEXTYPE");
			typeParam.put("_value_", "_doc");
			params.add(typeParam);

			configNode.set("_PARAMETER_", params);
			connNode.set("configuration", configNode);
			payload.set("outputconnection", connNode);

			String url = mcfApiUrl + "/json/outputconnections/" + urlEncode(name);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(payload), headers);

			ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.PUT, request, String.class);
			log.info("Created MCF output connection '{}' -> index '{}': {}", name, indexName,
					response.getStatusCode());
			return name;
		}
		catch (Exception e) {
			log.error("Failed to create output connection '{}': {}", name, e.getMessage(), e);
			throw new RuntimeException("Failed to create MCF output connection: " + e.getMessage(), e);
		}
	}

	/**
	 * Delete an output connection from ManifoldCF.
	 */
	public void deleteOutputConnection(String connectionName) {
		try {
			String url = mcfApiUrl + "/json/outputconnections/" + urlEncode(connectionName);
			restTemplate.delete(url);
			log.info("Deleted MCF output connection '{}'", connectionName);
		}
		catch (Exception e) {
			log.warn("Failed to delete output connection '{}': {}", connectionName, e.getMessage());
		}
	}

	// ── Tika Transformation ──────────────────────────────────────────────

	private static final String TIKA_CONNECTION_NAME = "Tika";

	/**
	 * Ensure the Tika transformation connection exists in ManifoldCF.
	 * This connection uses the embedded Tika extractor (no external Tika server needed)
	 * and is shared across all crawl jobs. When added to a pipeline, MCF will extract
	 * text from binary documents (PDF, DOCX, PPTX, etc.) before indexing to OpenSearch.
	 */
	public void ensureTikaTransformationConnection() {
		try {
			String url = mcfApiUrl + "/json/transformationconnections/" + urlEncode(TIKA_CONNECTION_NAME);
			ResponseEntity<String> existCheck = restTemplate.getForEntity(url, String.class);
			if (existCheck.getBody() != null && existCheck.getBody().contains("\"isnew\"")) {
				log.debug("Tika transformation connection '{}' already exists", TIKA_CONNECTION_NAME);
				return;
			}
		}
		catch (Exception e) {
			// Connection doesn't exist yet — fall through to create it
			log.debug("Tika connection check failed (will create): {}", e.getMessage());
		}

		try {
			ObjectNode payload = objectMapper.createObjectNode();
			ObjectNode connNode = objectMapper.createObjectNode();
			connNode.put("name", TIKA_CONNECTION_NAME);
			connNode.put("class_name",
					"org.apache.manifoldcf.agents.transformation.tika.TikaExtractor");
			connNode.put("description", "Tika content extractor (embedded)");
			connNode.put("max_connections", "10");
			payload.set("transformationconnection", connNode);

			String url = mcfApiUrl + "/json/transformationconnections/" + urlEncode(TIKA_CONNECTION_NAME);
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> request = new HttpEntity<>(
					objectMapper.writeValueAsString(payload), headers);

			ResponseEntity<String> response = restTemplate.exchange(
					url, HttpMethod.PUT, request, String.class);
			log.info("Created MCF Tika transformation connection '{}': {}",
					TIKA_CONNECTION_NAME, response.getStatusCode());
		}
		catch (Exception e) {
			log.error("Failed to create Tika transformation connection: {}", e.getMessage(), e);
			throw new RuntimeException(
					"Failed to create Tika transformation connection: " + e.getMessage(), e);
		}
	}

	/**
	 * Get the name of the shared Tika transformation connection.
	 */
	public String getTikaConnectionName() {
		return TIKA_CONNECTION_NAME;
	}

	// ── Jobs ─────────────────────────────────────────────────────────────

	/**
	 * Create a crawl job in ManifoldCF linking a repository connection to the output.
	 * Uses RESTAPIQUERY as the default query attribute (for REST-based connectors).
	 * No transformation stage — raw content goes directly to output.
	 */
	public String createCrawlJob(String jobDescription, String repositoryConnectionName, String outputConnectionName,
			String query) {
		return createCrawlJob(jobDescription, repositoryConnectionName, outputConnectionName, query, "RESTAPIQUERY",
				null);
	}

	/**
	 * Create a crawl job with a specific query attribute name but no transformation.
	 */
	public String createCrawlJob(String jobDescription, String repositoryConnectionName, String outputConnectionName,
			String query, String queryAttributeName) {
		return createCrawlJob(jobDescription, repositoryConnectionName, outputConnectionName, query,
				queryAttributeName, null);
	}

	/**
	 * Create a crawl job with an optional transformation pipeline stage.
	 * <p>When {@code transformationConnectionName} is provided, the pipeline becomes:
	 * {@code Repository → [stage 0: Transformation] → [stage 1: Output]}</p>
	 * <p>When null/empty, the pipeline is:
	 * {@code Repository → [stage 0: Output]}</p>
	 *
	 * @param transformationConnectionName name of the MCF transformation connection
	 *        (e.g., "Tika"), or null for direct output
	 */
	public String createCrawlJob(String jobDescription, String repositoryConnectionName, String outputConnectionName,
			String query, String queryAttributeName, String transformationConnectionName) {
		try {
			if (outputConnectionName == null || outputConnectionName.isEmpty()) {
				outputConnectionName = defaultOutputConnection;
			}

			ObjectNode payload = objectMapper.createObjectNode();
			ArrayNode children = objectMapper.createArrayNode();

			addChild(children, "description", jobDescription);
			addChild(children, "repository_connection", repositoryConnectionName);
			addChild(children, "start_mode", "manual");
			addChild(children, "run_mode", "scan once");
			addChild(children, "hopcount_mode", "accurate");
			addChild(children, "priority", "5");
			addChild(children, "recrawl_interval", "86400000"); // 24h

			boolean hasTransformation = transformationConnectionName != null
					&& !transformationConnectionName.isEmpty();

			if (hasTransformation) {
				// Stage 0: Transformation (NOT output) — e.g., Tika text extraction
				ObjectNode tikaStage = objectMapper.createObjectNode();
				tikaStage.put("_type_", "pipelinestage");
				ArrayNode tikaChildren = objectMapper.createArrayNode();
				addChild(tikaChildren, "stage_id", "0");
				addChild(tikaChildren, "stage_isoutput", "false");
				addChild(tikaChildren, "stage_connectionname", transformationConnectionName);
				tikaStage.set("_children_", tikaChildren);
				children.add(tikaStage);

				// Stage 1: Output (depends on stage 0)
				ObjectNode outputStage = objectMapper.createObjectNode();
				outputStage.put("_type_", "pipelinestage");
				ArrayNode outputChildren = objectMapper.createArrayNode();
				addChild(outputChildren, "stage_id", "1");
				addChild(outputChildren, "stage_isoutput", "true");
				addChild(outputChildren, "stage_connectionname", outputConnectionName);
				addChild(outputChildren, "stage_prerequisite", "0");
				outputStage.set("_children_", outputChildren);
				children.add(outputStage);
			}
			else {
				// Single stage: direct output (no transformation)
				ObjectNode pipelineChild = objectMapper.createObjectNode();
				pipelineChild.put("_type_", "pipelinestage");
				ArrayNode pipelineChildren = objectMapper.createArrayNode();
				addChild(pipelineChildren, "stage_id", "0");
				addChild(pipelineChildren, "stage_isoutput", "true");
				addChild(pipelineChildren, "stage_connectionname", outputConnectionName);
				pipelineChild.set("_children_", pipelineChildren);
				children.add(pipelineChild);
			}

			// Document specification (query)
			if (query != null && !query.isEmpty()) {
				ObjectNode specChild = objectMapper.createObjectNode();
				specChild.put("_type_", "document_specification");
				ArrayNode specChildren = objectMapper.createArrayNode();
				ObjectNode queryNode = objectMapper.createObjectNode();
				queryNode.put("_type_", "startpoint");
				queryNode.put("_attribute_" + queryAttributeName, query);
				queryNode.put("_value_", "");
				specChildren.add(queryNode);
				specChild.set("_children_", specChildren);
				children.add(specChild);
			}

			payload.set("_children_", children);
			ObjectNode wrapper = objectMapper.createObjectNode();
			wrapper.set("job", payload);

			String url = mcfApiUrl + "/json/jobs";
			HttpHeaders headers = new HttpHeaders();
			headers.setContentType(MediaType.APPLICATION_JSON);
			HttpEntity<String> request = new HttpEntity<>(objectMapper.writeValueAsString(wrapper), headers);

			ResponseEntity<String> response = restTemplate.postForEntity(url, request, String.class);
			JsonNode responseRoot = objectMapper.readTree(response.getBody());
			String jobId = responseRoot.path("job_id").asText();
			log.info("Created MCF crawl job '{}' (id={}, transform={})", jobDescription, jobId,
					hasTransformation ? transformationConnectionName : "none");
			return jobId;
		}
		catch (Exception e) {
			log.error("Failed to create crawl job: {}", e.getMessage(), e);
			throw new RuntimeException("Failed to create crawl job: " + e.getMessage(), e);
		}
	}

	/**
	 * Start a crawl job.
	 */
	public void startJob(String jobId) {
		try {
			String url = mcfApiUrl + "/json/start/" + jobId;
			restTemplate.put(url, null);
			log.info("Started MCF job {}", jobId);
		}
		catch (Exception e) {
			log.error("Failed to start job {}: {}", jobId, e.getMessage());
			throw new RuntimeException("Failed to start job: " + e.getMessage(), e);
		}
	}

	/**
	 * Get job status including document progress.
	 */
	public Map<String, String> getJobStatus(String jobId) {
		try {
			String url = mcfApiUrl + "/json/jobstatuses/" + jobId;
			ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);
			JsonNode root = objectMapper.readTree(response.getBody());
			JsonNode children = root.path("jobstatus").path("_children_");

			Map<String, String> status = new HashMap<>();
			if (children.isArray()) {
				for (JsonNode child : children) {
					status.put(child.get("_type_").asText(), child.get("_value_").asText());
				}
			}
			return status;
		}
		catch (Exception e) {
			log.error("Failed to get job status for {}: {}", jobId, e.getMessage());
			Map<String, String> error = new HashMap<>();
			error.put("status", "error");
			error.put("error", e.getMessage());
			return error;
		}
	}

	/**
	 * Delete a job.
	 */
	public void deleteJob(String jobId) {
		try {
			String url = mcfApiUrl + "/json/jobs/" + jobId;
			restTemplate.delete(url);
			log.info("Deleted MCF job {}", jobId);
		}
		catch (Exception e) {
			log.warn("Failed to delete job {}: {}", jobId, e.getMessage());
		}
	}

	/**
	 * Abort a running job.
	 */
	public void abortJob(String jobId) {
		try {
			String url = mcfApiUrl + "/json/abort/" + jobId;
			restTemplate.put(url, null);
			log.info("Aborted MCF job {}", jobId);
		}
		catch (Exception e) {
			log.warn("Failed to abort job {}: {}", jobId, e.getMessage());
		}
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private void addChild(ArrayNode children, String type, String value) {
		ObjectNode child = objectMapper.createObjectNode();
		child.put("_type_", type);
		child.put("_value_", value);
		children.add(child);
	}

	private String urlEncode(String value) {
		try {
			return java.net.URLEncoder.encode(value, "UTF-8");
		}
		catch (Exception e) {
			return value;
		}
	}

}
