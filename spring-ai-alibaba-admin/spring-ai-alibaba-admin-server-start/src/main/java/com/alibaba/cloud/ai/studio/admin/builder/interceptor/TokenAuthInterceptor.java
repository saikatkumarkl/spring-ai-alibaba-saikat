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

package com.alibaba.cloud.ai.studio.admin.builder.interceptor;

import com.alibaba.cloud.ai.studio.admin.config.KeycloakJwtValidator;
import com.alibaba.cloud.ai.studio.runtime.constants.ApiConstants;
import com.alibaba.cloud.ai.studio.runtime.enums.AccountType;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.runtime.domain.account.Account;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.alibaba.cloud.ai.studio.core.base.manager.TokenManager;
import com.alibaba.cloud.ai.studio.core.base.service.AccountService;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.utils.LogUtils;
import com.alibaba.cloud.ai.studio.core.utils.common.IdGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jetbrains.annotations.NotNull;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Map;

/**
 * Interceptor for handling token-based authentication for console API access. Validates
 * access tokens (built-in custom JWT or Keycloak OIDC JWT) and sets up request context
 * for authenticated users.
 *
 * <p>
 * When Keycloak is enabled, the interceptor first checks if the token was issued by
 * Keycloak (by inspecting the {@code iss} claim). If so, it validates the token using the
 * Keycloak JWKS endpoint. Otherwise, it falls back to the built-in Redis-based token
 * validation.
 *
 * @since 1.0.0.3
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TokenAuthInterceptor implements HandlerInterceptor {

	/** Service for managing account-related operations */
	private final AccountService accountService;

	/** Manager for handling token operations */
	private final TokenManager tokenManager;

	/** Keycloak JWT validator for OIDC SSO tokens */
	private final KeycloakJwtValidator keycloakJwtValidator;

	/**
	 * Intercepts requests to validate authentication token and set up request context.
	 * Returns true if authentication is successful, false otherwise.
	 */
	@Override
	public boolean preHandle(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response,
			@NotNull Object handler) {
		long start = System.currentTimeMillis();

		if (RequestMethod.OPTIONS.name().equals(request.getMethod().toUpperCase())) {
			return true;
		}

		String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
		if (authHeader == null) {
			authHeader = request.getParameter(ApiConstants.ACCESS_TOKEN);
		}
		else if (!authHeader.startsWith(ApiConstants.TOKEN_PREFIX)) {
			returnAuthError(start, response, ErrorCode.INVALID_TOKEN);
			return false;
		}

		if (authHeader == null) {
			returnAuthError(start, response, ErrorCode.INVALID_TOKEN);
			return false;
		}

		String token = authHeader.replace(ApiConstants.TOKEN_PREFIX + " ", "");

		// ── Try Keycloak OIDC JWT first ──────────────────────────────
		if (keycloakJwtValidator.isEnabled() && keycloakJwtValidator.isKeycloakToken(token)) {
			JWTClaimsSet claims = keycloakJwtValidator.validateToken(token);
			if (claims == null) {
				returnAuthError(start, response, ErrorCode.INVALID_TOKEN);
				return false;
			}

			Map<String, String> userInfo = keycloakJwtValidator.extractUserInfo(claims);
			RequestContext context = new RequestContext();
			context.setRequestId(IdGenerator.uuid());
			context.setAccountId(userInfo.getOrDefault("sub", ""));
			context.setUsername(userInfo.getOrDefault("username", ""));
			// Admin users share the main admin workspace (id "1") so they can
			// see all apps, models, and other shared resources.
			context.setWorkspaceId("1");
			// Map Keycloak roles to AccountType
			String roles = userInfo.getOrDefault("roles", "");
			context.setAccountType(roles.contains("admin") ? AccountType.ADMIN : AccountType.USER);
			context.setCallerIp(request.getRemoteAddr());
			context.setStartTime(System.currentTimeMillis());
			context.setSource("keycloak");

			// Enforce admin role — only users with the "admin" realm role may
			// access the admin control-center API.
			if (!roles.contains("admin")) {
				log.warn("Access denied for non-admin Keycloak user: {}", userInfo.get("username"));
				returnForbiddenError(start, response);
				return false;
			}

			RequestContextHolder.setRequestContext(context);
			log.debug("Keycloak SSO auth successful for user={}", userInfo.get("username"));
			return true;
		}

		// ── Fall back to built-in Redis-based token auth ─────────────
		String accountId = tokenManager.getAccountIdFromAccessToken(token);
		if (accountId == null) {
			returnAuthError(start, response, ErrorCode.INVALID_TOKEN);
			return false;
		}

		// login info
		Account account = accountService.getAccount(accountId);
		if (account == null) {
			returnAuthError(start, response, ErrorCode.INVALID_TOKEN);
			return false;
		}

		RequestContext context = new RequestContext();
		context.setRequestId(IdGenerator.uuid());
		context.setAccountId(account.getAccountId());
		context.setUsername(account.getUsername());
		context.setAccountType(account.getType());
		context.setCallerIp(request.getRemoteAddr());
		context.setStartTime(System.currentTimeMillis());

		// Enforce admin role for built-in accounts (consistent with Keycloak path)
		if (AccountType.ADMIN != account.getType()) {
			RequestContextHolder.setRequestContext(context);
			returnForbiddenError(start, response);
			return false;
		}

		// Admin users share the main admin workspace so they can see all apps,
		// models, and other shared resources. The admin workspace (id "1") is
		// always seeded in init SQL and belongs to the primary admin account.
		context.setWorkspaceId("1");

		RequestContextHolder.setRequestContext(context);

		return true;
	}

	/**
	 * Sends an unauthorized error response with the specified error code.
	 * @param start Start time of the request for monitoring
	 * @param response HTTP response object
	 * @param errorCode Error code to be returned
	 */
	public void returnAuthError(long start, HttpServletResponse response, ErrorCode errorCode) {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		Result<String> result = Result.error(IdGenerator.uuid(), errorCode);

		LogUtils.monitor("AuthInterceptor", "TokenAuth", start, "unauthorized", "", result);

		try {
			response.getWriter().write(JsonUtils.toJson(result));
		}
		catch (IOException e) {
			LogUtils.error("failed to unauthorized message: {}", result, e);
		}
	}

	/**
	 * Sends a 403 Forbidden response when a Keycloak-authenticated user lacks the admin
	 * role.
	 * @param start Start time of the request for monitoring
	 * @param response HTTP response object
	 */
	public void returnForbiddenError(long start, HttpServletResponse response) {
		response.setContentType("application/json;charset=UTF-8");
		response.setStatus(HttpServletResponse.SC_FORBIDDEN);
		Result<String> result = Result.error(403,
				"Access denied. Only users with the 'admin' role may access the control center.");

		LogUtils.monitor("AuthInterceptor", "TokenAuth", start, "forbidden", "", result);

		try {
			response.getWriter().write(JsonUtils.toJson(result));
		}
		catch (IOException e) {
			LogUtils.error("failed to write forbidden message: {}", result, e);
		}
	}

}
