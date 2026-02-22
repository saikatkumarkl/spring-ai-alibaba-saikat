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
package com.alibaba.cloud.ai.examples.chatbot;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Keycloak configuration, JWT validator, and REST endpoint for the chatbot app.
 * Provides:
 * <ul>
 *   <li>Configuration properties via {@code keycloak.*}</li>
 *   <li>JWT validation against the Keycloak JWKS endpoint</li>
 *   <li>A REST endpoint ({@code GET /api/auth/keycloak-config}) for the frontend</li>
 * </ul>
 */
@Slf4j
@Data
@Configuration
@ConfigurationProperties(prefix = "keycloak")
@RestController
@RequestMapping("/api/auth")
public class KeycloakConfig {

	private boolean enabled = false;

	private String authServerUrl = "http://localhost:8180";

	/** Public URL for browser-facing OIDC redirects (may differ from authServerUrl in Docker). */
	private String publicUrl;

	private String realm = "spring-ai-alibaba";

	private String clientId = "chatbot";

	/** Keycloak admin username for Admin API access (e.g. listing identity providers). */
	private String adminUsername = "admin";

	/** Keycloak admin password. */
	private String adminPassword = "admin";

	private final transient ObjectMapper objectMapper = new ObjectMapper();

	private final transient HttpClient httpClient = HttpClient.newHttpClient();

	// --- JWKS cache ---
	private final transient AtomicReference<JWKSet> cachedJwkSet = new AtomicReference<>();

	private transient volatile long jwkSetFetchedAt = 0;

	private static final long JWKS_CACHE_TTL_MS = 300_000; // 5 minutes

	/**
	 * REST endpoint that returns Keycloak config to the frontend. This is
	 * unauthenticated so the login page can fetch it before the user logs in.
	 */
	@GetMapping("/keycloak-config")
	public Map<String, Object> getKeycloakConfig() {
		Map<String, Object> config = new LinkedHashMap<>();
		config.put("enabled", enabled);
		if (enabled) {
			// Return public URL for browser-facing OIDC; fall back to authServerUrl
			config.put("url", (publicUrl != null && !publicUrl.isBlank()) ? publicUrl : authServerUrl);
			config.put("realm", realm);
			config.put("clientId", clientId);
		}
		return config;
	}

	/**
	 * REST endpoint that returns configured Identity Providers (e.g. Azure AD, Google)
	 * from Keycloak. The frontend uses this to render specific SSO buttons that
	 * bypass the Keycloak login page via {@code kc_idp_hint}.
	 */
	@GetMapping("/sso-providers")
	public Map<String, Object> getSsoProviders() {
		if (!enabled) {
			return Map.of("providers", Collections.emptyList());
		}
		try {
			String adminToken = getKeycloakAdminToken();
			String url = authServerUrl + "/admin/realms/" + realm + "/identity-provider/instances";

			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Authorization", "Bearer " + adminToken)
				.GET()
				.build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				log.error("Failed to list IdPs from Keycloak: {} {}", resp.statusCode(), resp.body());
				return Map.of("providers", Collections.emptyList());
			}

			List<Map<String, Object>> rawIdps = objectMapper.readValue(resp.body(), new TypeReference<>() {
			});

			List<Map<String, String>> providers = new ArrayList<>();
			for (Map<String, Object> raw : rawIdps) {
				if (!Boolean.TRUE.equals(raw.get("enabled"))) {
					continue;
				}
				Map<String, String> p = new LinkedHashMap<>();
				p.put("alias", (String) raw.get("alias"));
				p.put("displayName", (String) raw.get("displayName"));
				p.put("providerId", (String) raw.get("providerId"));
				providers.add(p);
			}
			return Map.of("providers", providers);
		}
		catch (Exception e) {
			log.error("Error listing SSO providers from Keycloak", e);
			return Map.of("providers", Collections.emptyList());
		}
	}

	/**
	 * Obtain a Keycloak master-realm admin token.
	 */
	private String getKeycloakAdminToken() throws Exception {
		String tokenUrl = authServerUrl + "/realms/master/protocol/openid-connect/token";
		String formBody = "grant_type=password" + "&client_id=admin-cli" + "&username="
				+ URLEncoder.encode(adminUsername, StandardCharsets.UTF_8) + "&password="
				+ URLEncoder.encode(adminPassword, StandardCharsets.UTF_8);

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

	// --- JWT validation methods ---

	public String getIssuerUri() {
		String base = authServerUrl.endsWith("/") ? authServerUrl.substring(0, authServerUrl.length() - 1)
				: authServerUrl;
		return base + "/realms/" + realm;
	}

	/** Issuer URI based on publicUrl (for tokens issued via browser-facing URL). */
	private String getPublicIssuerUri() {
		if (publicUrl == null || publicUrl.isBlank())
			return null;
		String base = publicUrl.endsWith("/") ? publicUrl.substring(0, publicUrl.length() - 1) : publicUrl;
		return base + "/realms/" + realm;
	}

	/** Returns true if the given issuer matches either the internal or public issuer URI. */
	private boolean matchesIssuer(String issuer) {
		if (getIssuerUri().equals(issuer))
			return true;
		String pub = getPublicIssuerUri();
		return pub != null && pub.equals(issuer);
	}

	public String getJwksUri() {
		return getIssuerUri() + "/protocol/openid-connect/certs";
	}

	/**
	 * Check if a token looks like it was issued by this Keycloak realm.
	 */
	public boolean isKeycloakToken(String token) {
		if (!enabled)
			return false;
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			return matchesIssuer(jwt.getJWTClaimsSet().getIssuer());
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Validate a Keycloak JWT and return the claims, or null if invalid.
	 */
	public JWTClaimsSet validateToken(String token) {
		if (!enabled)
			return null;
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			JWTClaimsSet claims = jwt.getJWTClaimsSet();

			// Check issuer (accept both internal and public URLs)
			if (!matchesIssuer(claims.getIssuer()))
				return null;

			// Check expiration
			Date exp = claims.getExpirationTime();
			if (exp == null || exp.toInstant().isBefore(Instant.now()))
				return null;

			// Verify signature
			JWKSet jwkSet = getJwkSet();
			if (jwkSet == null)
				return null;

			JWKMatcher matcher = new JWKMatcher.Builder().keyID(jwt.getHeader().getKeyID())
				.algorithm(JWSAlgorithm.RS256)
				.build();
			List<JWK> matches = new JWKSelector(matcher).select(jwkSet);

			if (matches.isEmpty()) {
				jwkSet = refreshJwkSet();
				if (jwkSet == null)
					return null;
				matches = new JWKSelector(matcher).select(jwkSet);
				if (matches.isEmpty())
					return null;
			}

			JWSVerifier verifier = new RSASSAVerifier(matches.get(0).toRSAKey());
			if (!jwt.verify(verifier))
				return null;

			return claims;
		}
		catch (Exception e) {
			log.warn("Keycloak JWT validation error: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Extract user info from validated Keycloak claims.
	 */
	public Map<String, Object> extractUserInfo(JWTClaimsSet claims) {
		try {
			Map<String, Object> info = new LinkedHashMap<>();
			info.put("email", claims.getClaim("email"));
			info.put("username", claims.getClaim("preferred_username"));
			info.put("name", claims.getClaim("name"));
			info.put("sub", claims.getSubject());
			info.put("token_type", "keycloak");
			return info;
		}
		catch (Exception e) {
			return Collections.emptyMap();
		}
	}

	private JWKSet getJwkSet() {
		JWKSet current = cachedJwkSet.get();
		if (current != null && (System.currentTimeMillis() - jwkSetFetchedAt) < JWKS_CACHE_TTL_MS) {
			return current;
		}
		return refreshJwkSet();
	}

	private synchronized JWKSet refreshJwkSet() {
		try {
			log.info("Fetching Keycloak JWKS from {}", getJwksUri());
			JWKSet jwkSet = JWKSet.load(new URL(getJwksUri()));
			cachedJwkSet.set(jwkSet);
			jwkSetFetchedAt = System.currentTimeMillis();
			return jwkSet;
		}
		catch (Exception e) {
			log.error("Failed to fetch JWKS: {}", e.getMessage());
			return cachedJwkSet.get();
		}
	}

}
