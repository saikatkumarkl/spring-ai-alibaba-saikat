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

package com.alibaba.cloud.ai.studio.core.destination;

import com.alibaba.cloud.ai.studio.runtime.domain.BaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.destination.Destination;

import java.util.List;
import java.util.Map;

/**
 * Service interface for managing destination systems (e.g., OpenSearch) for knowledge
 * indexing.
 */
public interface DestinationService {

	/**
	 * Create a new destination.
	 */
	String createDestination(Destination destination);

	/**
	 * Update an existing destination.
	 */
	void updateDestination(Destination destination);

	/**
	 * Delete a destination.
	 */
	void deleteDestination(String destinationId);

	/**
	 * Get a destination by ID.
	 */
	Destination getDestination(String destinationId);

	/**
	 * List destinations with pagination.
	 */
	PagingList<Destination> listDestinations(BaseQuery query);

	/**
	 * Test connection to the destination.
	 */
	Map<String, String> testConnection(String destinationId);

	/**
	 * Test connection with inline config (before saving).
	 */
	Map<String, String> testConnectionInline(Destination destination);

	/**
	 * Get available provider types.
	 */
	List<Map<String, String>> getProviderTypes();

}
