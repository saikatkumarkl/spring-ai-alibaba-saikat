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
package com.alibaba.cloud.ai.studio.admin.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration properties for Keycloak OIDC integration. When enabled, the
 * {@link com.alibaba.cloud.ai.studio.admin.builder.interceptor.TokenAuthInterceptor}
 * will accept Keycloak-issued JWTs in addition to the built-in custom tokens.
 *
 * @since 1.0.0
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak")
public class KeycloakProperties {

	/**
	 * Whether Keycloak SSO is enabled. When false, only built-in token auth is active.
	 */
	private boolean enabled = false;

	/**
	 * Keycloak server base URL used for backend-to-Keycloak communication (e.g. JWKS
	 * fetching). Inside Docker this is the internal hostname
	 * ({@code http://keycloak:8080}).
	 */
	private String authServerUrl = "http://localhost:8180";

	/**
	 * Browser-accessible Keycloak URL. Used for JWT issuer ({@code iss}) validation and
	 * returned to the frontend for OIDC redirects. Defaults to {@code authServerUrl} when
	 * not set.
	 */
	private String publicUrl;

	/**
	 * Keycloak realm name.
	 */
	private String realm = "cordondata";

	/**
	 * Expected audience in the JWT ({@code aud} claim). Leave empty to skip audience
	 * validation.
	 */
	private String audience = "";

	/**
	 * JWKS cache TTL in seconds. Public keys are fetched from the Keycloak JWKS endpoint
	 * and cached for this duration.
	 */
	private long jwksCacheTtlSeconds = 300;

	/**
	 * Admin username for Keycloak Admin REST API calls (user/role management). Must be a
	 * user in the {@code master} realm with {@code admin} role.
	 */
	private String adminUsername = "admin";

	/**
	 * Admin password for Keycloak Admin REST API calls.
	 */
	private String adminPassword = "admin";

	/**
	 * Returns the effective public URL (falls back to {@code authServerUrl}).
	 */
	public String getEffectivePublicUrl() {
		String url = (publicUrl != null && !publicUrl.isBlank()) ? publicUrl : authServerUrl;
		return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
	}

	/**
	 * Issuer URI for JWT {@code iss} claim validation — uses the public URL because
	 * Keycloak sets the issuer based on how the browser accessed it.
	 */
	public String getIssuerUri() {
		return getEffectivePublicUrl() + "/realms/" + realm;
	}

	/**
	 * JWKS URI for fetching RSA public keys — uses the internal {@code authServerUrl}
	 * because this is a backend-to-Keycloak network call.
	 */
	public String getJwksUri() {
		String base = authServerUrl.endsWith("/") ? authServerUrl.substring(0, authServerUrl.length() - 1)
				: authServerUrl;
		return base + "/realms/" + realm + "/protocol/openid-connect/certs";
	}

}
