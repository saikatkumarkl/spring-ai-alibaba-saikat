/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.examples.chatbot;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Service for communicating with the Admin API backend.
 */
@Slf4j
@Service
public class AdminApiService {

	private final WebClient webClient;
	private final String baseUrl;

	public AdminApiService(@Value("${admin.api.base-url:http://localhost:8080}") String baseUrl) {
		this.baseUrl = baseUrl;
		this.webClient = WebClient.builder()
			.baseUrl(baseUrl)
			.defaultHeader("Content-Type", "application/json")
			// Increase in-memory buffer limit for document downloads (default 256KB is too small)
			.codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(50 * 1024 * 1024))
			.build();
	}

	/**
	 * Login to Admin backend.
	 * The Admin API returns: {"code": 200, "message": "success", "data": {...}}
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> login(String email, String password) {
		log.info("Attempting login for user: {}", email);
		return webClient.post()
			.uri("/console/v1/chatbot/login")
			.bodyValue(Map.of("email", email, "password", password))
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> {
				Integer code = (Integer) response.get("code");
				if (code != null && code == 200 && response.get("data") != null) {
					return (Map<String, Object>) response.get("data");
				}
				String message = (String) response.get("message");
				throw new RuntimeException("Login failed: " + (message != null ? message : "Unknown error"));
			})
			.doOnSuccess(data -> log.info("Login successful for: {}", email))
			.doOnError(error -> log.error("Login failed for: {}", email, error));
	}

	/**
	 * Send chat message via the chatbot proxy endpoint (no auth required)
	 */
	public Flux<String> chatStream(String appId, String token, String message, String conversationId) {
		log.info("Sending chat message to app: {} with conversationId: {}", appId, conversationId);
		
		Map<String, Object> requestBody = Map.of(
			"app_id", appId,
			"messages", List.of(Map.of("role", "user", "content", message)),
			"stream", true,
			"conversation_id", conversationId != null ? conversationId : ""
		);

		return webClient.post()
			.uri("/console/v1/chatbot/chat/completions")
			.contentType(MediaType.APPLICATION_JSON)
			.bodyValue(requestBody)
			.retrieve()
			.bodyToFlux(String.class)
			.timeout(Duration.ofMinutes(10))
			.doOnError(error -> log.error("Chat stream error: {}", error.getMessage()));
	}

	/**
	 * Save a chat message to history via Admin backend.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Void> saveChatMessage(String email, String appId, String conversationId, String role, String content) {
		Map<String, String> requestBody = Map.of(
			"appId", appId,
			"conversationId", conversationId,
			"role", role,
			"content", content
		);

		return webClient.post()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/chat-history")
				.queryParam("email", email)
				.build())
			.bodyValue(requestBody)
			.retrieve()
			.bodyToMono(Void.class)
			.doOnError(error -> log.warn("Failed to save chat message", error))
			.onErrorResume(error -> Mono.empty());
	}

	/**
	 * Get chat history for a user and app.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> getChatHistory(String email, String appId, String conversationId) {
		return webClient.get()
			.uri(uriBuilder -> {
				uriBuilder.path("/console/v1/chatbot/chat-history")
					.queryParam("email", email)
					.queryParam("appId", appId);
				if (conversationId != null && !conversationId.isEmpty()) {
					uriBuilder.queryParam("conversationId", conversationId);
				}
				return uriBuilder.build();
			})
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> {
				Map<String, Object> result = new java.util.HashMap<>();
				Integer code = (Integer) response.get("code");
				if (code != null && code == 200 && response.get("data") != null) {
					result.put("data", response.get("data"));
				} else {
					result.put("data", List.of());
				}
				return result;
			})
			.doOnError(error -> log.error("Failed to fetch chat history", error));
	}

	/**
	 * Get conversations for a user and app.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> getConversations(String email, String appId) {
		return webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/conversations")
				.queryParam("email", email)
				.queryParam("appId", appId)
				.build())
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> {
				Map<String, Object> result = new java.util.HashMap<>();
				Integer code = (Integer) response.get("code");
				if (code != null && code == 200 && response.get("data") != null) {
					result.put("data", response.get("data"));
				} else {
					result.put("data", List.of());
				}
				return result;
			})
			.doOnError(error -> log.error("Failed to fetch conversations", error));
	}

	/**
	 * Delete a conversation and its uploaded files.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> deleteConversation(String email, String conversationId) {
		return webClient.delete()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/conversation")
				.queryParam("email", email)
				.queryParam("conversationId", conversationId)
				.build())
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> (Map<String, Object>) response)
			.doOnError(error -> log.error("Failed to delete conversation", error));
	}

	/**
	 * Upload a file for a conversation.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> uploadFile(String email, String conversationId,
			String fileName, byte[] fileBytes, String contentType) {
		return webClient.post()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/upload")
				.queryParam("email", email)
				.queryParam("conversationId", conversationId)
				.build())
			.contentType(MediaType.MULTIPART_FORM_DATA)
			.body(org.springframework.web.reactive.function.BodyInserters.fromMultipartData(
				org.springframework.util.LinkedMultiValueMap.class.cast(buildMultipartData(fileName, fileBytes, contentType))))
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> (Map<String, Object>) response)
			.doOnError(error -> log.error("Failed to upload file", error));
	}

	private org.springframework.util.MultiValueMap<String, org.springframework.http.HttpEntity<?>> buildMultipartData(
			String fileName, byte[] fileBytes, String contentType) {
		org.springframework.util.LinkedMultiValueMap<String, org.springframework.http.HttpEntity<?>> parts =
			new org.springframework.util.LinkedMultiValueMap<>();
		org.springframework.http.HttpHeaders fileHeaders = new org.springframework.http.HttpHeaders();
		fileHeaders.setContentType(MediaType.parseMediaType(contentType != null ? contentType : "application/octet-stream"));
		fileHeaders.setContentDispositionFormData("file", fileName);
		org.springframework.http.HttpEntity<byte[]> filePart = new org.springframework.http.HttpEntity<>(fileBytes, fileHeaders);
		parts.add("file", filePart);
		return parts;
	}

	/**
	 * Search uploaded files in a conversation.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> searchFiles(String email, String conversationId, String query) {
		return webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/file-search")
				.queryParam("email", email)
				.queryParam("conversationId", conversationId)
				.queryParam("query", query)
				.build())
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> (Map<String, Object>) response)
			.doOnError(error -> log.error("Failed to search files", error));
	}

	/**
	 * Get uploaded files for a conversation.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> getFiles(String email, String conversationId) {
		return webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/files")
				.queryParam("email", email)
				.queryParam("conversationId", conversationId)
				.build())
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> (Map<String, Object>) response)
			.doOnError(error -> log.error("Failed to get files", error));
	}

	/**
	 * Log an audit action.
	 * Note: details may contain curly braces (e.g. JSON with {doc_id}),
	 * so we build a java.net.URI directly to bypass Spring's UriBuilder
	 * template expansion that treats {x} as a URI variable.
	 */
	public Mono<Void> logAuditAction(String email, String action, String resourceType,
			String resourceId, String details) {
		// Build query string manually to avoid UriBuilder template expansion of {x} patterns
		StringBuilder queryStr = new StringBuilder(baseUrl);
		queryStr.append("/console/v1/chatbot/audit-log?");
		queryStr.append("email=").append(urlEncode(email));
		queryStr.append("&action=").append(urlEncode(action));
		if (resourceType != null) queryStr.append("&resourceType=").append(urlEncode(resourceType));
		if (resourceId != null) queryStr.append("&resourceId=").append(urlEncode(resourceId));
		if (details != null) queryStr.append("&details=").append(urlEncode(details));

		java.net.URI uri = java.net.URI.create(queryStr.toString());
		return webClient.post()
			.uri(uri)
			.retrieve()
			.bodyToMono(Void.class)
			.doOnError(error -> log.warn("Failed to log audit action: {} - {}", action, error.getMessage()))
			.onErrorResume(error -> Mono.empty());
	}

	private String urlEncode(String value) {
		try {
			return java.net.URLEncoder.encode(value, "UTF-8");
		} catch (Exception e) {
			return value;
		}
	}

	/**
	 * Get audit logs.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> getAuditLogs(String email, int page, int pageSize) {
		return webClient.get()
			.uri(uriBuilder -> {
				var builder = uriBuilder.path("/console/v1/chatbot/audit-log")
					.queryParam("page", page)
					.queryParam("pageSize", pageSize);
				if (email != null) builder.queryParam("email", email);
				return builder.build();
			})
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> (Map<String, Object>) response)
			.doOnError(error -> log.error("Failed to get audit logs", error));
	}

	/**
	 * Search documents with ACL filtering.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> searchDocuments(String email, String appId, String query,
			int from, int size, String mimeType, String createdBy) {
		return webClient.get()
			.uri(uriBuilder -> {
				var builder = uriBuilder.path("/console/v1/chatbot/documents")
					.queryParam("email", email)
					.queryParam("appId", appId)
					.queryParam("from", from)
					.queryParam("size", size);
				if (query != null && !query.isBlank()) builder.queryParam("query", query);
				if (mimeType != null && !mimeType.isBlank()) builder.queryParam("mimeType", mimeType);
				if (createdBy != null && !createdBy.isBlank()) builder.queryParam("createdBy", createdBy);
				return builder.build();
			})
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> {
				Integer code = (Integer) response.get("code");
				if (code != null && code == 200 && response.get("data") != null) {
					return (Map<String, Object>) response.get("data");
				}
				return Map.<String, Object>of("documents", List.of(), "total", 0, "facets", Map.of());
			})
			.doOnError(error -> log.error("Failed to search documents", error));
	}

	/**
	 * Search RAG chunks with ACL filtering.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> searchRagChunks(String email, String appId, String query, int size) {
		return webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/rag-search")
				.queryParam("email", email)
				.queryParam("appId", appId)
				.queryParam("query", query)
				.queryParam("size", size)
				.build())
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> {
				Integer code = (Integer) response.get("code");
				if (code != null && code == 200 && response.get("data") != null) {
					return (Map<String, Object>) response.get("data");
				}
				return Map.<String, Object>of("chunks", List.of(), "total", 0);
			})
			.doOnError(error -> log.error("Failed to search RAG chunks", error));
	}

	/**
	 * Download a document from the source system via the admin backend.
	 * Returns the raw bytes as a Mono<byte[]>.
	 */
	public Mono<org.springframework.http.ResponseEntity<byte[]>> downloadDocument(
			String jwtToken, String kbId, String docId) {
		return webClient.get()
			.uri(uriBuilder -> uriBuilder.path("/console/v1/chatbot/document/download")
				.queryParam("kbId", kbId)
				.queryParam("docId", docId)
				.build())
			.header("Authorization", "Bearer " + jwtToken)
			.retrieve()
			.toEntity(byte[].class)
			.doOnError(error -> log.error("Failed to download document kbId={}, docId={}", kbId, docId, error));
	}

	/**
	 * Browse authorities (users/groups) for a knowledge base.
	 */
	@SuppressWarnings("unchecked")
	public Mono<Map<String, Object>> browseAuthorities(String jwtToken, String appId, String kbId,
			String query, String principalType, int from, int size) {
		return webClient.get()
			.uri(uriBuilder -> {
				var builder = uriBuilder.path("/console/v1/chatbot/authorities")
					.queryParam("appId", appId)
					.queryParam("from", from)
					.queryParam("size", size);
				if (kbId != null && !kbId.isBlank()) builder.queryParam("kbId", kbId);
				if (query != null && !query.isBlank()) builder.queryParam("query", query);
				if (principalType != null && !principalType.isBlank()) builder.queryParam("principalType", principalType);
				return builder.build();
			})
			.header("Authorization", "Bearer " + jwtToken)
			.retrieve()
			.bodyToMono(Map.class)
			.map(response -> {
				Integer code = (Integer) response.get("code");
				if (code != null && code == 200 && response.get("data") != null) {
					return (Map<String, Object>) response.get("data");
				}
				return Map.<String, Object>of("authorities", List.of(), "total", 0);
			})
			.doOnError(error -> log.error("Failed to browse authorities", error));
	}

	/**
	 * Get the base URL for constructing document download URLs.
	 */
	public String getBaseUrl() {
		return baseUrl;
	}

}
