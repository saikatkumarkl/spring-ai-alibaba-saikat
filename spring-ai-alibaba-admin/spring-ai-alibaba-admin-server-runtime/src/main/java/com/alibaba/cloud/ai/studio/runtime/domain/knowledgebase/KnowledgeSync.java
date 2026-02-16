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

package com.alibaba.cloud.ai.studio.runtime.domain.knowledgebase;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * DTO representing a knowledge sync job that orchestrates source→destination indexing.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KnowledgeSync implements Serializable {

	private static final long serialVersionUID = 1L;

	@JsonProperty("sync_id")
	private String syncId;

	@JsonProperty("workspace_id")
	private String workspaceId;

	@JsonProperty("kb_id")
	private String kbId;

	@JsonProperty("source_id")
	private String sourceId;

	@JsonProperty("destination_id")
	private String destinationId;

	@JsonProperty("sync_cron")
	private String syncCron;

	@JsonProperty("index_name")
	private String indexName;

	@JsonProperty("authority_index_name")
	private String authorityIndexName;

	@JsonProperty("rag_index_name")
	private String ragIndexName;

	@JsonProperty("mcf_job_id")
	private String mcfJobId;

	/**
	 * Status: pending, indexing, rag_processing, completed, failed.
	 */
	private String status;

	@JsonProperty("index_progress")
	private Integer indexProgress;

	@JsonProperty("rag_progress")
	private Integer ragProgress;

	@JsonProperty("total_docs")
	private Long totalDocs;

	@JsonProperty("indexed_docs")
	private Long indexedDocs;

	@JsonProperty("rag_docs")
	private Long ragDocs;

	@JsonProperty("failed_docs")
	private Long failedDocs;

	@JsonProperty("error_message")
	private String errorMessage;

	@JsonProperty("last_sync_time")
	private Date lastSyncTime;

	@JsonProperty("gmt_create")
	private Date gmtCreate;

	@JsonProperty("gmt_modified")
	private Date gmtModified;

	private String creator;

	private String modifier;

}
