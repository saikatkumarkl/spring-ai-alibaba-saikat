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

package com.alibaba.cloud.ai.studio.core.rag;

import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpHeaders;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Shared OpenSearch utility methods used by both {@code KnowledgeSyncServiceImpl}
 * and {@code KnowledgeIndexSchemaFactory}.
 */
public final class OpenSearchUtils {

	private OpenSearchUtils() {
		// utility class
	}

	/**
	 * Build HTTP headers with Basic auth for OpenSearch.
	 * @param username the OpenSearch username (may be blank for anonymous access)
	 * @param password the OpenSearch password
	 * @return headers with Authorization set (if username is non-blank)
	 */
	public static HttpHeaders buildAuthHeaders(String username, String password) {
		HttpHeaders headers = new HttpHeaders();
		if (StringUtils.isNotBlank(username)) {
			// B8: Handle null password gracefully (send empty string, not literal "null")
			String safePassword = password != null ? password : "";
			String auth = Base64.getEncoder()
				.encodeToString((username + ":" + safePassword).getBytes(StandardCharsets.UTF_8));
			headers.set("Authorization", "Basic " + auth);
		}
		return headers;
	}

}
