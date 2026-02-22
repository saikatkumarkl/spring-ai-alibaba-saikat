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

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSVerifier;
import com.nimbusds.jose.crypto.RSASSAVerifier;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKMatcher;
import com.nimbusds.jose.jwk.JWKSelector;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.time.Instant;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Validates Keycloak-issued JWTs using the JWKS (JSON Web Key Set) endpoint. Caches
 * public keys for the configured TTL to avoid hitting Keycloak on every request.
 *
 * <p>
 * The validator checks: signature (RS256), issuer, expiration, and optionally audience.
 *
 * @since 1.0.0
 */
@Slf4j
@Component
public class KeycloakJwtValidator {

	private final KeycloakProperties properties;

	private final AtomicReference<JWKSet> cachedJwkSet = new AtomicReference<>();

	private volatile long jwkSetFetchedAt = 0;

	public KeycloakJwtValidator(KeycloakProperties properties) {
		this.properties = properties;
	}

	/**
	 * Returns whether Keycloak is enabled.
	 */
	public boolean isEnabled() {
		return properties.isEnabled();
	}

	/**
	 * Checks whether the token looks like a Keycloak JWT (has the expected issuer).
	 * This is a fast, non-cryptographic check used to determine which validation path to
	 * take.
	 */
	public boolean isKeycloakToken(String token) {
		if (!properties.isEnabled()) {
			return false;
		}
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			String issuer = jwt.getJWTClaimsSet().getIssuer();
			return properties.getIssuerUri().equals(issuer);
		}
		catch (Exception e) {
			return false;
		}
	}

	/**
	 * Validates a Keycloak JWT and returns the claims if valid.
	 * @param token The raw JWT string (without "Bearer " prefix).
	 * @return The validated claims, or {@code null} if validation fails.
	 */
	public JWTClaimsSet validateToken(String token) {
		if (!properties.isEnabled()) {
			return null;
		}
		try {
			SignedJWT jwt = SignedJWT.parse(token);
			JWTClaimsSet claims = jwt.getJWTClaimsSet();

			// 1. Check issuer
			if (!properties.getIssuerUri().equals(claims.getIssuer())) {
				log.debug("Keycloak JWT issuer mismatch: expected={}, actual={}", properties.getIssuerUri(),
						claims.getIssuer());
				return null;
			}

			// 2. Check expiration
			Date expiration = claims.getExpirationTime();
			if (expiration == null || expiration.toInstant().isBefore(Instant.now())) {
				log.debug("Keycloak JWT expired");
				return null;
			}

			// 3. Check audience (if configured)
			String audience = properties.getAudience();
			if (audience != null && !audience.isBlank()) {
				List<String> tokenAudience = claims.getAudience();
				if (tokenAudience == null || !tokenAudience.contains(audience)) {
					log.debug("Keycloak JWT audience mismatch: expected={}, actual={}", audience, tokenAudience);
					return null;
				}
			}

			// 4. Verify signature using JWKS
			JWKSet jwkSet = getJwkSet();
			if (jwkSet == null) {
				log.warn("Could not fetch Keycloak JWKS — rejecting token");
				return null;
			}

			// Select the matching key
			JWKMatcher matcher = new JWKMatcher.Builder().keyID(jwt.getHeader().getKeyID())
				.algorithm(JWSAlgorithm.RS256)
				.build();
			List<JWK> matches = new JWKSelector(matcher).select(jwkSet);

			if (matches.isEmpty()) {
				// Key might have rotated — force refresh and retry once
				jwkSet = refreshJwkSet();
				if (jwkSet == null) {
					return null;
				}
				matches = new JWKSelector(matcher).select(jwkSet);
				if (matches.isEmpty()) {
					log.warn("No matching JWK found for kid={}", jwt.getHeader().getKeyID());
					return null;
				}
			}

			RSAKey rsaKey = matches.get(0).toRSAKey();
			JWSVerifier verifier = new RSASSAVerifier(rsaKey);

			if (!jwt.verify(verifier)) {
				log.debug("Keycloak JWT signature verification failed");
				return null;
			}

			log.debug("Keycloak JWT validated successfully for sub={}", claims.getSubject());
			return claims;
		}
		catch (Exception e) {
			log.warn("Keycloak JWT validation error: {}", e.getMessage());
			return null;
		}
	}

	/**
	 * Extracts the preferred_username / email / name from Keycloak claims.
	 */
	public Map<String, String> extractUserInfo(JWTClaimsSet claims) {
		try {
			String sub = claims.getSubject(); // Keycloak user ID
			String preferredUsername = (String) claims.getClaim("preferred_username");
			String email = (String) claims.getClaim("email");
			String name = (String) claims.getClaim("name");
			String givenName = (String) claims.getClaim("given_name");
			String familyName = (String) claims.getClaim("family_name");

			// Extract realm roles from the token — supports both standard
			// "realm_access.roles" (nested) and our custom "realm_roles" (flat list)
			List<String> roles = Collections.emptyList();
			Object realmAccess = claims.getClaim("realm_access");
			if (realmAccess instanceof Map<?, ?> ra) {
				Object rolesObj = ra.get("roles");
				if (rolesObj instanceof List<?> rolesList) {
					roles = rolesList.stream().filter(String.class::isInstance).map(String.class::cast).toList();
				}
			}
			if (roles.isEmpty()) {
				Object realmRoles = claims.getClaim("realm_roles");
				if (realmRoles instanceof List<?> rolesList) {
					roles = rolesList.stream().filter(String.class::isInstance).map(String.class::cast).toList();
				}
			}

			Map<String, String> info = new ConcurrentHashMap<>();
			info.put("sub", sub != null ? sub : "");
			info.put("username", preferredUsername != null ? preferredUsername : sub);
			info.put("email", email != null ? email : "");
			info.put("name", name != null ? name : preferredUsername);
			info.put("given_name", givenName != null ? givenName : "");
			info.put("family_name", familyName != null ? familyName : "");
			info.put("roles", String.join(",", roles));
			return info;
		}
		catch (Exception e) {
			log.warn("Failed to extract user info from Keycloak claims: {}", e.getMessage());
			return Collections.emptyMap();
		}
	}

	private JWKSet getJwkSet() {
		JWKSet current = cachedJwkSet.get();
		long now = System.currentTimeMillis();
		long ttlMs = properties.getJwksCacheTtlSeconds() * 1000;

		if (current != null && (now - jwkSetFetchedAt) < ttlMs) {
			return current;
		}
		return refreshJwkSet();
	}

	private synchronized JWKSet refreshJwkSet() {
		try {
			log.info("Fetching Keycloak JWKS from {}", properties.getJwksUri());
			JWKSet jwkSet = JWKSet.load(new URL(properties.getJwksUri()));
			cachedJwkSet.set(jwkSet);
			jwkSetFetchedAt = System.currentTimeMillis();
			return jwkSet;
		}
		catch (Exception e) {
			log.error("Failed to fetch Keycloak JWKS from {}: {}", properties.getJwksUri(), e.getMessage());
			return cachedJwkSet.get(); // return stale if available
		}
	}

}
