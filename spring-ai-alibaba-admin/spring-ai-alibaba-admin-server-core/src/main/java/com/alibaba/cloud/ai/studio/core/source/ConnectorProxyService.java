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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Proxies crawl/test operations to an external connector microservice.
 *
 * <p>When a source system's connector class matches a registered connector
 * (via {@link ConnectorRegistryService}), this service is used instead of
 * {@link ManifoldCFBridgeService}. It calls the connector's REST API directly.</p>
 *
 * <p>The connector REST API contract:
 * <ul>
 *   <li>POST /api/v1/test-connection — test CMIS connection</li>
 *   <li>POST /api/v1/test-query — test a CMIS query</li>
 *   <li>POST /api/v1/crawl — start a crawl job</li>
 *   <li>GET  /api/v1/crawl/{jobId}/status — get job status</li>
 *   <li>POST /api/v1/crawl/{jobId}/abort — abort a job</li>
 *   <li>GET  /api/v1/jobs/{jobId}/status — MCF-compatible status format</li>
 * </ul>
 * </p>
 */
@Slf4j
@Service
public class ConnectorProxyService {

	private final HttpClient httpClient;

	private final ObjectMapper objectMapper;

	public ConnectorProxyService() {
		this.httpClient = HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.followRedirects(HttpClient.Redirect.NORMAL)
			.build();
		this.objectMapper = new ObjectMapper();
	}

	/**
	 * Test connection through the connector service.
	 *
	 * @param baseUrl connector base URL (e.g., http://cmis-connector:8390)
	 * @param config connection configuration to test
	 * @return test result with "result" key
	 */
	public Map<String, String> testConnection(String baseUrl, Map<String, Object> config) {
		try {
			String body = objectMapper.writeValueAsString(config);
			String url = baseUrl + "/api/v1/test-connection";

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(30))
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return parseStringMap(response.body());
		}
		catch (Exception e) {
			log.error("Failed to test connection via connector at {}: {}", baseUrl, e.getMessage());
			Map<String, String> error = new LinkedHashMap<>();
			error.put("result", "Connection failed: " + e.getMessage());
			return error;
		}
	}

	/**
	 * Test a query through the connector service.
	 *
	 * @param baseUrl connector base URL
	 * @param config connection configuration
	 * @param testType the type of test (cmis_query, group_api, user_api, etc.)
	 * @param query CMIS query to test
	 * @return query results preview normalized to frontend format
	 */
	public Map<String, Object> testQuery(String baseUrl, Map<String, Object> config, String testType, String query) {
		try {
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("connection_config", config);
			if (testType != null) {
				payload.put("test_type", testType);
			}
			if (query != null) {
				payload.put("query", query);
			}

			String body = objectMapper.writeValueAsString(payload);
			String url = baseUrl + "/api/v1/test-query";

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(60))
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			Map<String, Object> raw = parseObjectMap(response.body());
			return normalizeTestQueryResponse(raw);
		}
		catch (Exception e) {
			log.error("Failed to test query via connector at {}: {}", baseUrl, e.getMessage());
			Map<String, Object> error = new LinkedHashMap<>();
			error.put("status", "error");
			error.put("message", e.getMessage());
			return error;
		}
	}

	/**
	 * Normalize connector test-query response to the format expected by the frontend.
	 * Connector returns: {status: "OK", total_results: N, preview: [...], query: "..."}
	 * Frontend expects: {status: "ok"|"PASS", count: N, items: [...], message: "..."}
	 */
	private Map<String, Object> normalizeTestQueryResponse(Map<String, Object> raw) {
		Map<String, Object> result = new LinkedHashMap<>();

		// Normalize status: connector "OK" → frontend "PASS", "ERROR" → "error"
		String rawStatus = String.valueOf(raw.getOrDefault("status", "error"));
		if ("OK".equalsIgnoreCase(rawStatus)) {
			result.put("status", "PASS");
		}
		else {
			result.put("status", rawStatus.toLowerCase());
		}

		// Map total_results → count
		Object totalResults = raw.get("total_results");
		if (totalResults instanceof Number n) {
			result.put("count", n.intValue());
		}
		else if (totalResults != null) {
			try {
				result.put("count", Integer.parseInt(totalResults.toString()));
			}
			catch (NumberFormatException e) {
				result.put("count", 0);
			}
		}
		else {
			result.put("count", 0);
		}

		// Map preview → items
		Object preview = raw.get("preview");
		if (preview != null) {
			result.put("items", preview);
		}
		else {
			// Connector may already use "items"
			result.put("items", raw.getOrDefault("items", java.util.List.of()));
		}

		// Map error → message
		Object error = raw.get("error");
		if (error != null) {
			result.put("message", error.toString());
		}
		else if (raw.containsKey("message")) {
			result.put("message", raw.get("message"));
		}

		return result;
	}

	/**
	 * Start a crawl job through the connector service.
	 *
	 * @param baseUrl connector base URL
	 * @param config connection configuration
	 * @param indexName target OpenSearch index name
	 * @param query CMIS query for crawling
	 * @param lastSyncTime timestamp of last successful sync (may be null for full crawl)
	 * @return job ID from the connector
	 */
	public String startCrawl(String baseUrl, Map<String, Object> config, String indexName,
			String query, Date lastSyncTime) {
		try {
			// Use snake_case keys to match connector's @JsonProperty annotations
			Map<String, Object> payload = new LinkedHashMap<>();
			payload.put("cmis_query", query);
			payload.put("index_name", indexName);
			payload.put("connection_config", config);

			if (lastSyncTime != null) {
				String isoDate = DateTimeFormatter.ISO_INSTANT
						.format(lastSyncTime.toInstant());
				payload.put("last_sync_time", isoDate);
				log.info("Incremental crawl: passing last_sync_time={}", isoDate);
			}
			else {
				log.info("Full crawl: no lastSyncTime provided");
			}

			String body = objectMapper.writeValueAsString(payload);
			String url = baseUrl + "/api/v1/crawl";

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.timeout(Duration.ofSeconds(30))
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			Map<String, Object> result = parseObjectMap(response.body());
			return (String) result.get("job_id");
		}
		catch (Exception e) {
			log.error("Failed to start crawl via connector at {}: {}", baseUrl, e.getMessage());
			throw new RuntimeException("Failed to start crawl: " + e.getMessage(), e);
		}
	}

	/**
	 * Get job status from the connector service (MCF-compatible format).
	 *
	 * @param baseUrl connector base URL
	 * @param jobId crawl job ID
	 * @return status map with MCF-compatible keys
	 */
	public Map<String, String> getJobStatus(String baseUrl, String jobId) {
		try {
			String url = baseUrl + "/api/v1/jobs/" + jobId + "/status";

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.GET()
				.timeout(Duration.ofSeconds(10))
				.build();

			HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
			return parseStringMap(response.body());
		}
		catch (Exception e) {
			log.warn("Failed to get job status from connector at {}: {}", baseUrl, e.getMessage());
			Map<String, String> error = new LinkedHashMap<>();
			error.put("status", "unknown");
			error.put("error", e.getMessage());
			return error;
		}
	}

	/**
	 * Abort a crawl job through the connector service.
	 *
	 * @param baseUrl connector base URL
	 * @param jobId crawl job ID
	 */
	public void abortJob(String baseUrl, String jobId) {
		try {
			String url = baseUrl + "/api/v1/crawl/" + jobId + "/abort";

			HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.noBody())
				.timeout(Duration.ofSeconds(10))
				.build();

			httpClient.send(request, HttpResponse.BodyHandlers.ofString());
		}
		catch (Exception e) {
			log.warn("Failed to abort job via connector at {}: {}", baseUrl, e.getMessage());
		}
	}

	/**
	 * Delete a job through the connector service (no-op for connector services
	 * since they manage jobs internally, but provided for API compatibility).
	 */
	public void deleteJob(String baseUrl, String jobId) {
		// Connector services auto-clean jobs; this is a no-op
		log.debug("deleteJob called for connector at {} (no-op)", baseUrl);
	}

	// ── Helpers ──────────────────────────────────────────────────────────

	private Map<String, String> parseStringMap(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, String>>() {
			});
		}
		catch (Exception e) {
			Map<String, String> fallback = new LinkedHashMap<>();
			fallback.put("raw", json);
			return fallback;
		}
	}

	private Map<String, Object> parseObjectMap(String json) {
		try {
			return objectMapper.readValue(json, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (Exception e) {
			Map<String, Object> fallback = new LinkedHashMap<>();
			fallback.put("raw", json);
			return fallback;
		}
	}

}
