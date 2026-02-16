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

package com.alibaba.cloud.ai.studio.core.source;

import com.alibaba.cloud.ai.studio.runtime.domain.BaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.source.SourceSystem;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing source systems (ManifoldCF repository connections).
 */
public interface SourceSystemService {

	/**
	 * Create a new source system and register it in ManifoldCF.
	 */
	String createSourceSystem(SourceSystem source);

	/**
	 * Update an existing source system.
	 */
	void updateSourceSystem(SourceSystem source);

	/**
	 * Delete a source system and its ManifoldCF connection.
	 */
	void deleteSourceSystem(String sourceId);

	/**
	 * Get a source system by ID.
	 */
	SourceSystem getSourceSystem(String sourceId);

	/**
	 * List source systems with pagination.
	 */
	PagingList<SourceSystem> listSourceSystems(BaseQuery query);

	/**
	 * Get available connector types from ManifoldCF.
	 */
	List<Map<String, String>> getConnectorTypes();

	/**
	 * Test the connection to the source system.
	 */
	Map<String, String> testConnection(String sourceId);

	/**
	 * Start a sync/crawl job for a source system.
	 */
	String startSync(String sourceId, String query);

	/**
	 * Get the current sync status of a source system.
	 */
	Map<String, String> getSyncStatus(String sourceId);

	/**
	 * Abort a running sync job.
	 */
	void abortSync(String sourceId);

	/**
	 * Update the sync cron schedule for a source system.
	 */
	void updateSyncSchedule(String sourceId, String cronExpression);

	/**
	 * Enable a source system. Requires connection test to pass. If ACL enforcement is
	 * configured (groupApiUrl is set), the Group API must also be validated.
	 * @param sourceId the source system ID
	 * @return validation result map with "status" and detail fields
	 */
	Map<String, String> enableSourceSystem(String sourceId);

	/**
	 * Test the Group API configuration for ACL enforcement on a source system.
	 * @param sourceId the source system ID
	 * @return test result map with PASS/WARN/FAIL status and details
	 */
	Map<String, String> testGroupApi(String sourceId);

	/**
	 * Test a query against the source system (CMIS query, REST seed, or Group API).
	 * Returns item count and sample results.
	 * @param sourceId the source system ID
	 * @param testType "query", "groupApi", or "userApi"
	 * @param queryOverride optional custom query string
	 * @return result map with count, items, status
	 */
	Map<String, Object> testQuery(String sourceId, String testType, String queryOverride);

	/**
	 * Copy an existing source system. Creates a new source with " (Copy)" appended to
	 * the name and all configuration duplicated. The new source starts in Draft status.
	 * @param sourceId the ID of the source to copy
	 * @return the new source ID
	 */
	String copySourceSystem(String sourceId);

}
