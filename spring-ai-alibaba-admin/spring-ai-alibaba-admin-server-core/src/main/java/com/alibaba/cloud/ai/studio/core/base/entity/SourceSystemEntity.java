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

package com.alibaba.cloud.ai.studio.core.base.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.util.Date;

/**
 * Entity representing an external source system (ManifoldCF repository connection).
 * Bridges the admin platform with ManifoldCF for document crawling.
 */
@Data
@TableName("source_system")
public class SourceSystemEntity {

	@TableId(value = "id", type = IdType.AUTO)
	private Long id;

	@TableField("source_id")
	private String sourceId;

	@TableField("workspace_id")
	private String workspaceId;

	private String name;

	private String description;

	@TableField("connector_type")
	private String connectorType;

	@TableField("connector_class")
	private String connectorClass;

	private Integer status;

	@TableField("connection_config")
	private String connectionConfig;

	@TableField("test_result")
	private String testResult;

	@TableField("mcf_connection_name")
	private String mcfConnectionName;

	@TableField("mcf_output_name")
	private String mcfOutputName;

	@TableField("mcf_job_id")
	private String mcfJobId;

	@TableField("mcf_job_status")
	private String mcfJobStatus;

	@TableField("last_sync_time")
	private Date lastSyncTime;

	@TableField("sync_cron")
	private String syncCron;

	@TableField("docs_total")
	private Long docsTotal;

	@TableField("docs_processed")
	private Long docsProcessed;

	@TableField("docs_failed")
	private Long docsFailed;

	@TableField("error_message")
	private String errorMessage;

	@TableField("gmt_create")
	private Date gmtCreate;

	@TableField("gmt_modified")
	private Date gmtModified;

	private String creator;

	private String modifier;

}
