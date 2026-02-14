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

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import jakarta.servlet.http.HttpSession;
import java.util.Map;

/**
 * Controller for handling user authentication and session management.
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AdminApiService adminApi;

	public AuthController(AdminApiService adminApi) {
		this.adminApi = adminApi;
	}

	@Data
	public static class LoginRequest {
		private String email;
		private String password;
	}

	@Data
	public static class LoginResponse {
		private boolean success;
		private String message;
		private Map<String, Object> data;

		public static LoginResponse success(Map<String, Object> data) {
			LoginResponse response = new LoginResponse();
			response.success = true;
			response.data = data;
			return response;
		}

		public static LoginResponse error(String message) {
			LoginResponse response = new LoginResponse();
			response.success = false;
			response.message = message;
			return response;
		}
	}

	@PostMapping("/login")
	public Mono<ResponseEntity<LoginResponse>> login(@RequestBody LoginRequest request, HttpSession session) {
		log.info("Login request for email: {}", request.getEmail());
		
		return adminApi.login(request.getEmail(), request.getPassword())
			.map(data -> {
				// Store in session
				session.setAttribute("user", data);
				session.setAttribute("token", data.get("token"));
				session.setAttribute("email", data.get("email"));
				log.info("User logged in successfully: {}", data.get("email"));
				// Log audit action
				adminApi.logAuditAction((String) data.get("email"), "LOGIN", "session", session.getId(), null)
					.subscribe();
				return ResponseEntity.ok(LoginResponse.success(data));
			})
			.onErrorResume(error -> {
				log.error("Login failed", error);
				return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
					.body(LoginResponse.error(error.getMessage())));
			});
	}

	@PostMapping("/logout")
	public Mono<ResponseEntity<Map<String, String>>> logout(HttpSession session) {
		String email = (String) session.getAttribute("email");
		log.info("User logging out: {}", email);
		// Log audit action
		if (email != null) {
			adminApi.logAuditAction(email, "LOGOUT", "session", session.getId(), null).subscribe();
		}
		session.invalidate();
		return Mono.just(ResponseEntity.ok(Map.of("message", "Logged out successfully")));
	}

	@GetMapping("/session")
	public Mono<ResponseEntity<LoginResponse>> getSession(HttpSession session) {
		Object user = session.getAttribute("user");
		if (user != null) {
			return Mono.just(ResponseEntity.ok(LoginResponse.success((Map<String, Object>) user)));
		}
		return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
			.body(LoginResponse.error("Not logged in")));
	}

}
