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

package com.alibaba.cloud.ai.studio.runtime.domain.source;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.Map;

/**
 * DTO for source system (ManifoldCF repository connection).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SourceSystem implements Serializable {

	@JsonProperty("source_id")
	private String sourceId;

	@JsonProperty("workspace_id")
	private String workspaceId;

	private String name;

	private String description;

	@JsonProperty("connector_type")
	private String connectorType;

	@JsonProperty("connector_class")
	private String connectorClass;

	private Integer status;

	@JsonProperty("connection_config")
	private Map<String, Object> connectionConfig;

	@JsonProperty("test_result")
	private String testResult;

	@JsonProperty("mcf_connection_name")
	private String mcfConnectionName;

	@JsonProperty("mcf_output_name")
	private String mcfOutputName;

	@JsonProperty("mcf_job_id")
	private String mcfJobId;

	@JsonProperty("mcf_job_status")
	private String mcfJobStatus;

	@JsonProperty("last_sync_time")
	private Date lastSyncTime;

	@JsonProperty("sync_cron")
	private String syncCron;

	@JsonProperty("docs_total")
	private Long docsTotal;

	@JsonProperty("docs_processed")
	private Long docsProcessed;

	@JsonProperty("docs_failed")
	private Long docsFailed;

	@JsonProperty("error_message")
	private String errorMessage;

	@JsonProperty("gmt_create")
	private Date gmtCreate;

	@JsonProperty("gmt_modified")
	private Date gmtModified;

	private String creator;

	private String modifier;

}
