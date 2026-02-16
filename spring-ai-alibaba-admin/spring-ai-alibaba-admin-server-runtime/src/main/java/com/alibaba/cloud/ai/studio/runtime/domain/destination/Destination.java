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

package com.alibaba.cloud.ai.studio.runtime.domain.destination;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * DTO representing a destination system (e.g., OpenSearch) where knowledge data is
 * indexed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Destination implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("destination_id")
	private String destinationId;

	@JsonProperty("workspace_id")
	private String workspaceId;

	private String name;

	private String description;

	/**
	 * Provider type: "opensearch" for now. Extensible to "elasticsearch", "milvus", etc.
	 */
	@JsonProperty("provider_type")
	private String providerType;

	/**
	 * Connection status: 1=active, 0=disabled, -1=deleted.
	 */
	private Integer status;

	/**
	 * Connection configuration (url, username, password, index_prefix, etc.).
	 */
	@JsonProperty("connection_config")
	private Map<String, Object> connectionConfig;

	/**
	 * Last test result: "success" or error message.
	 */
	@JsonProperty("test_result")
	private String testResult;

	@JsonProperty("gmt_create")
	private Date gmtCreate;

	@JsonProperty("gmt_modified")
	private Date gmtModified;

	private String creator;

	private String modifier;

}
