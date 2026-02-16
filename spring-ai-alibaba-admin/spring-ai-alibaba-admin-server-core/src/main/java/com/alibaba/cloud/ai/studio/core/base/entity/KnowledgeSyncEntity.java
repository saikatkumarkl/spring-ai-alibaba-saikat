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

package com.alibaba.cloud.ai.studio.core.base.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Entity representing a knowledge sync job.
 */
@Data
@TableName("knowledge_sync")
public class KnowledgeSyncEntity {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@TableField("sync_id")
	private String syncId;

	@TableField("workspace_id")
	private String workspaceId;

	@TableField("kb_id")
	private String kbId;

	@TableField("source_id")
	private String sourceId;

	@TableField("destination_id")
	private String destinationId;

	@TableField("sync_cron")
	private String syncCron;

	@TableField("index_name")
	private String indexName;

	@TableField("authority_index_name")
	private String authorityIndexName;

	@TableField("rag_index_name")
	private String ragIndexName;

	@TableField("mcf_job_id")
	private String mcfJobId;

	private String status;

	@TableField("index_progress")
	private Integer indexProgress;

	@TableField("rag_progress")
	private Integer ragProgress;

	@TableField("total_docs")
	private Long totalDocs;

	@TableField("indexed_docs")
	private Long indexedDocs;

	@TableField("rag_docs")
	private Long ragDocs;

	@TableField("failed_docs")
	private Long failedDocs;

	@TableField(value = "error_message", updateStrategy = FieldStrategy.ALWAYS)
	private String errorMessage;

	@TableField("last_sync_time")
	private Date lastSyncTime;

	@TableField("gmt_create")
	private Date gmtCreate;

	@TableField("gmt_modified")
	private Date gmtModified;

	private String creator;

	private String modifier;

}
