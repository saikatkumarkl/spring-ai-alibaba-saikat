/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.studio.admin.builder.controller;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.alibaba.cloud.ai.studio.admin.config.KeycloakProperties;
import com.alibaba.cloud.ai.studio.runtime.enums.UploadType;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.core.config.StudioProperties;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

@Slf4j
@RestController
@Tag(name = "system")
@RequestMapping("/console/v1/system")
@RequiredArgsConstructor
public class SystemController {

	private final StudioProperties studioProperties;

	private final KeycloakProperties keycloakProperties;

	private final ObjectMapper objectMapper;

	private final HttpClient httpClient = HttpClient.newHttpClient();

	@GetMapping("/global-config")
	public Result<GlobalConfig> globalConfig() {
		GlobalConfig globalConfig = new GlobalConfig();
		if (StringUtils.isBlank(studioProperties.getLoginMethod())) {
			globalConfig.setLoginMethod(LoginMethodEnum.preset_account.name());
		}
		else {
			LoginMethodEnum loginMethodEnum = LoginMethodEnum.valueOf(studioProperties.getLoginMethod());
			globalConfig.setLoginMethod(loginMethodEnum.name());
		}

		if (StringUtils.isBlank(studioProperties.getUploadMethod())) {
			globalConfig.setUploadMethod(UploadType.FILE.name());
		}
		else {
			UploadType uploadMethodEnum = UploadType.fromValue(studioProperties.getUploadMethod());
			globalConfig.setUploadMethod(uploadMethodEnum.name().toLowerCase());
		}

		return Result.success(globalConfig);
	}

	/**
	 * Returns Keycloak OIDC configuration for frontend clients. The frontend uses this to
	 * initialise the keycloak-js adapter. Exposed on the unauthenticated
	 * /console/v1/system/** path.
	 */
	@GetMapping("/keycloak-config")
	public Result<KeycloakConfig> keycloakConfig() {
		KeycloakConfig config = new KeycloakConfig();
		config.setEnabled(keycloakProperties.isEnabled());
		if (keycloakProperties.isEnabled()) {
			// Return the browser-accessible public URL, not the Docker-internal URL.
			config.setUrl(keycloakProperties.getEffectivePublicUrl());
			config.setRealm(keycloakProperties.getRealm());
		}
		return Result.success(config);
	}

	@Data
	public static class KeycloakConfig implements Serializable {

		private boolean enabled;

		private String url;

		private String realm;

	}

	@Data
	public static class GlobalConfig implements Serializable {

		@JsonProperty("login_method")
		private String loginMethod;

		@JsonProperty("upload_method")
		private String uploadMethod;

	}

	enum LoginMethodEnum {

		third_party, preset_account

	}

	/**
	 * Returns the list of configured Identity Providers (e.g. Azure AD, Google) from
	 * Keycloak. The frontend uses this to render specific SSO buttons that bypass the
	 * Keycloak login page via {@code kc_idp_hint}. Exposed on the unauthenticated
	 * {@code /console/v1/system/**} path so the login page can fetch it.
	 */
	@Operation(summary = "List configured SSO identity providers from Keycloak")
	@GetMapping("/sso-providers")
	public Result<List<SsoProvider>> ssoProviders() {
		if (!keycloakProperties.isEnabled()) {
			return Result.success(Collections.emptyList());
		}
		try {
			String adminToken = getAdminToken();
			String baseUrl = keycloakProperties.getAuthServerUrl();
			String realm = keycloakProperties.getRealm();

			String url = baseUrl + "/admin/realms/" + realm + "/identity-provider/instances";
			HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.header("Authorization", "Bearer " + adminToken)
				.GET()
				.build();

			HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
			if (resp.statusCode() != 200) {
				log.error("Failed to list IdPs from Keycloak: {} {}", resp.statusCode(), resp.body());
				return Result.success(Collections.emptyList());
			}

			List<Map<String, Object>> rawIdps = objectMapper.readValue(resp.body(), new TypeReference<>() {
			});

			List<SsoProvider> providers = new ArrayList<>();
			for (Map<String, Object> raw : rawIdps) {
				if (!Boolean.TRUE.equals(raw.get("enabled"))) {
					continue;
				}
				SsoProvider p = new SsoProvider();
				p.setAlias((String) raw.get("alias"));
				p.setDisplayName((String) raw.get("displayName"));
				p.setProviderId((String) raw.get("providerId"));
				providers.add(p);
			}
			return Result.success(providers);
		}
		catch (Exception e) {
			log.error("Error listing SSO providers from Keycloak", e);
			return Result.success(Collections.emptyList());
		}
	}

	/**
	 * Obtain a Keycloak master-realm admin token via resource owner password credentials
	 * grant.
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

	@Data
	public static class SsoProvider implements Serializable {

		/** Keycloak alias used as kc_idp_hint (e.g. "azure-ad", "google") */
		private String alias;

		/** Human-readable display name (e.g. "Azure Active Directory") */
		private String displayName;

		/** Keycloak provider type (e.g. "oidc", "saml", "google") */
		private String providerId;

	}

	@GetMapping("/health")
	public String health() {
		return "ok";
	}

}
