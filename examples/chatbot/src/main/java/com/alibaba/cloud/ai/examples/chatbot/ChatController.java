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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpSession;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Controller for handling authenticated chat conversations.
 * Supports app-scoped access and chat history persistence.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
public class ChatController {

	private final AdminApiService adminApi;
	private final Map<String, String> sessionConversations = new ConcurrentHashMap<>();

	public ChatController(AdminApiService adminApi) {
		this.adminApi = adminApi;
	}

	@SuppressWarnings("unchecked")
	@PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
	public Flux<String> chat(@RequestParam String message,
			@RequestParam(required = false) String appId,
			HttpSession session) {
		String token = (String) session.getAttribute("token");
		String email = (String) session.getAttribute("email");
		Map<String, Object> user = (Map<String, Object>) session.getAttribute("user");

		if (token == null || user == null) {
			log.warn("Unauthenticated chat attempt");
			return Flux.error(new RuntimeException("Not authenticated. Please login first."));
		}

		// Get app: use provided appId or fall back to first accessible app
		List<Map<String, Object>> apps = (List<Map<String, Object>>) user.get("apps");
		if (apps == null || apps.isEmpty()) {
			log.warn("User {} has no apps available", email);
			return Flux.error(new RuntimeException("No apps available. Contact administrator to grant access."));
		}

		String resolvedAppId = appId;
		if (resolvedAppId == null || resolvedAppId.isEmpty()) {
			resolvedAppId = (String) apps.get(0).get("app_id");
		}

		String conversationKey = session.getId() + ":" + resolvedAppId;
		String conversationId = sessionConversations.get(conversationKey);

		// Generate new conversation ID if needed
		if (conversationId == null) {
			conversationId = java.util.UUID.randomUUID().toString();
			sessionConversations.put(conversationKey, conversationId);
			log.info("New conversation {} for user {} on app {}", conversationId, email, resolvedAppId);
		}

		final String finalAppId = resolvedAppId;
		final String finalConversationId = conversationId;
		
		log.info("Chat request from user: {}, appId: {}, conversationId: {}", email, finalAppId, finalConversationId);

		// Save user message to chat history
		adminApi.saveChatMessage(email, finalAppId, finalConversationId, "user", message)
			.subscribe(
				result -> log.debug("User message saved to history"),
				error -> log.warn("Failed to save user message to history", error)
			);

		// Log audit action for chat (include prompt in details for audit trail)
		String promptPreview = message.length() > 200 ? message.substring(0, 200) + "..." : message;
		adminApi.logAuditAction(email, "CHAT_MESSAGE", "app", finalAppId,
			"conversation=" + finalConversationId + "|prompt=" + promptPreview).subscribe();

		// Accumulate full assistant response for saving to history
		StringBuilder assistantResponse = new StringBuilder();
		// Accumulate RAG retrieval docs from FILE_SEARCH_RESULT events
		StringBuilder ragDocsJson = new StringBuilder();

		return adminApi.chatStream(finalAppId, token, message, finalConversationId)
			.doOnNext(chunk -> {
				try {
					// Intercept FILE_SEARCH_RESULT to capture which docs RAG retrieved
					if (chunk.contains("\"file_search_result\"")) {
						String extracted = extractFileSearchOutput(chunk);
						if (extracted != null) {
							if (ragDocsJson.length() > 0) ragDocsJson.append(",");
							ragDocsJson.append(extracted);
						}
					}
					// Accumulate assistant response content from SSE chunks
					if (chunk.contains("\"content\":")) {
						String content = extractContent(chunk);
						if (content != null) {
							assistantResponse.append(content);
						}
					}
				}
				catch (Exception e) {
					log.debug("Failed to extract content from chunk", e);
				}
			})
			.timeout(Duration.ofMinutes(10))
			.doOnComplete(() -> {
				// Save complete assistant response to chat history
				if (assistantResponse.length() > 0) {
					adminApi.saveChatMessage(email, finalAppId, finalConversationId, "assistant", assistantResponse.toString())
						.subscribe(
							result -> log.debug("Assistant response saved to history"),
							error -> log.warn("Failed to save assistant response to history", error)
						);
				}
				// Log which documents RAG retrieved for this chat (audit trail)
				if (ragDocsJson.length() > 0) {
					String ragDetails = "conversation=" + finalConversationId + "|rag_docs=[" + ragDocsJson.toString() + "]";
					adminApi.logAuditAction(email, "RAG_RETRIEVAL", "app", finalAppId, ragDetails).subscribe();
					log.info("RAG retrieval logged for conversation {}: {} docs", finalConversationId,
						ragDocsJson.toString().split("\\},").length);
				}
			})
			.onErrorResume(error -> {
				log.error("Chat stream error for user: {}", email, error);
				String errorMsg = error.getMessage();
				if (errorMsg != null && (errorMsg.contains("timeout") || errorMsg.contains("TimeoutException"))) {
					return Flux.just("data:{\"error\":\"Request timed out. The AI model may be busy or slow. Please try again.\"}\n\n");
				}
				return Flux.just("data:{\"error\":\"An error occurred: " + (errorMsg != null ? errorMsg.replace("\"", "'") : "Unknown") + "\"}\n\n");
			});
	}

	@GetMapping("/conversation")
	public ResponseEntity<Map<String, String>> getConversation(
			@RequestParam(required = false) String appId,
			HttpSession session) {
		String conversationKey = session.getId() + ":" + (appId != null ? appId : "");
		String conversationId = sessionConversations.get(conversationKey);
		if (conversationId != null) {
			return ResponseEntity.ok(Map.of("conversationId", conversationId));
		}
		return ResponseEntity.ok(Map.of("conversationId", ""));
	}

	@PostMapping("/new-conversation")
	public ResponseEntity<Map<String, String>> newConversation(
			@RequestParam String appId,
			HttpSession session) {
		String conversationKey = session.getId() + ":" + appId;
		String conversationId = java.util.UUID.randomUUID().toString();
		sessionConversations.put(conversationKey, conversationId);
		return ResponseEntity.ok(Map.of("conversationId", conversationId));
	}

	@SuppressWarnings("unchecked")
	@GetMapping("/history")
	public Mono<ResponseEntity<Map<String, Object>>> getChatHistory(
			@RequestParam String appId,
			@RequestParam(required = false) String conversationId,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		return adminApi.getChatHistory(email, appId, conversationId)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to fetch chat history", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	@SuppressWarnings("unchecked")
	@GetMapping("/conversations")
	public Mono<ResponseEntity<Map<String, Object>>> getConversations(
			@RequestParam String appId,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		return adminApi.getConversations(email, appId)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to fetch conversations", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	@SuppressWarnings("unchecked")
	@DeleteMapping("/conversation")
	public Mono<ResponseEntity<Map<String, Object>>> deleteConversation(
			@RequestParam String conversationId,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		// Remove from session map
		sessionConversations.entrySet().removeIf(e -> e.getValue().equals(conversationId));
		// Log audit action
		adminApi.logAuditAction(email, "DELETE_CONVERSATION", "conversation", conversationId, null)
			.subscribe();
		return adminApi.deleteConversation(email, conversationId)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to delete conversation", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	@SuppressWarnings("unchecked")
	@PostMapping("/upload")
	public Mono<ResponseEntity<Map<String, Object>>> uploadFile(
			@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
			@RequestParam String conversationId,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		try {
			byte[] bytes = file.getBytes();
			String fileName = file.getOriginalFilename();
			String contentType = file.getContentType();
			log.info("File upload: {} ({} bytes) for conversation {} by {}", fileName, bytes.length, conversationId, email);
			// Log audit action
			adminApi.logAuditAction(email, "UPLOAD_FILE", "file", fileName,
				"size=" + bytes.length + ",conversation=" + conversationId).subscribe();
			return adminApi.uploadFile(email, conversationId, fileName, bytes, contentType)
				.map(data -> ResponseEntity.ok(data))
				.onErrorResume(error -> {
					log.error("File upload failed", error);
					return Mono.just(ResponseEntity.internalServerError().build());
				});
		}
		catch (Exception e) {
			log.error("Failed to read uploaded file", e);
			return Mono.just(ResponseEntity.internalServerError().build());
		}
	}

	@SuppressWarnings("unchecked")
	@GetMapping("/files")
	public Mono<ResponseEntity<Map<String, Object>>> getFiles(
			@RequestParam String conversationId,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		return adminApi.getFiles(email, conversationId)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to get files", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	@SuppressWarnings("unchecked")
	@GetMapping("/file-search")
	public Mono<ResponseEntity<Map<String, Object>>> searchFiles(
			@RequestParam String conversationId,
			@RequestParam String query,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		// Log audit action
		adminApi.logAuditAction(email, "FILE_SEARCH", "file", null,
			"query=" + query + ",conversation=" + conversationId).subscribe();
		return adminApi.searchFiles(email, conversationId, query)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to search files", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	@SuppressWarnings("unchecked")
	@GetMapping("/audit-log")
	public Mono<ResponseEntity<Map<String, Object>>> getAuditLogs(
			@RequestParam(required = false) String email,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int pageSize,
			HttpSession session) {
		String sessionEmail = (String) session.getAttribute("email");
		if (sessionEmail == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		// Non-admin users can only see their own logs
		String queryEmail = email != null ? email : sessionEmail;
		return adminApi.getAuditLogs(queryEmail, page, pageSize)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to get audit logs", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	/**
	 * ACL-filtered document search endpoint.
	 * Returns documents the logged-in user is authorized to see.
	 */
	@GetMapping("/documents")
	public Mono<ResponseEntity<Map<String, Object>>> searchDocuments(
			@RequestParam String appId,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") int from,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) String mimeType,
			@RequestParam(required = false) String createdBy,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		// Log audit action for document search
		if (query != null && !query.isBlank()) {
			adminApi.logAuditAction(email, "DOCUMENT_SEARCH", "app", appId, "query=" + query).subscribe();
		}
		return adminApi.searchDocuments(email, appId, query, from, size, mimeType, createdBy)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to search documents", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	/**
	 * ACL-filtered RAG chunk search endpoint.
	 */
	@GetMapping("/rag-search")
	public Mono<ResponseEntity<Map<String, Object>>> searchRagChunks(
			@RequestParam String appId,
			@RequestParam String query,
			@RequestParam(defaultValue = "10") int size,
			HttpSession session) {
		String email = (String) session.getAttribute("email");
		if (email == null) {
			return Mono.just(ResponseEntity.status(401).build());
		}
		return adminApi.searchRagChunks(email, appId, query, size)
			.map(data -> ResponseEntity.ok(data))
			.onErrorResume(error -> {
				log.error("Failed to search RAG chunks", error);
				return Mono.just(ResponseEntity.internalServerError().build());
			});
	}

	/**
	 * Extract document info from FILE_SEARCH_RESULT SSE events.
	 * The output field is double-serialized JSON containing DocumentChunk data.
	 * Returns compact JSON objects like: {"doc_id":"x","doc_name":"y","score":0.9,"chunk_id":"z"}
	 */
	private String extractFileSearchOutput(String sseChunk) {
		try {
			// Find "output":"[...] — the value is a JSON-escaped string
			int outputIdx = sseChunk.indexOf("\"output\":");
			if (outputIdx == -1) return null;
			int valStart = sseChunk.indexOf("\"", outputIdx + 9);
			if (valStart == -1) return null;
			valStart++;
			// Find the closing quote (handle escaped quotes)
			int valEnd = valStart;
			while (valEnd < sseChunk.length()) {
				if (sseChunk.charAt(valEnd) == '"' && sseChunk.charAt(valEnd - 1) != '\\') break;
				valEnd++;
			}
			if (valEnd >= sseChunk.length()) return null;
			String escaped = sseChunk.substring(valStart, valEnd);
			// Unescape the JSON string
			String json = escaped.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", "\t");
			// Parse individual doc entries by finding doc_id occurrences
			// JSON format: {"text":"...","score":0.57,"enabled":true,"doc_id":"x","doc_name":"y","page_number":0,"chunk_id":"z"}
			// The text field can contain any characters including { and }, so we search
			// forward from doc_id position for the metadata fields that follow it
			StringBuilder result = new StringBuilder();
			int searchFrom = 0;
			while ((searchFrom = json.indexOf("\"doc_id\"", searchFrom)) != -1) {
				// doc_id is followed by doc_name, page_number, chunk_id in the JSON
				// score appears BEFORE doc_id in the object
				String docId = extractJsonString(json, "doc_id", searchFrom);
				String docName = extractJsonString(json, "doc_name", searchFrom);
				String chunkId = extractJsonString(json, "chunk_id", searchFrom);
				// Score is BEFORE doc_id - search backwards for it
				// Find the "score": pattern before this doc_id
				int scoreSearchEnd = searchFrom;
				int scoreIdx = json.lastIndexOf("\"score\"", scoreSearchEnd);
				String score = null;
				if (scoreIdx != -1 && scoreIdx > searchFrom - 2000) {
					score = extractJsonNumber(json, "score", scoreIdx);
				}
				if (docId != null) {
					if (result.length() > 0) result.append(",");
					result.append("{\"doc_id\":\"").append(docId)
						.append("\",\"doc_name\":\"").append(docName != null ? docName : "")
						.append("\",\"score\":").append(score != null ? score : "0")
						.append(",\"chunk_id\":\"").append(chunkId != null ? chunkId : "")
						.append("\"}");
				}
				searchFrom += 10;
			}
			return result.length() > 0 ? result.toString() : null;
		} catch (Exception e) {
			log.debug("Failed to parse FILE_SEARCH_RESULT", e);
			return null;
		}
	}

	private String extractJsonString(String json, String key, int searchFrom) {
		int idx = json.indexOf("\"" + key + "\"", searchFrom);
		if (idx == -1 || idx > searchFrom + 5000) return null;
		int colonIdx = json.indexOf(":", idx + key.length() + 2);
		if (colonIdx == -1) return null;
		int valStart = json.indexOf("\"", colonIdx);
		if (valStart == -1) return null;
		valStart++;
		int valEnd = json.indexOf("\"", valStart);
		if (valEnd == -1) return null;
		return json.substring(valStart, valEnd);
	}

	private String extractJsonNumber(String json, String key, int searchFrom) {
		int idx = json.indexOf("\"" + key + "\"", searchFrom);
		if (idx == -1 || idx > searchFrom + 500) return null;
		int colonIdx = json.indexOf(":", idx + key.length() + 2);
		if (colonIdx == -1) return null;
		int valStart = colonIdx + 1;
		while (valStart < json.length() && json.charAt(valStart) == ' ') valStart++;
		int valEnd = valStart;
		while (valEnd < json.length() && (Character.isDigit(json.charAt(valEnd)) || json.charAt(valEnd) == '.' || json.charAt(valEnd) == '-')) valEnd++;
		if (valEnd == valStart) return null;
		return json.substring(valStart, valEnd);
	}

	private String extractContent(String sseChunk) {
		try {
			// Admin API format: data:{"status":"in_progress","message":{"content":"text"}}
			// Also skip "content_type" field - look for "content" that is NOT "content_type"
			int searchFrom = 0;
			while (true) {
				int start = sseChunk.indexOf("\"content\":", searchFrom);
				if (start == -1) return null;
				
				// Check this isn't "content_type"
				if (start > 0 && sseChunk.charAt(start - 1) == '_') {
					// This is part of "content_type", skip it
					searchFrom = start + 10;
					continue;
				}
				
				// Find the value after "content":
				int valueStart = sseChunk.indexOf("\"", start + 10);
				if (valueStart == -1) return null;
				valueStart++; // skip the opening quote
				
				int valueEnd = sseChunk.indexOf("\"", valueStart);
				if (valueEnd == -1) return null;
				
				String content = sseChunk.substring(valueStart, valueEnd);
				if (content.isEmpty()) return null;
				
				return content
					.replace("\\n", "\n")
					.replace("\\t", "\t")
					.replace("\\\"", "\"");
			}
		}
		catch (Exception e) {
			return null;
		}
	}

}
