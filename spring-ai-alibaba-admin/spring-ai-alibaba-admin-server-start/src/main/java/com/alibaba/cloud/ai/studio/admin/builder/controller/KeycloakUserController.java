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

import com.alibaba.cloud.ai.studio.admin.config.KeycloakProperties;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.Serializable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Controller for managing Keycloak users and roles via the Keycloak Admin REST API.
 * Only accessible to users with the "admin" role (enforced by TokenAuthInterceptor).
 *
 * @since 1.0.0
 */
@Slf4j
@RestController
@Tag(name = "keycloak_users")
@RequestMapping("/console/v1/keycloak-users")
@RequiredArgsConstructor
public class KeycloakUserController {

	private final KeycloakProperties keycloakProperties;

	private final ObjectMapper objectMapper;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	// ── DTOs ──────────────────────────────────────────────────────

	@Data
	public static class KeycloakUser implements Serializable {

		private String id;

		private String username;

		private String email;

		private String firstName;

		private String lastName;

		private boolean enabled;

		private Long createdTimestamp;

		private List<String> roles;

	}

	@Data
	public static class RoleRequest implements Serializable {

		private String roleName;

	}

	// ── Endpoints ─────────────────────────────────────────────────

	@Operation(summary = "List all users in the Keycloak realm")
	@GetMapping
	public Result<List<KeycloakUser>> listUsers(
			@RequestParam(defaultValue = "0") int first,
			@RequestParam(defaultValue = "100") int max) {
		try {
			String adminToken = getAdminToken();
			String baseUrl = keycloakProperties.getAuthServerUrl();
			String realm = keycloakProperties.getRealm();

			// Fetch users
			String usersUrl = baseUrl + "/admin/realms/" + realm + "/users?first=" + first + "&max=" + max;
			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(usersUrl))
				.header("Authorization", "Bearer " + adminToken)
				.GET()
				.build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				log.error("Failed to list Keycloak users: {} {}", resp.statusCode(), resp.body());
				return Result.error(resp.statusCode(), "Failed to list users from Keycloak");
			}

			List<Map<String, Object>> rawUsers = objectMapper.readValue(resp.body(), new TypeReference<>() {
			});

			// Fetch roles for each user
			List<KeycloakUser> users = new ArrayList<>();
			for (Map<String, Object> raw : rawUsers) {
				KeycloakUser user = new KeycloakUser();
				user.setId((String) raw.get("id"));
				user.setUsername((String) raw.get("username"));
				user.setEmail((String) raw.get("email"));
				user.setFirstName((String) raw.get("firstName"));
				user.setLastName((String) raw.get("lastName"));
				user.setEnabled(Boolean.TRUE.equals(raw.get("enabled")));
				if (raw.get("createdTimestamp") instanceof Number n) {
					user.setCreatedTimestamp(n.longValue());
				}
				user.setRoles(getUserRealmRoles(adminToken, user.getId()));
				users.add(user);
			}

			return Result.success(users);
		}
		catch (Exception e) {
			log.error("Error listing Keycloak users", e);
			return Result.error(500, "Error listing users: " + e.getMessage());
		}
	}

	@Operation(summary = "Get roles for a specific user")
	@GetMapping("/{userId}/roles")
	public Result<List<String>> getUserRoles(@PathVariable String userId) {
		try {
			String adminToken = getAdminToken();
			List<String> roles = getUserRealmRoles(adminToken, userId);
			return Result.success(roles);
		}
		catch (Exception e) {
			log.error("Error getting user roles for {}", userId, e);
			return Result.error(500, "Error getting roles: " + e.getMessage());
		}
	}

	@Operation(summary = "Assign a realm role to a user")
	@PostMapping("/{userId}/roles")
	public Result<String> assignRole(@PathVariable String userId, @RequestBody RoleRequest request) {
		try {
			String adminToken = getAdminToken();
			String baseUrl = keycloakProperties.getAuthServerUrl();
			String realm = keycloakProperties.getRealm();

			// First, get the role representation by name
			Map<String, Object> role = getRealmRoleByName(adminToken, request.getRoleName());
			if (role == null) {
				return Result.error(404, "Role '" + request.getRoleName() + "' not found");
			}

			// Assign the role to the user
			String url = baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
			String body = objectMapper.writeValueAsString(List.of(role));

			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Authorization", "Bearer " + adminToken)
				.header("Content-Type", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body))
				.build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 204 && resp.statusCode() != 200) {
				log.error("Failed to assign role: {} {}", resp.statusCode(), resp.body());
				return Result.error(resp.statusCode(), "Failed to assign role");
			}

			log.info("Assigned role '{}' to user {}", request.getRoleName(), userId);
			return Result.success("Role assigned successfully");
		}
		catch (Exception e) {
			log.error("Error assigning role to user {}", userId, e);
			return Result.error(500, "Error assigning role: " + e.getMessage());
		}
	}

	@Operation(summary = "Remove a realm role from a user")
	@DeleteMapping("/{userId}/roles")
	public Result<String> removeRole(@PathVariable String userId, @RequestBody RoleRequest request) {
		try {
			String adminToken = getAdminToken();
			String baseUrl = keycloakProperties.getAuthServerUrl();
			String realm = keycloakProperties.getRealm();

			Map<String, Object> role = getRealmRoleByName(adminToken, request.getRoleName());
			if (role == null) {
				return Result.error(404, "Role '" + request.getRoleName() + "' not found");
			}

			String url = baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";
			String body = objectMapper.writeValueAsString(List.of(role));

			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Authorization", "Bearer " + adminToken)
				.header("Content-Type", "application/json")
				.method("DELETE", HttpRequest.BodyPublishers.ofString(body))
				.build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 204 && resp.statusCode() != 200) {
				log.error("Failed to remove role: {} {}", resp.statusCode(), resp.body());
				return Result.error(resp.statusCode(), "Failed to remove role");
			}

			log.info("Removed role '{}' from user {}", request.getRoleName(), userId);
			return Result.success("Role removed successfully");
		}
		catch (Exception e) {
			log.error("Error removing role from user {}", userId, e);
			return Result.error(500, "Error removing role: " + e.getMessage());
		}
	}

	// ── Internal helpers ──────────────────────────────────────────

	/**
	 * Obtain an admin access token from the Keycloak master realm using the resource
	 * owner password credentials grant.
	 */
	private String getAdminToken() throws Exception {
		String baseUrl = keycloakProperties.getAuthServerUrl();
		String tokenUrl = baseUrl + "/realms/master/protocol/openid-connect/token";

		String formBody = "grant_type=password" + "&client_id=admin-cli" + "&username="
				+ URLEncoder.encode(keycloakProperties.getAdminUsername(), StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode(keycloakProperties.getAdminPassword(), StandardCharsets.UTF_8);

		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(tokenUrl))
			.header("Content-Type", "application/x-www-form-urlencoded")
			.POST(HttpRequest.BodyPublishers.ofString(formBody))
			.build();

		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) {
			throw new RuntimeException("Failed to obtain Keycloak admin token: " + resp.statusCode());
		}

		Map<String, Object> tokenResponse = objectMapper.readValue(resp.body(), new TypeReference<>() {
		});
		return (String) tokenResponse.get("access_token");
	}

	/**
	 * Get realm-level role mappings for a user. Returns only non-default role names
	 * (filters out Keycloak internal roles).
	 */
	private List<String> getUserRealmRoles(String adminToken, String userId) throws Exception {
		String baseUrl = keycloakProperties.getAuthServerUrl();
		String realm = keycloakProperties.getRealm();
		String url = baseUrl + "/admin/realms/" + realm + "/users/" + userId + "/role-mappings/realm";

		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Authorization", "Bearer " + adminToken)
			.GET()
			.build();

		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) {
			return Collections.emptyList();
		}

		List<Map<String, Object>> roles = objectMapper.readValue(resp.body(), new TypeReference<>() {
		});
		List<String> roleNames = new ArrayList<>();
		for (Map<String, Object> role : roles) {
			String name = (String) role.get("name");
			// Filter out Keycloak internal default roles
			if (name != null && !name.startsWith("default-roles-") && !name.equals("offline_access")
					&& !name.equals("uma_authorization")) {
				roleNames.add(name);
			}
		}
		return roleNames;
	}

	/**
	 * Get a realm role representation by name.
	 */
	private Map<String, Object> getRealmRoleByName(String adminToken, String roleName) throws Exception {
		String baseUrl = keycloakProperties.getAuthServerUrl();
		String realm = keycloakProperties.getRealm();
		String url = baseUrl + "/admin/realms/" + realm + "/roles/"
				+ URLEncoder.encode(roleName, StandardCharsets.UTF_8);

		HttpRequest req = HttpRequest.newBuilder()
			.uri(URI.create(url))
			.header("Authorization", "Bearer " + adminToken)
			.GET()
			.build();

		HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
		if (resp.statusCode() != 200) {
			return null;
		}

		return objectMapper.readValue(resp.body(), new TypeReference<>() {
		});
	}

}
