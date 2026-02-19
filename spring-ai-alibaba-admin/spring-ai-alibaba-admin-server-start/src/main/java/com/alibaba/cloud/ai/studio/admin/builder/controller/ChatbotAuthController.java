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
package com.alibaba.cloud.ai.studio.admin.builder.controller;

import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentRequest;
import com.alibaba.cloud.ai.studio.runtime.domain.agent.AgentResponse;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.alibaba.cloud.ai.studio.core.base.service.AgentService;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.rag.KnowledgeSyncService;
import com.alibaba.cloud.ai.studio.core.utils.common.IdGenerator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.opensearch.client.Request;
import org.opensearch.client.Response;
import org.opensearch.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;

import javax.crypto.SecretKey;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Authentication controller for chatbot users.
 * Provides login, app-scoped access control, and chat history persistence.
 */
@Slf4j
@RestController
@Tag(name = "chatbot_auth")
@RequestMapping("/console/v1/chatbot")
public class ChatbotAuthController {

	private final JdbcTemplate jdbcTemplate;

	private final BCryptPasswordEncoder passwordEncoder;

	private final SecretKey secretKey;

	private final AgentService agentService;

	private final RestClient openSearchRestClient;

	private final ObjectMapper objectMapper;

	private final KnowledgeSyncService knowledgeSyncService;

	private final long jwtExpiration = 24 * 60 * 60 * 1000; // 24 hours

	/** Default account ID used for chatbot agent requests (admin account) */
	private static final String CHATBOT_ACCOUNT_ID = "10000";

	/** Default workspace ID used for chatbot agent requests */
	private static final String CHATBOT_WORKSPACE_ID = "1";

	public ChatbotAuthController(JdbcTemplate jdbcTemplate, AgentService agentService,
			RestClient openSearchRestClient, ObjectMapper objectMapper,
			KnowledgeSyncService knowledgeSyncService,
@Value("${chatbot.jwt.secret:my-super-secret-key-change-in-production}") String jwtSecret) {
		this.jdbcTemplate = jdbcTemplate;
		this.agentService = agentService;
		this.openSearchRestClient = openSearchRestClient;
		this.objectMapper = objectMapper;
		this.knowledgeSyncService = knowledgeSyncService;
		this.passwordEncoder = new BCryptPasswordEncoder();
		this.secretKey = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
	}

	@Data
	public static class LoginRequest {

		private String email;

		private String password;

	}

	@Data
	public static class LoginResponse {

		private String token;

		private String email;

		private String fullName;

		private List<Map<String, Object>> apps;

	}

	@Data
	public static class AppAccessRequest {

		private String appId;

		private List<String> userEmails;

	}

	@Data
	public static class ChatHistoryRequest {

		private String appId;

		private String role;

		private String content;

		private String conversationId;

	}

	// Authentication

	@Operation(summary = "Login for chatbot users")
	@PostMapping("/login")
	public Result<LoginResponse> login(@RequestBody LoginRequest request) {
		log.info("Login attempt for email: {}", request.getEmail());

		String sql = "SELECT email, password_hash, full_name FROM simple_users WHERE email = ?";
		List<Map<String, Object>> users = jdbcTemplate.queryForList(sql, request.getEmail());

		String fullName;

		if (users.isEmpty()) {
			// Auto-provision: create user on first login
			log.info("Auto-provisioning new user: {}", request.getEmail());
			String hashedPassword = passwordEncoder.encode(request.getPassword());
			// Derive full name from email (e.g., john@example.com -> John)
			String emailPrefix = request.getEmail().split("@")[0];
			fullName = emailPrefix.substring(0, 1).toUpperCase() + emailPrefix.substring(1);
			jdbcTemplate.update(
				"INSERT INTO simple_users (email, password_hash, full_name) VALUES (?, ?, ?) ON CONFLICT (email) DO NOTHING",
				request.getEmail(), hashedPassword, fullName);
			log.info("Auto-provisioned user: {} ({})", request.getEmail(), fullName);
		}
		else {
			Map<String, Object> user = users.get(0);
			String storedHash = (String) user.get("password_hash");
			fullName = (String) user.get("full_name");

			if (!passwordEncoder.matches(request.getPassword(), storedHash)) {
				return Result.error(401, "Invalid email or password");
			}
		}

		jdbcTemplate.update("UPDATE simple_users SET last_login = NOW() WHERE email = ?", request.getEmail());

		// Note: App access must be explicitly granted by admin via /app-access endpoint
		// or pre-seeded in app_user_access table. No auto-grant of all apps.

		// Get user accessible apps (join with application table)
		List<Map<String, Object>> apps = jdbcTemplate.queryForList(
"SELECT a.app_id, a.name, a.description, a.type FROM application a INNER JOIN app_user_access aua ON a.app_id = aua.app_id WHERE aua.user_email = ?",
request.getEmail());

		String token = Jwts.builder()
			.subject(request.getEmail())
			.issuedAt(new Date())
			.expiration(new Date(System.currentTimeMillis() + jwtExpiration))
			.signWith(secretKey)
			.compact();

		LoginResponse response = new LoginResponse();
		response.setToken(token);
		response.setEmail(request.getEmail());
		response.setFullName(fullName);
		response.setApps(apps);

		log.info("User {} logged in successfully with access to {} apps", request.getEmail(), apps.size());
		return Result.success(response);
	}

	@Operation(summary = "Validate JWT token")
	@PostMapping("/validate-token")
	public Result<Map<String, Object>> validateToken(@RequestHeader("Authorization") String authHeader) {
		try {
			String token = authHeader.replace("Bearer ", "");
			String email = Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();

			Map<String, Object> response = new HashMap<>();
			response.put("email", email);
			response.put("valid", true);
			return Result.success(response);
		}
		catch (Exception e) {
			return Result.error(401, "Token is invalid or expired");
		}
	}

	// App Access Control

	@Operation(summary = "Check if user has access to app")
	@GetMapping("/check-access")
	public Result<Boolean> checkAccess(@RequestParam String email, @RequestParam String appId) {
		Integer count = jdbcTemplate.queryForObject(
"SELECT COUNT(*) FROM app_user_access WHERE user_email = ? AND app_id = ?", Integer.class, email,
appId);
		boolean hasAccess = count != null && count > 0;
		return Result.success(hasAccess);
	}

	@Operation(summary = "Get apps accessible by user")
	@GetMapping("/user-apps")
	public Result<List<Map<String, Object>>> getUserApps(@RequestParam String email) {
		List<Map<String, Object>> apps = jdbcTemplate.queryForList(
"SELECT a.app_id, a.name, a.description, a.type FROM application a INNER JOIN app_user_access aua ON a.app_id = aua.app_id WHERE aua.user_email = ?",
email);
		return Result.success(apps);
	}

	@Operation(summary = "Manage app user access")
	@PostMapping("/app-access")
	public Result<String> manageAppAccess(@RequestBody AppAccessRequest request) {
		jdbcTemplate.update("DELETE FROM app_user_access WHERE app_id = ?", request.getAppId());

		if (request.getUserEmails() != null && !request.getUserEmails().isEmpty()) {
			String sql = "INSERT INTO app_user_access (app_id, user_email) VALUES (?, ?) ON CONFLICT DO NOTHING";
			for (String email : request.getUserEmails()) {
				jdbcTemplate.update(sql, request.getAppId(), email);
			}
			log.info("Updated app access for app {} with {} users", request.getAppId(),
					request.getUserEmails().size());
		}
		return Result.success("App access updated successfully");
	}

	@Operation(summary = "Get users with access to app")
	@GetMapping("/app-users")
	public Result<List<String>> getAppUsers(@RequestParam String appId) {
		List<String> emails = jdbcTemplate.queryForList(
"SELECT user_email FROM app_user_access WHERE app_id = ? ORDER BY user_email", String.class, appId);
		return Result.success(emails);
	}

	@Operation(summary = "Get all registered users")
	@GetMapping("/users")
	public Result<List<Map<String, Object>>> getAllUsers() {
		List<Map<String, Object>> users = jdbcTemplate
			.queryForList("SELECT email, full_name FROM simple_users ORDER BY email");
		return Result.success(users);
	}

	// Chat History

	@Operation(summary = "Save a chat message")
	@PostMapping("/chat-history")
	public Result<String> saveChatMessage(@RequestBody ChatHistoryRequest request, @RequestParam String email) {
		jdbcTemplate.update(
"INSERT INTO chat_history (user_email, app_id, conversation_id, role, content) VALUES (?, ?, ?, ?, ?)",
email, request.getAppId(), request.getConversationId(), request.getRole(), request.getContent());
		return Result.success("Message saved");
	}

	@Operation(summary = "Get chat history for user and app")
	@GetMapping("/chat-history")
	public Result<List<Map<String, Object>>> getChatHistory(@RequestParam String email, @RequestParam String appId,
@RequestParam(required = false) String conversationId) {
		if (conversationId != null && !conversationId.isEmpty()) {
			return Result.success(jdbcTemplate.queryForList(
"SELECT role, content, conversation_id, created_at FROM chat_history WHERE user_email = ? AND app_id = ? AND conversation_id = ? ORDER BY created_at",
email, appId, conversationId));
		}
		return Result.success(jdbcTemplate.queryForList(
"SELECT role, content, conversation_id, created_at FROM chat_history WHERE user_email = ? AND app_id = ? ORDER BY created_at DESC LIMIT 100",
email, appId));
	}

	@Operation(summary = "Get user conversations for an app")
	@GetMapping("/conversations")
	public Result<List<Map<String, Object>>> getConversations(@RequestParam String email,
@RequestParam String appId) {
		return Result.success(jdbcTemplate.queryForList(
"SELECT conversation_id, MIN(created_at) as started_at, MAX(created_at) as last_message_at, COUNT(*) as message_count FROM chat_history WHERE user_email = ? AND app_id = ? GROUP BY conversation_id ORDER BY MAX(created_at) DESC",
email, appId));
	}

	@Operation(summary = "Delete a conversation and its uploaded files")
	@DeleteMapping("/conversation")
	public Result<String> deleteConversation(@RequestParam String email, @RequestParam String conversationId) {
		log.info("Deleting conversation {} for user {}", conversationId, email);
		// Delete uploaded files from disk
		List<Map<String, Object>> files = jdbcTemplate.queryForList(
			"SELECT file_path FROM uploaded_files WHERE conversation_id = ? AND user_email = ?",
			conversationId, email);
		for (Map<String, Object> file : files) {
			try {
				java.nio.file.Files.deleteIfExists(java.nio.file.Paths.get((String) file.get("file_path")));
			}
			catch (Exception e) {
				log.warn("Failed to delete file: {}", file.get("file_path"), e);
			}
		}
		// Delete from DB
		jdbcTemplate.update("DELETE FROM uploaded_files WHERE conversation_id = ? AND user_email = ?",
			conversationId, email);
		int deleted = jdbcTemplate.update(
			"DELETE FROM chat_history WHERE conversation_id = ? AND user_email = ?",
			conversationId, email);
		log.info("Deleted {} messages and {} files from conversation {}", deleted, files.size(), conversationId);
		return Result.success("Conversation deleted (" + deleted + " messages, " + files.size() + " files)");
	}

	// File Upload

	@Operation(summary = "Upload a file for a conversation (not RAG, local search only)")
	@PostMapping("/upload")
	public Result<Map<String, Object>> uploadFile(
			@RequestParam String email,
			@RequestParam String conversationId,
			@RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
		log.info("File upload: {} ({} bytes) for conversation {} by {}",
			file.getOriginalFilename(), file.getSize(), conversationId, email);

		try {
			// Read bytes first (before any transfer consumes the stream)
			byte[] fileBytes = file.getBytes();

			// Save file to disk
			String uploadDir = System.getProperty("java.io.tmpdir") + "/chatbot-uploads/" + conversationId;
			java.nio.file.Path dirPath = java.nio.file.Paths.get(uploadDir);
			java.nio.file.Files.createDirectories(dirPath);

			String safeFileName = file.getOriginalFilename().replaceAll("[^a-zA-Z0-9._-]", "_");
			java.nio.file.Path filePath = dirPath.resolve(safeFileName);
			java.nio.file.Files.write(filePath, fileBytes);

			// Extract text content from the file
			String extractedText = new String(fileBytes, StandardCharsets.UTF_8);

			// Save metadata to DB
			jdbcTemplate.update(
				"INSERT INTO uploaded_files (user_email, conversation_id, file_name, file_path, file_size, content_type, extracted_text) VALUES (?, ?, ?, ?, ?, ?, ?)",
				email, conversationId, file.getOriginalFilename(), filePath.toString(),
				file.getSize(), file.getContentType(), extractedText);

			Map<String, Object> result = new HashMap<>();
			result.put("fileName", file.getOriginalFilename());
			result.put("fileSize", file.getSize());
			result.put("conversationId", conversationId);
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("File upload failed", e);
			return Result.error(500, "File upload failed: " + e.getMessage());
		}
	}

	@Operation(summary = "Search uploaded files in a conversation")
	@GetMapping("/file-search")
	public Result<List<Map<String, Object>>> searchFiles(
			@RequestParam String email,
			@RequestParam String conversationId,
			@RequestParam String query) {
		List<Map<String, Object>> files = jdbcTemplate.queryForList(
			"SELECT file_name, extracted_text FROM uploaded_files WHERE user_email = ? AND conversation_id = ?",
			email, conversationId);

		java.util.List<Map<String, Object>> results = new java.util.ArrayList<>();
		String queryLower = query.toLowerCase();
		for (Map<String, Object> file : files) {
			String text = (String) file.get("extracted_text");
			if (text != null && text.toLowerCase().contains(queryLower)) {
				// Find matching lines
				String[] lines = text.split("\n");
				java.util.List<String> matchingLines = new java.util.ArrayList<>();
				for (String line : lines) {
					if (line.toLowerCase().contains(queryLower)) {
						matchingLines.add(line.trim());
					}
				}
				Map<String, Object> match = new HashMap<>();
				match.put("fileName", file.get("file_name"));
				match.put("matchingLines", matchingLines);
				match.put("matchCount", matchingLines.size());
				results.add(match);
			}
		}
		return Result.success(results);
	}

	@Operation(summary = "Get uploaded files for a conversation")
	@GetMapping("/files")
	public Result<List<Map<String, Object>>> getFiles(
			@RequestParam String email,
			@RequestParam String conversationId) {
		return Result.success(jdbcTemplate.queryForList(
			"SELECT file_name, file_size, content_type, created_at FROM uploaded_files WHERE user_email = ? AND conversation_id = ?",
			email, conversationId));
	}

	// Audit Log

	@Operation(summary = "Log a user action")
	@PostMapping("/audit-log")
	public Result<String> logAction(
			@RequestParam String email,
			@RequestParam String action,
			@RequestParam(required = false) String resourceType,
			@RequestParam(required = false) String resourceId,
			@RequestParam(required = false) String details,
			@RequestParam(required = false) String ipAddress) {
		jdbcTemplate.update(
			"INSERT INTO audit_log (user_email, action, resource_type, resource_id, details, ip_address) VALUES (?, ?, ?, ?, ?, ?)",
			email, action, resourceType, resourceId, details, ipAddress);
		return Result.success("Action logged");
	}

	@Operation(summary = "Get audit logs (admin only)")
	@GetMapping("/audit-log")
	public Result<Object> getAuditLogs(
			@RequestParam(required = false) String email,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int pageSize) {
		int offset = (page - 1) * pageSize;
		List<Map<String, Object>> logs;
		int totalCount;
		if (email != null && !email.isEmpty()) {
			totalCount = jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM audit_log WHERE user_email = ?", Integer.class, email);
			logs = jdbcTemplate.queryForList(
				"SELECT user_email, action, resource_type, resource_id, details, ip_address, created_at FROM audit_log WHERE user_email = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
				email, pageSize, offset);
		}
		else {
			totalCount = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM audit_log", Integer.class);
			logs = jdbcTemplate.queryForList(
				"SELECT user_email, action, resource_type, resource_id, details, ip_address, created_at FROM audit_log ORDER BY created_at DESC LIMIT ? OFFSET ?",
				pageSize, offset);
		}
		Map<String, Object> result = new HashMap<>();
		result.put("logs", logs);
		result.put("totalCount", totalCount);
		result.put("page", page);
		result.put("pageSize", pageSize);
		return Result.success(result);
	}

	@Operation(summary = "Get audit log user summary - aggregated per user")
	@GetMapping("/audit-log/users")
	public Result<Object> getAuditLogUsers() {
		// Get all users with their audit summary
		List<Map<String, Object>> users = jdbcTemplate.queryForList(
			"SELECT su.email, su.full_name, su.last_login, " +
			"COALESCE(al.total_actions, 0) AS total_actions, " +
			"COALESCE(al.last_activity, su.last_login) AS last_activity, " +
			"COALESCE(al.login_count, 0) AS login_count, " +
			"COALESCE(al.chat_count, 0) AS chat_count, " +
			"COALESCE(al.search_count, 0) AS search_count, " +
			"COALESCE(al.delete_count, 0) AS delete_count, " +
			"COALESCE(al.upload_count, 0) AS upload_count, " +
			"(SELECT COUNT(*) FROM app_user_access aua WHERE aua.user_email = su.email) AS app_count " +
			"FROM simple_users su " +
			"LEFT JOIN (" +
			"  SELECT user_email, COUNT(*) AS total_actions, MAX(created_at) AS last_activity, " +
			"  COUNT(*) FILTER (WHERE action = 'LOGIN') AS login_count, " +
			"  COUNT(*) FILTER (WHERE action = 'CHAT_MESSAGE') AS chat_count, " +
			"  COUNT(*) FILTER (WHERE action = 'FILE_SEARCH') AS search_count, " +
			"  COUNT(*) FILTER (WHERE action = 'DELETE_CONVERSATION') AS delete_count, " +
			"  COUNT(*) FILTER (WHERE action = 'UPLOAD_FILE') AS upload_count " +
			"  FROM audit_log GROUP BY user_email" +
			") al ON su.email = al.user_email " +
			"ORDER BY al.last_activity DESC NULLS LAST"
		);
		return Result.success(users);
	}

	@Operation(summary = "Get detailed audit trail for a specific user")
	@GetMapping("/audit-log/user-detail")
	public Result<Object> getAuditLogUserDetail(
			@RequestParam String email,
			@RequestParam(defaultValue = "1") int page,
			@RequestParam(defaultValue = "50") int pageSize) {
		int offset = (page - 1) * pageSize;
		Map<String, Object> result = new HashMap<>();

		// User info
		List<Map<String, Object>> userInfo = jdbcTemplate.queryForList(
			"SELECT email, full_name, last_login, created_time FROM simple_users WHERE email = ?", email);
		result.put("user", userInfo.isEmpty() ? null : userInfo.get(0));

		// Apps the user has access to (explains WHY they can see documents)
		List<Map<String, Object>> userApps = jdbcTemplate.queryForList(
			"SELECT a.app_id, a.name, a.description, a.type FROM application a " +
			"INNER JOIN app_user_access aua ON a.app_id = aua.app_id WHERE aua.user_email = ?", email);
		result.put("apps", userApps);

		// Audit log entries with enriched data (exclude RAG_RETRIEVAL - it's internal metadata)
		int totalCount = jdbcTemplate.queryForObject(
			"SELECT COUNT(*) FROM audit_log WHERE user_email = ? AND action != 'RAG_RETRIEVAL'", Integer.class, email);
		List<Map<String, Object>> logs = jdbcTemplate.queryForList(
			"SELECT al.id, al.action, al.resource_type, al.resource_id, al.details, al.ip_address, al.created_at, " +
			"CASE WHEN al.action = 'CHAT_MESSAGE' AND al.resource_id IS NOT NULL " +
			"  THEN (SELECT a.name FROM application a WHERE a.app_id = al.resource_id) " +
			"  ELSE NULL END AS app_name, " +
			"CASE WHEN al.action = 'CHAT_MESSAGE' THEN " +
			"  (SELECT CASE WHEN EXISTS(" +
			"    SELECT 1 FROM chat_history ch WHERE ch.user_email = al.user_email " +
			"    AND ch.conversation_id = split_part(REPLACE(al.details, 'conversation=', ''), '|', 1)" +
			"  ) THEN true ELSE false END) " +
			"  ELSE NULL END AS chat_available, " +
			"CASE WHEN al.action = 'CHAT_MESSAGE' THEN " +
			"  EXISTS(SELECT 1 FROM audit_log r WHERE r.user_email = al.user_email " +
			"    AND r.action = 'RAG_RETRIEVAL' " +
			"    AND r.details LIKE 'conversation=' || split_part(REPLACE(al.details, 'conversation=', ''), '|', 1) || '%')" +
			"  ELSE false END AS has_rag " +
			"FROM audit_log al WHERE al.user_email = ? AND al.action != 'RAG_RETRIEVAL' ORDER BY al.created_at DESC LIMIT ? OFFSET ?",
			email, pageSize, offset);
		result.put("logs", logs);
		result.put("totalCount", totalCount);
		result.put("page", page);
		result.put("pageSize", pageSize);

		return Result.success(result);
	}

	@Operation(summary = "Get chat detail for audit - shows conversation messages if not deleted")
	@GetMapping("/audit-log/chat-detail")
	public Result<Object> getAuditChatDetail(
			@RequestParam String email,
			@RequestParam String conversationId) {
		Map<String, Object> result = new HashMap<>();

		// Get the chat messages (if conversation still exists)
		List<Map<String, Object>> messages = jdbcTemplate.queryForList(
			"SELECT role, content, created_at FROM chat_history WHERE user_email = ? AND conversation_id = ? ORDER BY created_at ASC",
			email, conversationId);

		if (messages.isEmpty()) {
			result.put("available", false);
			result.put("message", "Chat history not available — conversation may have been deleted by the user.");
			result.put("messages", List.of());
		}
		else {
			result.put("available", true);
			result.put("messages", messages);
			// Get the app info from audit log
			List<Map<String, Object>> appInfo = jdbcTemplate.queryForList(
				"SELECT DISTINCT al.resource_id AS app_id, a.name AS app_name FROM audit_log al " +
				"LEFT JOIN application a ON a.app_id = al.resource_id " +
				"WHERE al.user_email = ? AND al.details LIKE ? AND al.action = 'CHAT_MESSAGE'",
				email, "conversation=" + conversationId + "%");
			if (!appInfo.isEmpty()) {
				result.put("app", appInfo.get(0));
			}
		}
		result.put("conversationId", conversationId);

		// Add RAG retrieval data: which specific documents were pulled during this conversation
		List<Map<String, Object>> ragEntries = jdbcTemplate.queryForList(
			"SELECT details, created_at FROM audit_log " +
			"WHERE user_email = ? AND action = 'RAG_RETRIEVAL' AND details LIKE ? " +
			"ORDER BY created_at ASC",
			email, "conversation=" + conversationId + "%");
		List<Map<String, Object>> ragRetrievals = new ArrayList<>();
		for (Map<String, Object> entry : ragEntries) {
			String details = (String) entry.get("details");
			if (details != null && details.contains("rag_docs=")) {
				Map<String, Object> retrieval = new HashMap<>();
				retrieval.put("retrieved_at", entry.get("created_at"));
				String docsJson = details.substring(details.indexOf("rag_docs=") + 9);
				retrieval.put("docs_json", docsJson);
				ragRetrievals.add(retrieval);
			}
		}
		result.put("ragRetrievals", ragRetrievals);

		return Result.success(result);
	}

	@Operation(summary = "Generate BCrypt hash (debug only)")
	@GetMapping("/hash")
	public Result<String> generateHash(@RequestParam String password) {
		return Result.success(passwordEncoder.encode(password));
	}

	// Chat Proxy

	/**
	 * Proxy endpoint for chat completions that bypasses normal auth.
	 * Sets up RequestContext with the admin service account and delegates to AgentService.
	 * Extracts the chatbot user's email from the JWT to enable ACL-filtered RAG retrieval.
	 */
	@PostMapping(value = "/chat/completions")
	public Object chatCompletions(@RequestBody AgentRequest request,
			HttpServletRequest httpRequest, HttpServletResponse response) {
		log.info("Chatbot chat proxy request for app: {}", request.getAppId());

		// Extract the real chatbot user's email from JWT for ACL filtering
		String userEmail = extractChatbotUserEmail(httpRequest);

		// Set up RequestContext since this endpoint bypasses the auth interceptor
		RequestContext context = new RequestContext();
		context.setRequestId(IdGenerator.uuid());
		context.setAccountId(CHATBOT_ACCOUNT_ID);
		// Use real user email for ACL-filtered RAG, fallback to service account
		context.setUsername(userEmail != null ? userEmail : "chatbot-service");
		context.setWorkspaceId(CHATBOT_WORKSPACE_ID);
		context.setStartTime(System.currentTimeMillis());

		// Resolve user groups for ACL-filtered RAG chunk retrieval
		if (userEmail != null) {
			List<String> kbIds = resolveKbIds(request.getAppId());
			if (!kbIds.isEmpty()) {
				Set<String> userGroups = resolveUserGroups(userEmail, kbIds);
				context.setUserGroups(userGroups);
				log.info("Chatbot ACL: user={}, groups={}", userEmail, userGroups);
			}
		}

		RequestContextHolder.setRequestContext(context);

		// Use draft=true to match console behavior (can use unpublished apps)
		request.setDraft(true);

		if (request.getStream() != null && request.getStream()) {
			response.addHeader("X-Accel-Buffering", "no");
			response.addHeader(HttpHeaders.CONTENT_TYPE, MediaType.TEXT_EVENT_STREAM_VALUE);

			SseEmitter emitter = new SseEmitter(600_000L); // 10-minute timeout
			Flux<AgentResponse> responseFlux = agentService.streamCall(Flux.just(request));

			AtomicBoolean done = new AtomicBoolean(false);
			AtomicReference<Disposable> subRef = new AtomicReference<>();

			Runnable cleanup = () -> {
				if (done.compareAndSet(false, true)) {
					Disposable sub = subRef.get();
					if (sub != null && !sub.isDisposed()) {
						sub.dispose();
					}
				}
			};

			emitter.onCompletion(cleanup::run);
			emitter.onTimeout(() -> {
				log.warn("SSE emitter timeout for app: {}", request.getAppId());
				cleanup.run();
			});
			emitter.onError(t -> {
				log.warn("SSE emitter error for app {}: {}", request.getAppId(), t.getMessage());
				cleanup.run();
			});

			Disposable subscription = responseFlux.subscribe(
				data -> {
					if (done.get()) return;
					try {
						String json = JsonUtils.toJson(data);
						emitter.send(json, MediaType.TEXT_EVENT_STREAM);
					}
					catch (Exception e) {
						log.warn("SSE send failed for app {}, cleaning up: {}", request.getAppId(), e.getMessage());
						cleanup.run();
					}
				},
				err -> {
					log.error("Chat stream error for app {}: {}", request.getAppId(), err.getMessage());
					if (!done.get()) {
						try {
							String errorJson = "{\"error\":\"" + err.getMessage().replace("\"", "'") + "\"}";
							emitter.send(errorJson, MediaType.TEXT_EVENT_STREAM);
						}
						catch (Exception ignored) {
						}
						try {
							emitter.complete();
						}
						catch (Exception ignored) {
						}
					}
					cleanup.run();
				},
				() -> {
					if (!done.get()) {
						try {
							emitter.complete();
						}
						catch (Exception ignored) {
						}
					}
					cleanup.run();
				}
			);
			subRef.set(subscription);

			return emitter;
		}

		try {
			// Non-streaming
			AgentResponse completion = agentService.call(request);
			return JsonUtils.toJson(completion);
		}
		catch (Exception e) {
			log.error("Chat completion error", e);
			response.setStatus(500);
			return "{\"error\":\"" + e.getMessage() + "\"}";
		}
	}

	// ==================== ACL-Filtered Document Search ====================

	/**
	 * Resolve the knowledge base IDs from an app's configuration.
	 * Returns the kb_ids from file_search config in application_version.
	 */
	private List<String> resolveKbIds(String appId) {
		try {
			List<Map<String, Object>> versions = jdbcTemplate.queryForList(
				"SELECT config FROM application_version WHERE app_id = ? ORDER BY version DESC LIMIT 1", appId);
			if (versions.isEmpty()) {
				return List.of();
			}
			String config = (String) versions.get(0).get("config");
			Map<String, Object> configMap = objectMapper.readValue(config, new TypeReference<>() {});
			@SuppressWarnings("unchecked")
			Map<String, Object> fileSearch = (Map<String, Object>) configMap.get("file_search");
			if (fileSearch == null) {
				return List.of();
			}
			@SuppressWarnings("unchecked")
			List<String> kbIds = (List<String>) fileSearch.get("kb_ids");
			return kbIds != null ? kbIds : List.of();
		}
		catch (Exception e) {
			log.error("Failed to resolve kb_ids for app {}", appId, e);
			return List.of();
		}
	}

	/**
	 * Execute a raw OpenSearch query and return the parsed response.
	 */
	private Map<String, Object> executeOpenSearchQuery(String index, String queryJson) throws Exception {
		Request request = new Request("POST", "/" + index + "/_search");
		request.setEntity(new StringEntity(queryJson, ContentType.APPLICATION_JSON));
		Response response = openSearchRestClient.performRequest(request);
		try (InputStream is = response.getEntity().getContent()) {
			return objectMapper.readValue(is, new TypeReference<>() {});
		}
	}

	/**
	 * Resolve the groups the user belongs to by querying the {kbId}_authority index.
	 * Looks up the user by principal_id (email), then returns all group identifiers
	 * from the "member_of" field. These are used to enrich ACL queries so that
	 * documents accessible via group membership are visible.
	 *
	 * @param email  the user's email (lowercased)
	 * @param kbIds  the knowledge base IDs to search
	 * @return set of group tokens (lowercased) the user is a member of
	 */
	private Set<String> resolveUserGroups(String email, List<String> kbIds) {
		Set<String> groups = new LinkedHashSet<>();
		String lowerEmail = email.toLowerCase();

		for (String kbId : kbIds) {
			String index = kbId + "_authority";
			try {
				// Search for the user by principal_id (exact match)
				Map<String, Object> query = Map.of(
					"query", Map.of("bool", Map.of(
						"must", List.of(
							Map.of("term", Map.of("principal_id", lowerEmail))
						)
					)),
					"size", 1,
					"_source", List.of("member_of")
				);
				String queryJson = objectMapper.writeValueAsString(query);
				Map<String, Object> result = executeOpenSearchQuery(index, queryJson);

				@SuppressWarnings("unchecked")
				Map<String, Object> hits = (Map<String, Object>) result.get("hits");
				@SuppressWarnings("unchecked")
				List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");

				for (Map<String, Object> hit : hitList) {
					@SuppressWarnings("unchecked")
					Map<String, Object> source = (Map<String, Object>) hit.get("_source");
					@SuppressWarnings("unchecked")
					List<String> memberOf = (List<String>) source.getOrDefault("member_of", List.of());
					for (String group : memberOf) {
						groups.add(group.toLowerCase());
					}
				}
			}
			catch (Exception e) {
				log.debug("Could not resolve groups from authority index {}: {}", index, e.getMessage());
			}
		}
		log.debug("Resolved {} groups for user {}: {}", groups.size(), lowerEmail, groups);
		return groups;
	}

	/**
	 * Build ACL filter clause for OpenSearch queries.
	 * Checks allow_token_document contains the user OR any of the user's groups,
	 * AND deny_token_document does NOT contain the user.
	 *
	 * @param username   the user's email (lowercased)
	 * @param userGroups the groups the user belongs to (from resolveUserGroups)
	 */
	private Map<String, Object> buildAclFilter(String username, Set<String> userGroups) {
		String lowerUser = username.toLowerCase();

		// Build "should" clauses: user email + __nosecurity__ + all user groups
		List<Object> shouldClauses = new ArrayList<>();
		shouldClauses.add(Map.of("term", Map.of("allow_token_document", lowerUser)));
		shouldClauses.add(Map.of("term", Map.of("allow_token_document", "__nosecurity__")));
		for (String group : userGroups) {
			shouldClauses.add(Map.of("term", Map.of("allow_token_document", group)));
		}

		// Build "must_not" clauses: deny for user email + all user groups
		List<Object> mustNotClauses = new ArrayList<>();
		mustNotClauses.add(Map.of("term", Map.of("deny_token_document", lowerUser)));
		for (String group : userGroups) {
			mustNotClauses.add(Map.of("term", Map.of("deny_token_document", group)));
		}

		Map<String, Object> aclFilter = new LinkedHashMap<>();
		aclFilter.put("bool", Map.of(
			"should", shouldClauses,
			"minimum_should_match", 1,
			"must_not", mustNotClauses
		));
		return aclFilter;
	}

	/**
	 * Build ACL filter for RAG chunks (uses 'authorities' field).
	 * Includes the user's email AND all groups they belong to.
	 *
	 * @param username   the user's email (lowercased)
	 * @param userGroups the groups the user belongs to (from resolveUserGroups)
	 */
	private Map<String, Object> buildRagAclFilter(String username, Set<String> userGroups) {
		String lowerUser = username.toLowerCase();

		List<Object> shouldClauses = new ArrayList<>();
		shouldClauses.add(Map.of("term", Map.of("authorities", lowerUser)));
		shouldClauses.add(Map.of("term", Map.of("authorities", "__nosecurity__")));
		for (String group : userGroups) {
			shouldClauses.add(Map.of("term", Map.of("authorities", group)));
		}

		Map<String, Object> aclFilter = new LinkedHashMap<>();
		aclFilter.put("bool", Map.of(
			"should", shouldClauses,
			"minimum_should_match", 1
		));
		return aclFilter;
	}

	/**
	 * Extract the chatbot user's email from the JWT Authorization header.
	 * Returns null if the header is missing or the token is invalid.
	 */
	private String extractChatbotUserEmail(HttpServletRequest request) {
		String authHeader = request.getHeader("Authorization");
		if (authHeader == null || !authHeader.startsWith("Bearer ")) {
			return null;
		}
		try {
			String token = authHeader.substring(7);
			return Jwts.parser()
				.verifyWith(secretKey)
				.build()
				.parseSignedClaims(token)
				.getPayload()
				.getSubject();
		}
		catch (Exception e) {
			log.warn("Failed to extract email from chatbot JWT: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * ACL-filtered document listing with fulltext/metadata search.
	 * Returns documents the user is authorized to see.
	 */
	@Operation(summary = "List documents with ACL filter and optional search")
	@GetMapping("/documents")
	public Result<Map<String, Object>> searchDocuments(
			@RequestParam String email,
			@RequestParam String appId,
			@RequestParam(required = false) String query,
			@RequestParam(defaultValue = "0") int from,
			@RequestParam(defaultValue = "20") int size,
			@RequestParam(required = false) String sortField,
			@RequestParam(defaultValue = "desc") String sortOrder,
			@RequestParam(required = false) String mimeType,
			@RequestParam(required = false) String createdBy,
			@RequestParam(required = false) String dateRange,
			@RequestParam(required = false) String sizeRange,
			@RequestParam(required = false) String status,
			@RequestParam(required = false) String classification) {
		try {
			List<String> kbIds = resolveKbIds(appId);
			if (kbIds.isEmpty()) {
				return Result.error(400, "No knowledge base configured for this app");
			}

			String lowerEmail = email.toLowerCase();
			Set<String> userGroups = resolveUserGroups(lowerEmail, kbIds);
			Map<String, Object> aclFilter = buildAclFilter(lowerEmail, userGroups);

			// Build query
			List<Object> mustClauses = new ArrayList<>();
			List<Object> filterClauses = new ArrayList<>();
			filterClauses.add(aclFilter);

			// Fulltext search across content, filenames, metadata, and CMIS fields
			if (query != null && !query.isBlank()) {
				mustClauses.add(Map.of("multi_match", Map.of(
					"query", query,
					"fields", List.of("content^2", "cmis:contentStreamFileName^3", "cmis:name^3", "file_title^2",
							"cmis:description^1", "cmis:createdBy^1", "file_name^2"),
					"type", "best_fields",
					"fuzziness", "AUTO"
				)));
			}

			// Metadata filters
			if (mimeType != null && !mimeType.isBlank()) {
				filterClauses.add(Map.of("term", Map.of("mime-type.keyword", mimeType)));
			}
			if (createdBy != null && !createdBy.isBlank()) {
				filterClauses.add(Map.of("term", Map.of("cmis:createdBy.keyword", createdBy)));
			}
			if (status != null && !status.isBlank()) {
				filterClauses.add(Map.of("term", Map.of("digi:status.keyword", status)));
			}
			if (classification != null && !classification.isBlank()) {
				filterClauses.add(Map.of("term", Map.of("sc:classification.keyword", classification)));
			}

			// Date range filter
			if (dateRange != null && !dateRange.isBlank()) {
				java.time.Instant now = java.time.Instant.now();
				String gte = null;
				switch (dateRange) {
					case "today" -> gte = now.truncatedTo(java.time.temporal.ChronoUnit.DAYS).toString();
					case "week" -> gte = now.minus(7, java.time.temporal.ChronoUnit.DAYS).toString();
					case "month" -> gte = now.minus(30, java.time.temporal.ChronoUnit.DAYS).toString();
					case "quarter" -> gte = now.minus(90, java.time.temporal.ChronoUnit.DAYS).toString();
					case "year" -> gte = now.minus(365, java.time.temporal.ChronoUnit.DAYS).toString();
					case "older" -> { /* gte stays null = no lower bound; we add lte instead */ }
				}
				if ("older".equals(dateRange)) {
					String lte = now.minus(365, java.time.temporal.ChronoUnit.DAYS).toString();
					filterClauses.add(Map.of("range", Map.of("cmis:lastModificationDate", Map.of("lte", lte))));
				} else if (gte != null) {
					filterClauses.add(Map.of("range", Map.of("cmis:lastModificationDate", Map.of("gte", gte))));
				}
			}

			// File size range filter (cmis:contentStreamLength is text, so we use script filter)
			if (sizeRange != null && !sizeRange.isBlank()) {
				long minBytes = 0, maxBytes = Long.MAX_VALUE;
				switch (sizeRange) {
					case "small" -> maxBytes = 1048576; // <1MB
					case "medium" -> { minBytes = 1048576; maxBytes = 10485760; } // 1-10MB
					case "large" -> minBytes = 10485760; // >10MB
				}
				String script = "long s = 0; try { s = Long.parseLong(doc['cmis:contentStreamLength.keyword'].value); } catch(Exception e) {} return s >= " + minBytes + "L && s <= " + maxBytes + "L";
				filterClauses.add(Map.of("script", Map.of("script", Map.of("source", script, "lang", "painless"))));
			}

			Map<String, Object> boolQuery = new LinkedHashMap<>();
			if (!mustClauses.isEmpty()) {
				boolQuery.put("must", mustClauses);
			}
			if (!filterClauses.isEmpty()) {
				boolQuery.put("filter", filterClauses);
			}
			if (mustClauses.isEmpty() && filterClauses.size() == 1) {
				// Only ACL filter, no search query - match all with filter
				boolQuery.put("must", List.of(Map.of("match_all", Map.of())));
			}

			// Build sort
			List<Object> sort = new ArrayList<>();
			if (sortField != null && !sortField.isBlank()) {
				sort.add(Map.of(sortField, Map.of("order", sortOrder)));
			}
			else {
				sort.add(Map.of("cmis:creationDate", Map.of("order", "desc", "unmapped_type", "date")));
			}

			// Aggregations for facets
			Map<String, Object> aggs = new LinkedHashMap<>();
			aggs.put("mime_types", Map.of("terms", Map.of("field", "mime-type.keyword", "size", 20)));
			aggs.put("created_by", Map.of("terms", Map.of("field", "cmis:createdBy.keyword", "size", 20)));
			aggs.put("document_status", Map.of("terms", Map.of("field", "digi:status.keyword", "size", 20)));
			aggs.put("classification", Map.of("terms", Map.of("field", "sc:classification.keyword", "size", 20)));
			// Date histogram for date modified facets
			aggs.put("date_modified", Map.of("date_range", Map.of(
				"field", "cmis:lastModificationDate",
				"ranges", List.of(
					Map.of("key", "today", "from", "now/d"),
					Map.of("key", "week", "from", "now-7d/d"),
					Map.of("key", "month", "from", "now-30d/d"),
					Map.of("key", "quarter", "from", "now-90d/d"),
					Map.of("key", "year", "from", "now-365d/d"),
					Map.of("key", "older", "to", "now-365d/d")
				)
			)));

			Map<String, Object> requestBody = new LinkedHashMap<>();
			requestBody.put("query", Map.of("bool", boolQuery));
			requestBody.put("from", from);
			requestBody.put("size", size);
			requestBody.put("sort", sort);
			requestBody.put("aggs", aggs);
			requestBody.put("_source", Map.of("excludes", List.of("content")));

			// Search across all KB indices
			List<Map<String, Object>> allDocs = new ArrayList<>();
			long totalHits = 0;
			Map<String, Object> facets = new HashMap<>();

			for (String kbId : kbIds) {
				String index = kbId + "_document";
				String queryJson = objectMapper.writeValueAsString(requestBody);
				log.debug("OpenSearch document query on {}: {}", index, queryJson);

				Map<String, Object> result = executeOpenSearchQuery(index, queryJson);

				@SuppressWarnings("unchecked")
				Map<String, Object> hits = (Map<String, Object>) result.get("hits");
				@SuppressWarnings("unchecked")
				Map<String, Object> total = (Map<String, Object>) hits.get("total");
				totalHits += ((Number) total.get("value")).longValue();

				@SuppressWarnings("unchecked")
				List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
				for (Map<String, Object> hit : hitList) {
					@SuppressWarnings("unchecked")
					Map<String, Object> source = (Map<String, Object>) hit.get("_source");
					Map<String, Object> doc = new LinkedHashMap<>();
					doc.put("id", hit.get("_id"));
					doc.put("kbId", kbId);
					doc.put("fileName", source.getOrDefault("cmis:contentStreamFileName",
							source.getOrDefault("cmis:name", "Unknown")));
					doc.put("mimeType", source.getOrDefault("mime-type",
							source.getOrDefault("cmis:contentStreamMimeType", "")));
					doc.put("fileSize", source.getOrDefault("cmis:contentStreamLength", 0));
					doc.put("createdBy", source.getOrDefault("cmis:createdBy", ""));
					doc.put("createdDate", source.getOrDefault("cmis:creationDate", ""));
					doc.put("lastModified", source.getOrDefault("cmis:lastModificationDate", ""));
					doc.put("objectId", source.getOrDefault("cmis:objectId",
							source.getOrDefault("url", "")));
					doc.put("score", hit.get("_score"));
					// Extract contentPath from url field (e.g. …?contentPath=/Sites/…/file.ext)
					String urlField = String.valueOf(source.getOrDefault("url", ""));
					String contentPath = "";
					if (urlField.contains("contentPath=")) {
						try {
							String raw = urlField.substring(urlField.indexOf("contentPath=") + "contentPath=".length());
							if (raw.contains("&")) raw = raw.substring(0, raw.indexOf("&"));
							contentPath = java.net.URLDecoder.decode(raw, java.nio.charset.StandardCharsets.UTF_8);
						} catch (Exception ignored) {}
					}
					doc.put("contentPath", contentPath);
					// Include all remaining metadata for detailed view
					Map<String, Object> metadata = new LinkedHashMap<>(source);
					// Remove ACL fields from response (security: don't expose ACL internals)
					metadata.remove("allow_token_document");
					metadata.remove("deny_token_document");
					metadata.remove("allow_token_parent");
					metadata.remove("deny_token_parent");
					metadata.remove("allow_token_share");
					metadata.remove("deny_token_share");
					metadata.remove("authorities");
					// Remove processing/internal metadata — keep only source system metadata
					// Use prefix-based filtering for Tika-added fields
					metadata.keySet().removeIf(key -> {
						String k = key.toLowerCase();
						// Remove Tika processing metadata prefixes
						if (k.startsWith("pdf:") || k.startsWith("access_permission:")
								|| k.startsWith("dc:") || k.startsWith("dcterms:")
								|| k.startsWith("xmp:") || k.startsWith("xmptpg:")
								|| k.startsWith("meta:")) {
							return true;
						}
						// Remove ManifoldCF internal / RAG pipeline fields
						return Set
							.of("content", "indexed", "created", "last-modified", "url", "workspace_id", "enabled",
									"total_chunks", "doc_name", "doc_id", "chunk_index", "file_title", "file_name",
									"file_path", "file_size", "file_type", "embedding", "date", "modified",
									"last-save-date", "creation-date", "language", "producer", "hassignature",
									"mime-type")
							.contains(k);
					});
					doc.put("metadata", metadata);
					allDocs.add(doc);
				}

				// Extract facets from aggregations
				@SuppressWarnings("unchecked")
				Map<String, Object> aggregations = (Map<String, Object>) result.get("aggregations");
				if (aggregations != null) {
					facets = extractFacets(aggregations);
				}
			}

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("documents", allDocs);
			response.put("total", totalHits);
			response.put("from", from);
			response.put("size", size);
			response.put("facets", facets);
			// Include the resolved user identity and groups used for ACL filtering
			response.put("username", lowerEmail);
			response.put("userGroups", userGroups);

			return Result.success(response);
		}
		catch (Exception e) {
			log.error("Document search failed for user {}", email, e);
			return Result.error(500, "Document search failed: " + e.getMessage());
		}
	}

	/**
	 * ACL-filtered RAG chunk search.
	 * Filters chunks by authorities field BEFORE performing fulltext search.
	 */
	@Operation(summary = "Search RAG chunks with ACL filter")
	@GetMapping("/rag-search")
	public Result<Map<String, Object>> searchRagChunks(
			@RequestParam String email,
			@RequestParam String appId,
			@RequestParam String query,
			@RequestParam(defaultValue = "10") int size) {
		try {
			List<String> kbIds = resolveKbIds(appId);
			if (kbIds.isEmpty()) {
				return Result.error(400, "No knowledge base configured for this app");
			}

			String lowerEmail = email.toLowerCase();
			Set<String> userGroups = resolveUserGroups(lowerEmail, kbIds);
			Map<String, Object> aclFilter = buildRagAclFilter(lowerEmail, userGroups);

			// Fulltext search on RAG chunks with ACL filter
			// Search content, file title, AND metadata fields (file_name, created_by, mime_type, object_id)
			Map<String, Object> boolQuery = Map.of(
				"must", List.of(
					Map.of("multi_match", Map.of(
						"query", query,
						"fields", List.of("content^2", "file_title^2", "metadata.file_name^3", "metadata.created_by^1", "metadata.mime_type^0.5", "metadata.object_id^0.5"),
						"type", "best_fields",
						"fuzziness", "AUTO"
					))
				),
				"filter", List.of(aclFilter)
			);

			Map<String, Object> requestBody = new LinkedHashMap<>();
			requestBody.put("query", Map.of("bool", boolQuery));
			requestBody.put("size", size);
			requestBody.put("_source", Map.of("excludes", List.of("embedding")));

			List<Map<String, Object>> chunks = new ArrayList<>();
			long totalHits = 0;

			for (String kbId : kbIds) {
				String index = kbId + "_rag";
				String queryJson = objectMapper.writeValueAsString(requestBody);
				log.debug("OpenSearch RAG query on {}: {}", index, queryJson);

				Map<String, Object> result = executeOpenSearchQuery(index, queryJson);

				@SuppressWarnings("unchecked")
				Map<String, Object> hits = (Map<String, Object>) result.get("hits");
				@SuppressWarnings("unchecked")
				Map<String, Object> total = (Map<String, Object>) hits.get("total");
				totalHits += ((Number) total.get("value")).longValue();

				@SuppressWarnings("unchecked")
				List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
				for (Map<String, Object> hit : hitList) {
					@SuppressWarnings("unchecked")
					Map<String, Object> source = (Map<String, Object>) hit.get("_source");
					Map<String, Object> chunk = new LinkedHashMap<>();
					chunk.put("chunkId", source.getOrDefault("chunk_id", hit.get("_id")));
					chunk.put("docId", source.getOrDefault("doc_id", ""));
					chunk.put("fileTitle", source.getOrDefault("file_title", ""));
					chunk.put("content", source.getOrDefault("content", ""));
					chunk.put("chunkIndex", source.getOrDefault("chunk_index", 0));
					chunk.put("score", hit.get("_score"));
					@SuppressWarnings("unchecked")
					Map<String, Object> rawMeta = (Map<String, Object>) source.getOrDefault("metadata", Map.of());
					// Filter out processing metadata, keep only source system metadata
					Map<String, Object> meta = new LinkedHashMap<>();
					for (Map.Entry<String, Object> entry : rawMeta.entrySet()) {
						String key = entry.getKey();
						// Skip processing/internal fields
						if ("workspace_id".equals(key) || "enabled".equals(key) || "total_chunks".equals(key)
								|| "doc_name".equals(key) || "doc_id".equals(key) || "chunk_index".equals(key)) {
							continue;
						}
						meta.put(key, entry.getValue());
					}
					chunk.put("metadata", meta);
					chunks.add(chunk);
				}
			}

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("chunks", chunks);
			response.put("total", totalHits);
			response.put("query", query);
			// Include the resolved user identity and groups used for ACL filtering
			response.put("username", lowerEmail);
			response.put("userGroups", userGroups);

			return Result.success(response);
		}
		catch (Exception e) {
			log.error("RAG search failed for user {}", email, e);
			return Result.error(500, "RAG search failed: " + e.getMessage());
		}
	}

	/**
	 * Extract facet data from OpenSearch aggregations.
	 */
	@SuppressWarnings("unchecked")
	private Map<String, Object> extractFacets(Map<String, Object> aggregations) {
		Map<String, Object> facets = new LinkedHashMap<>();
		// Label map for date range buckets
		Map<String, String> dateLabels = Map.of(
			"today", "Today", "week", "This Week", "month", "This Month",
			"quarter", "This Quarter", "year", "This Year", "older", "Older"
		);
		for (Map.Entry<String, Object> entry : aggregations.entrySet()) {
			Map<String, Object> agg = (Map<String, Object>) entry.getValue();
			List<Map<String, Object>> buckets = (List<Map<String, Object>>) agg.get("buckets");
			if (buckets != null) {
				List<Map<String, Object>> facetItems = new ArrayList<>();
				for (Map<String, Object> bucket : buckets) {
					long count = ((Number) bucket.get("doc_count")).longValue();
					if (count == 0) continue; // Skip empty buckets
					Map<String, Object> item = new LinkedHashMap<>();
					item.put("value", bucket.get("key"));
					item.put("count", count);
					// Add label for date range buckets
					if ("date_modified".equals(entry.getKey())) {
						String key = String.valueOf(bucket.get("key"));
						item.put("label", dateLabels.getOrDefault(key, key));
					}
					facetItems.add(item);
				}
				if (!facetItems.isEmpty()) {
					facets.put(entry.getKey(), facetItems);
				}
			}
		}
		return facets;
	}

	// ==================== Document Download (for Viewer) ====================

	/**
	 * Resolve a sync ID from a knowledge base ID.
	 * Finds the most recent sync job for the given KB.
	 */
	private String resolveSyncId(String kbId) {
		try {
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT sync_id FROM knowledge_sync WHERE kb_id = ? ORDER BY gmt_modified DESC LIMIT 1", kbId);
			if (!rows.isEmpty()) {
				return String.valueOf(rows.get(0).get("sync_id"));
			}
		}
		catch (Exception e) {
			log.error("Failed to resolve syncId for kbId {}: {}", kbId, e.getMessage());
		}
		return null;
	}

	/**
	 * Download a source document for the chatbot document viewer.
	 * The document is fetched from the source system (e.g., CMIS/Alfresco) via the sync service.
	 * Requires kbId + docId (the CMIS content stream URL stored as the document's objectId).
	 */
	@Operation(summary = "Download source document for viewer")
	@GetMapping("/document/download")
	public void downloadDocument(
			@RequestParam String kbId,
			@RequestParam String docId,
			HttpServletRequest request,
			HttpServletResponse response) {
		try {
			// Verify user is authenticated
			String email = extractChatbotUserEmail(request);
			if (email == null) {
				response.setStatus(401);
				return;
			}

			// Resolve syncId from kbId
			String syncId = resolveSyncId(kbId);
			if (syncId == null) {
				response.setStatus(404);
				response.getWriter().write("No sync job found for knowledge base");
				return;
			}

			// Download from source via KnowledgeSyncService
			Map<String, Object> download = knowledgeSyncService.downloadSourceDocument(syncId, docId);
			byte[] content = (byte[]) download.get("content");
			String contentType = (String) download.getOrDefault("contentType", "application/octet-stream");
			String fileName = (String) download.getOrDefault("fileName", "document");

			response.setContentType(contentType);
			response.setContentLengthLong(content.length);

			// Allow CORS for the viewer (inline display, not download)
			response.setHeader("Content-Disposition", "inline; filename=\"" + fileName + "\"");

			try (var out = response.getOutputStream()) {
				out.write(content);
				out.flush();
			}

			log.info("Chatbot document download: user={}, kbId={}, file={}, size={}", email, kbId, fileName, content.length);
		}
		catch (Exception e) {
			log.error("Document download failed: kbId={}, docId={}", kbId, docId, e);
			try {
				response.setStatus(500);
				response.getWriter().write("Download failed: " + e.getMessage());
			}
			catch (Exception ignored) {}
		}
	}

	// ==================== Source System Navigation ====================

	/**
	 * Get the source system preview URL(s) for an app.
	 * Resolves appId -> kbIds -> syncJobs -> sourceIds -> connection_config.previewUrl.
	 */
	@Operation(summary = "Get source system preview URLs for an app")
	@GetMapping("/source-preview-url")
	public Result<Map<String, Object>> getSourcePreviewUrl(
			@RequestParam String appId,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) {
				return Result.error(401, "Unauthorized");
			}

			List<String> kbIds = resolveKbIds(appId);
			if (kbIds.isEmpty()) {
				return Result.error(400, "No knowledge base configured for this app");
			}

			List<Map<String, Object>> sources = new ArrayList<>();
			for (String kbId : kbIds) {
				try {
					// Get source_id from knowledge_sync table
					List<Map<String, Object>> syncRows = jdbcTemplate.queryForList(
						"SELECT source_id FROM knowledge_sync WHERE kb_id = ? AND source_id IS NOT NULL ORDER BY gmt_modified DESC LIMIT 1", kbId);
					if (syncRows.isEmpty()) continue;
					String sourceId = String.valueOf(syncRows.get(0).get("source_id"));
					if (sourceId.isBlank() || "null".equals(sourceId)) continue;

					// Get source system connection config
					List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList(
						"SELECT name, connector_type, connection_config FROM source_system WHERE source_id = ?", sourceId);
					if (sourceRows.isEmpty()) continue;

					String name = (String) sourceRows.get(0).get("name");
					String connectorType = (String) sourceRows.get(0).get("connector_type");
					String configJson = (String) sourceRows.get(0).get("connection_config");
					Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});
					String previewUrl = (String) config.getOrDefault("previewUrl", "");

					// If no previewUrl is configured, try to build one from server/protocol/port
					if (previewUrl == null || previewUrl.isBlank()) {
						String protocol = (String) config.getOrDefault("protocol",
							config.getOrDefault("PROTOCOL", "https"));
						String server = (String) config.getOrDefault("server",
							config.getOrDefault("SERVER", ""));
						String port = (String) config.getOrDefault("port",
							config.getOrDefault("PORT", "443"));
						if (server != null && !server.isBlank()) {
							previewUrl = protocol + "://" + server;
							if (!"443".equals(port) && !"80".equals(port)) {
								previewUrl += ":" + port;
							}
						}
					}

					Map<String, Object> sourceInfo = new LinkedHashMap<>();
					sourceInfo.put("sourceId", sourceId);
					sourceInfo.put("name", name);
					sourceInfo.put("connectorType", connectorType);
					sourceInfo.put("previewUrl", previewUrl != null ? previewUrl : "");
					sourceInfo.put("kbId", kbId);
					// Feature flags — whether "Navigate to Source" / "Browse in Source" are enabled
					sourceInfo.put("navigateToSourceEnabled",
						"true".equals(config.getOrDefault("navigateToSourceEnabled", "false")));
					sourceInfo.put("browseInSourceEnabled",
						"true".equals(config.getOrDefault("browseInSourceEnabled", "false")));
					sources.add(sourceInfo);
				}
				catch (Exception e) {
					log.warn("Failed to resolve source preview URL for kbId {}: {}", kbId, e.getMessage());
				}
			}

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("sources", sources);
			return Result.success(response);
		}
		catch (Exception e) {
			log.error("Failed to get source preview URLs for app {}", appId, e);
			return Result.error(500, "Failed to get source URLs: " + e.getMessage());
		}
	}

	/**
	 * Get the URL in the source system where a document can be viewed.
	 * Uses the vendor-agnostic documentUrlTemplate from connection_config.
	 * Template placeholders: {nodeId}, {objectId}, {protocol}, {server}, {port}, {path}, {fileName}
	 * Example (Alfresco): {protocol}://{server}:{port}/share/page/document-details?nodeRef=workspace://SpacesStore/{nodeId}
	 */
	@Operation(summary = "Get source system URL for a document")
	@GetMapping("/document-source-url")
	public Result<Map<String, Object>> getDocumentSourceUrl(
			@RequestParam String email,
			@RequestParam String appId,
			@RequestParam String objectId,
			@RequestParam(required = false) String kbId) {
		try {

			// Resolve which KB to use
			String targetKbId = kbId;
			if (targetKbId == null || targetKbId.isBlank()) {
				List<String> kbIds = resolveKbIds(appId);
				if (!kbIds.isEmpty()) targetKbId = kbIds.get(0);
			}
			if (targetKbId == null) return Result.error(400, "No knowledge base found");

			// Get source system config via sync
			String syncId = resolveSyncId(targetKbId);
			if (syncId == null) return Result.error(400, "No sync job found");

			List<Map<String, Object>> syncRows = jdbcTemplate.queryForList(
				"SELECT source_id FROM knowledge_sync WHERE sync_id = ?", syncId);
			if (syncRows.isEmpty()) return Result.error(400, "No source found");
			String sourceId = String.valueOf(syncRows.get(0).get("source_id"));

			List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList(
				"SELECT connector_type, connection_config FROM source_system WHERE source_id = ?", sourceId);
			if (sourceRows.isEmpty()) return Result.error(400, "Source system not found");

			String configJson = (String) sourceRows.get(0).get("connection_config");
			Map<String, Object> config = objectMapper.readValue(configJson, new com.fasterxml.jackson.core.type.TypeReference<>() {});

			String protocol = String.valueOf(config.getOrDefault("protocol",
					config.getOrDefault("PROTOCOL", "https")));
			String server = String.valueOf(config.getOrDefault("server",
					config.getOrDefault("SERVER", "")));
			String port = String.valueOf(config.getOrDefault("port",
					config.getOrDefault("PORT", "443")));

			// Extract the node ID from the objectId (strip version suffix like ";1.0")
			String nodeId = objectId;
			if (nodeId.contains(";")) {
				nodeId = nodeId.substring(0, nodeId.indexOf(";"));
			}

			// Resolve documentUrlTemplate from connection_config (vendor-agnostic)
			String template = (String) config.getOrDefault("documentUrlTemplate", "");
			String sourceUrl;
			if (template != null && !template.isBlank()) {
				sourceUrl = template
						.replace("{protocol}", protocol)
						.replace("{server}", server)
						.replace("{port}", port)
						.replace("{nodeId}", nodeId)
						.replace("{objectId}", objectId)
						.replace("{path}", String.valueOf(config.getOrDefault("path",
								config.getOrDefault("BASEPATH", ""))))
						.replace("{fileName}", "");
				// Strip default port suffixes for cleaner URLs
				sourceUrl = sourceUrl.replace(":443/", "/").replace(":80/", "/");
			} else {
				// Fallback: use previewUrl or base URL
				String previewUrl = (String) config.getOrDefault("previewUrl", "");
				if (previewUrl != null && !previewUrl.isBlank()) {
					sourceUrl = previewUrl;
				} else {
					String baseUrl = protocol + "://" + server;
					if (!"443".equals(port) && !"80".equals(port)) {
						baseUrl += ":" + port;
					}
					sourceUrl = baseUrl;
				}
			}

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("sourceUrl", sourceUrl);
			result.put("baseUrl", protocol + "://" + server + (!"443".equals(port) && !"80".equals(port) ? ":" + port : ""));
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("Failed to build document source URL: appId={}, objectId={}", appId, objectId, e);
			return Result.error(500, "Failed to build source URL: " + e.getMessage());
		}
	}

	// ==================== Sample Source URLs from Real Documents ====================

	/**
	 * Query OpenSearch for real documents and generate sample "Browse in Source" URLs.
	 * Instead of using hardcoded fake UUIDs, this endpoint fetches the top N documents
	 * from the source's OpenSearch index, extracts their objectId/nodeId, and applies
	 * the documentUrlTemplate to generate real, clickable URLs.
	 *
	 * @param sourceId the source system ID to look up connection_config
	 * @param size     number of sample URLs to return (default 10)
	 */
	@Operation(summary = "Generate sample Browse-in-Source URLs from real indexed documents")
	@GetMapping("/sample-source-urls")
	public Result<Map<String, Object>> getSampleSourceUrls(
			@RequestParam String sourceId,
			@RequestParam(defaultValue = "10") int size) {
		try {
			// 1. Get source system config
			List<Map<String, Object>> sourceRows = jdbcTemplate.queryForList(
				"SELECT connector_type, connection_config FROM source_system WHERE source_id = ?", sourceId);
			if (sourceRows.isEmpty()) {
				return Result.error(400, "Source system not found");
			}

			String configJson = (String) sourceRows.get(0).get("connection_config");
			Map<String, Object> config = objectMapper.readValue(configJson, new TypeReference<>() {});

			String template = (String) config.getOrDefault("documentUrlTemplate", "");
			String protocol = String.valueOf(config.getOrDefault("protocol",
					config.getOrDefault("PROTOCOL", "https")));
			String server = String.valueOf(config.getOrDefault("server",
					config.getOrDefault("SERVER", "")));
			String port = String.valueOf(config.getOrDefault("port",
					config.getOrDefault("PORT", "443")));

			if (template == null || template.isBlank()) {
				return Result.error(400, "No documentUrlTemplate configured for this source");
			}

			// 2. Find the KB linked to this source via knowledge_sync
			List<Map<String, Object>> syncRows = jdbcTemplate.queryForList(
				"SELECT kb_id FROM knowledge_sync WHERE source_id = ? ORDER BY gmt_modified DESC LIMIT 1", sourceId);
			if (syncRows.isEmpty()) {
				return Result.error(400, "No knowledge base synced from this source");
			}
			String kbId = String.valueOf(syncRows.get(0).get("kb_id"));

			// 3. Query OpenSearch for top N documents (no ACL filter — admin context)
			String index = kbId + "_document";
			Map<String, Object> requestBody = new LinkedHashMap<>();
			requestBody.put("query", Map.of("match_all", Map.of()));
			requestBody.put("size", size);
			requestBody.put("_source", List.of("cmis:objectId", "cmis:contentStreamFileName", "cmis:name", "url"));
			requestBody.put("sort", List.of(Map.of("cmis:creationDate", Map.of("order", "desc", "unmapped_type", "date"))));

			String queryJson = objectMapper.writeValueAsString(requestBody);
			log.debug("Sample source URLs query on {}: {}", index, queryJson);

			Map<String, Object> result;
			try {
				result = executeOpenSearchQuery(index, queryJson);
			}
			catch (Exception e) {
				log.warn("OpenSearch index {} not available: {}", index, e.getMessage());
				return Result.error(400, "Index " + index + " not available — documents may not be indexed yet");
			}

			// 4. Extract objectIds and generate URLs
			@SuppressWarnings("unchecked")
			Map<String, Object> hits = (Map<String, Object>) result.get("hits");
			@SuppressWarnings("unchecked")
			Map<String, Object> total = (Map<String, Object>) hits.get("total");
			long totalDocs = ((Number) total.get("value")).longValue();

			@SuppressWarnings("unchecked")
			List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");

			List<Map<String, Object>> sampleUrls = new ArrayList<>();
			for (Map<String, Object> hit : hitList) {
				@SuppressWarnings("unchecked")
				Map<String, Object> source = (Map<String, Object>) hit.get("_source");

				String objectId = String.valueOf(source.getOrDefault("cmis:objectId",
						source.getOrDefault("url", "")));
				String fileName = String.valueOf(source.getOrDefault("cmis:contentStreamFileName",
						source.getOrDefault("cmis:name", "document")));

				// Strip version suffix (e.g., "abc123;1.0" → "abc123")
				String nodeId = objectId;
				if (nodeId.contains(";")) {
					nodeId = nodeId.substring(0, nodeId.indexOf(";"));
				}

				// Apply template
				String url = template
						.replace("{protocol}", protocol)
						.replace("{server}", server)
						.replace("{port}", port)
						.replace("{nodeId}", nodeId)
						.replace("{objectId}", objectId)
						.replace("{path}", String.valueOf(config.getOrDefault("path",
								config.getOrDefault("BASEPATH", ""))))
						.replace("{fileName}", fileName);
				// Strip default port suffixes
				url = url.replace(":443/", "/").replace(":80/", "/");

				Map<String, Object> entry = new LinkedHashMap<>();
				entry.put("objectId", objectId);
				entry.put("nodeId", nodeId);
				entry.put("fileName", fileName);
				entry.put("sourceUrl", url);
				sampleUrls.add(entry);
			}

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("sampleUrls", sampleUrls);
			response.put("totalDocuments", totalDocs);
			response.put("template", template);
			return Result.success(response);
		}
		catch (Exception e) {
			log.error("Failed to generate sample source URLs for source {}", sourceId, e);
			return Result.error(500, "Failed to generate sample URLs: " + e.getMessage());
		}
	}

	// ==================== Knowledge Bases for App ====================

	/**
	 * List knowledge bases linked to an application.
	 * Returns kb_id, name, description, and total_docs for each KB
	 * configured in the app's file_search config.
	 */
	@Operation(summary = "List knowledge bases for an app")
	@GetMapping("/knowledge-bases")
	public Result<List<Map<String, Object>>> listKnowledgeBases(
			@RequestParam String appId,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) return Result.error(401, "Unauthorized");

			List<String> kbIds = resolveKbIds(appId);
			if (kbIds.isEmpty()) {
				return Result.success(List.of());
			}

			// Query knowledge_base table for these IDs
			String placeholders = kbIds.stream().map(id -> "?").collect(java.util.stream.Collectors.joining(","));
			String sql = "SELECT kb_id, name, description, total_docs FROM knowledge_base WHERE kb_id IN (" + placeholders + ")";
			List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, kbIds.toArray());

			List<Map<String, Object>> result = new java.util.ArrayList<>();
			for (Map<String, Object> row : rows) {
				Map<String, Object> kb = new LinkedHashMap<>();
				kb.put("kbId", row.get("kb_id"));
				kb.put("name", row.get("name"));
				kb.put("description", row.get("description"));
				kb.put("totalDocs", row.getOrDefault("total_docs", 0));
				// Check if this KB has a CMIS sync (enables browse)
				String syncId = resolveSyncId(String.valueOf(row.get("kb_id")));
				kb.put("hasCmisSync", syncId != null);
				result.add(kb);
			}
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("Failed to list knowledge bases for app {}", appId, e);
			return Result.error(500, "Failed to list knowledge bases: " + e.getMessage());
		}
	}

	// ==================== CMIS Browse (Folder/File Browser) ====================

	/**
	 * Resolve a syncId from an appId (picks the first CMIS source-backed KB).
	 */
	private String resolveSyncIdFromApp(String appId) {
		List<String> kbIds = resolveKbIds(appId);
		for (String kbId : kbIds) {
			String syncId = resolveSyncId(kbId);
			if (syncId != null) return syncId;
		}
		return null;
	}

	/**
	 * Browse a CMIS folder. Returns folder contents (files and subfolders).
	 * If kbId is provided, browses that specific KB; otherwise picks the first CMIS-backed KB.
	 */
	@Operation(summary = "Browse CMIS folder")
	@GetMapping("/cmis/browse")
	public Result<Map<String, Object>> browseCmisFolder(
			@RequestParam String appId,
			@RequestParam(required = false) String kbId,
			@RequestParam(required = false) String folderId,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) return Result.error(401, "Unauthorized");

			String syncId = null;
			if (kbId != null && !kbId.isEmpty()) {
				syncId = resolveSyncId(kbId);
			}
			if (syncId == null) {
				syncId = resolveSyncIdFromApp(appId);
			}
			if (syncId == null) return Result.error(400, "No sync job found for this app");

			Map<String, Object> result = knowledgeSyncService.browseCmisFolder(syncId, folderId);
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("CMIS browse failed for app {} folder {}", appId, folderId, e);
			return Result.error(500, "Failed to browse folder: " + e.getMessage());
		}
	}

	/**
	 * Upload a document to a CMIS folder.
	 */
	@Operation(summary = "Upload document to CMIS folder")
	@PostMapping("/cmis/upload")
	public Result<Map<String, Object>> uploadCmisDocument(
			@RequestParam String appId,
			@RequestParam(required = false) String folderId,
			@RequestParam("file") org.springframework.web.multipart.MultipartFile file,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) return Result.error(401, "Unauthorized");

			String syncId = resolveSyncIdFromApp(appId);
			if (syncId == null) return Result.error(400, "No sync job found for this app");

			String fileName = file.getOriginalFilename();
			if (fileName == null || fileName.isBlank()) fileName = "uploaded_file";
			String contentType = file.getContentType();
			if (contentType == null || contentType.isBlank()) contentType = "application/octet-stream";

			Map<String, Object> result = knowledgeSyncService.uploadCmisDocument(
					syncId, folderId, fileName, contentType, file.getBytes());
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("CMIS upload failed for app {} folder {}", appId, folderId, e);
			return Result.error(500, "Failed to upload: " + e.getMessage());
		}
	}

	/**
	 * Delete a CMIS object (file or folder).
	 */
	@Operation(summary = "Delete CMIS object")
	@DeleteMapping("/cmis/delete")
	public Result<Map<String, Object>> deleteCmisObject(
			@RequestParam String appId,
			@RequestParam String objectId,
			@RequestParam(defaultValue = "true") boolean allVersions,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) return Result.error(401, "Unauthorized");

			String syncId = resolveSyncIdFromApp(appId);
			if (syncId == null) return Result.error(400, "No sync job found for this app");

			Map<String, Object> result = knowledgeSyncService.deleteCmisObject(syncId, objectId, allVersions);
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("CMIS delete failed for app {} objectId {}", appId, objectId, e);
			return Result.error(500, "Failed to delete: " + e.getMessage());
		}
	}

	/**
	 * Rename a CMIS object.
	 */
	@Operation(summary = "Rename CMIS object")
	@PutMapping("/cmis/rename")
	public Result<Map<String, Object>> renameCmisObject(
			@RequestParam String appId,
			@RequestParam String objectId,
			@RequestParam String newName,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) return Result.error(401, "Unauthorized");

			String syncId = resolveSyncIdFromApp(appId);
			if (syncId == null) return Result.error(400, "No sync job found for this app");

			Map<String, Object> result = knowledgeSyncService.renameCmisObject(syncId, objectId, newName);
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("CMIS rename failed for app {} objectId {}", appId, objectId, e);
			return Result.error(500, "Failed to rename: " + e.getMessage());
		}
	}

	/**
	 * Create a new folder in CMIS.
	 */
	@Operation(summary = "Create CMIS folder")
	@PostMapping("/cmis/create-folder")
	public Result<Map<String, Object>> createCmisFolder(
			@RequestParam String appId,
			@RequestParam(required = false) String parentFolderId,
			@RequestParam String folderName,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) return Result.error(401, "Unauthorized");

			String syncId = resolveSyncIdFromApp(appId);
			if (syncId == null) return Result.error(400, "No sync job found for this app");

			Map<String, Object> result = knowledgeSyncService.createCmisFolder(syncId, parentFolderId, folderName);
			return Result.success(result);
		}
		catch (Exception e) {
			log.error("CMIS create folder failed for app {} parent {}", appId, parentFolderId, e);
			return Result.error(500, "Failed to create folder: " + e.getMessage());
		}
	}

	// ==================== Authority (Groups/Users) Browser ====================

	/**
	 * Browse authorities (users and groups) from the knowledge base's _authority index.
	 * Provides paginated access to the synced ACL principals.
	 */
	@Operation(summary = "Browse authorities for a knowledge base")
	@GetMapping("/authorities")
	public Result<Map<String, Object>> browseAuthorities(
			@RequestParam String appId,
			@RequestParam(required = false) String kbId,
			@RequestParam(required = false) String query,
			@RequestParam(required = false) String principalType,
			@RequestParam(defaultValue = "0") int from,
			@RequestParam(defaultValue = "50") int size,
			HttpServletRequest request) {
		try {
			String email = extractChatbotUserEmail(request);
			if (email == null) {
				return Result.error(401, "Unauthorized");
			}

			List<String> kbIds;
			if (kbId != null && !kbId.isBlank()) {
				kbIds = List.of(kbId);
			} else {
				kbIds = resolveKbIds(appId);
			}
			if (kbIds.isEmpty()) {
				return Result.error(400, "No knowledge base configured for this app");
			}

			List<Map<String, Object>> allAuthorities = new ArrayList<>();
			long totalHits = 0;

			for (String kb : kbIds) {
				String index = kb + "_authority";

				// Build search query
				List<Object> mustClauses = new ArrayList<>();
				List<Object> filterClauses = new ArrayList<>();

				if (query != null && !query.isBlank()) {
					mustClauses.add(Map.of("multi_match", Map.of(
						"query", query,
						"fields", List.of("principal_id^3", "display_name^2", "member_of^1"),
						"type", "best_fields",
						"fuzziness", "AUTO"
					)));
				}

				if (principalType != null && !principalType.isBlank()) {
					filterClauses.add(Map.of("term", Map.of("principal_type", principalType)));
				}

				Map<String, Object> boolQuery = new LinkedHashMap<>();
				if (!mustClauses.isEmpty()) {
					boolQuery.put("must", mustClauses);
				} else {
					boolQuery.put("must", List.of(Map.of("match_all", Map.of())));
				}
				if (!filterClauses.isEmpty()) {
					boolQuery.put("filter", filterClauses);
				}

				// Aggregations for summary (use missing parameter to include docs without the field)
				Map<String, Object> aggs = Map.of(
					"types", Map.of("terms", Map.of(
						"field", "principal_type",
						"size", 10,
						"missing", "unknown"
					))
				);

				Map<String, Object> requestBody = new LinkedHashMap<>();
				requestBody.put("query", Map.of("bool", boolQuery));
				requestBody.put("from", from);
				requestBody.put("size", size);
				requestBody.put("sort", List.of(
					Map.of("principal_type", Map.of("order", "asc", "unmapped_type", "keyword")),
					Map.of("principal_id", Map.of("order", "asc", "unmapped_type", "keyword"))
				));
				requestBody.put("aggs", aggs);

				try {
					String queryJson = objectMapper.writeValueAsString(requestBody);
					log.debug("OpenSearch authority query on {}: {}", index, queryJson);

					Map<String, Object> result = executeOpenSearchQuery(index, queryJson);

					@SuppressWarnings("unchecked")
					Map<String, Object> hits = (Map<String, Object>) result.get("hits");
					@SuppressWarnings("unchecked")
					Map<String, Object> total = (Map<String, Object>) hits.get("total");
					totalHits += ((Number) total.get("value")).longValue();

					@SuppressWarnings("unchecked")
					List<Map<String, Object>> hitList = (List<Map<String, Object>>) hits.get("hits");
					for (Map<String, Object> hit : hitList) {
						@SuppressWarnings("unchecked")
						Map<String, Object> source = (Map<String, Object>) hit.get("_source");
						Map<String, Object> auth = new LinkedHashMap<>();
						auth.put("id", hit.get("_id"));
						auth.put("kbId", kb);
						auth.put("principalId", source.getOrDefault("principal_id", ""));
						auth.put("principalType", source.getOrDefault("principal_type", ""));
						auth.put("displayName", source.getOrDefault("display_name", ""));
						auth.put("memberOf", source.getOrDefault("member_of", List.of()));
						auth.put("members", source.getOrDefault("members", List.of()));
						auth.put("memberCount", source.getOrDefault("member_count", 0));
						auth.put("syncedAt", source.getOrDefault("synced_at", ""));
						allAuthorities.add(auth);
					}
				}
				catch (Exception e) {
					log.warn("Failed to query authority index {}: {}", index, e.getMessage());
				}
			}

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("authorities", allAuthorities);
			response.put("total", totalHits);
			response.put("from", from);
			response.put("size", size);

			return Result.success(response);
		}
		catch (Exception e) {
			log.error("Authority browse failed for app {}", appId, e);
			return Result.error(500, "Authority browse failed: " + e.getMessage());
		}
	}

}
