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

import com.alibaba.cloud.ai.studio.core.base.entity.DestinationEntity;
import com.alibaba.cloud.ai.studio.core.base.mapper.DestinationMapper;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.runtime.domain.BaseQuery;
import com.alibaba.cloud.ai.studio.runtime.domain.PagingList;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.runtime.domain.destination.Destination;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Implementation of DestinationService. Manages destination system lifecycle and
 * connection testing.
 */
@Slf4j
@Service
public class DestinationServiceImpl extends ServiceImpl<DestinationMapper, DestinationEntity>
		implements DestinationService {

	private final ObjectMapper objectMapper;

	private final RestTemplate restTemplate;

	public DestinationServiceImpl() {
		this.objectMapper = new ObjectMapper();
		this.restTemplate = new RestTemplate();
	}

	@Override
	public String createDestination(Destination destination) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String workspaceId = context.getWorkspaceId();
		String accountId = context.getAccountId();

		// Check name uniqueness within workspace
		LambdaQueryWrapper<DestinationEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(DestinationEntity::getWorkspaceId, workspaceId)
			.eq(DestinationEntity::getName, destination.getName())
			.ne(DestinationEntity::getStatus, -1);
		if (this.count(wrapper) > 0) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("name", "Destination name already exists"));
		}

		String destinationId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);

		DestinationEntity entity = new DestinationEntity();
		entity.setDestinationId(destinationId);
		entity.setWorkspaceId(workspaceId);
		entity.setName(destination.getName());
		entity.setDescription(destination.getDescription());
		entity.setProviderType(destination.getProviderType() != null ? destination.getProviderType() : "opensearch");
		entity.setStatus(1);
		entity.setConnectionConfig(serializeConfig(destination.getConnectionConfig()));
		entity.setGmtCreate(new Date());
		entity.setGmtModified(new Date());
		entity.setCreator(accountId);
		entity.setModifier(accountId);

		this.save(entity);
		log.info("Created destination '{}' (destinationId={})", destination.getName(), destinationId);

		return destinationId;
	}

	@Override
	public void updateDestination(Destination destination) {
		RequestContext context = RequestContextHolder.getRequestContext();

		DestinationEntity entity = findByDestinationId(destination.getDestinationId());
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}

		if (StringUtils.isNotBlank(destination.getName())) {
			entity.setName(destination.getName());
		}
		if (destination.getDescription() != null) {
			entity.setDescription(destination.getDescription());
		}
		if (destination.getProviderType() != null) {
			entity.setProviderType(destination.getProviderType());
		}
		if (destination.getConnectionConfig() != null) {
			entity.setConnectionConfig(serializeConfig(destination.getConnectionConfig()));
		}
		entity.setGmtModified(new Date());
		entity.setModifier(context.getAccountId());

		this.updateById(entity);
		log.info("Updated destination '{}'", destination.getDestinationId());
	}

	@Override
	public void deleteDestination(String destinationId) {
		DestinationEntity entity = findByDestinationId(destinationId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}

		// Soft delete
		entity.setStatus(-1);
		entity.setGmtModified(new Date());
		this.updateById(entity);
		log.info("Deleted destination '{}'", destinationId);
	}

	@Override
	public Destination getDestination(String destinationId) {
		DestinationEntity entity = findByDestinationId(destinationId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}
		return toDto(entity);
	}

	@Override
	public PagingList<Destination> listDestinations(BaseQuery query) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String workspaceId = context.getWorkspaceId();

		LambdaQueryWrapper<DestinationEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(DestinationEntity::getWorkspaceId, workspaceId).ne(DestinationEntity::getStatus, -1);

		if (StringUtils.isNotBlank(query.getName())) {
			wrapper.like(DestinationEntity::getName, query.getName());
		}
		wrapper.orderByDesc(DestinationEntity::getGmtModified);

		Page<DestinationEntity> page = new Page<>(query.getCurrent(), query.getSize());
		Page<DestinationEntity> result = this.page(page, wrapper);

		List<Destination> records = result.getRecords().stream().map(this::toDto).collect(Collectors.toList());

		PagingList<Destination> pagingList = new PagingList<>();
		pagingList.setRecords(records);
		pagingList.setCurrent((int) result.getCurrent());
		pagingList.setSize((int) result.getSize());
		pagingList.setTotal(result.getTotal());
		return pagingList;
	}

	@Override
	public Map<String, String> testConnection(String destinationId) {
		DestinationEntity entity = findByDestinationId(destinationId);
		if (entity == null) {
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("destination_id", "Destination not found"));
		}

		Map<String, Object> config = deserializeConfig(entity.getConnectionConfig());
		Map<String, String> result = doTestConnection(entity.getProviderType(), config);

		// Save test result
		entity.setTestResult(result.get("status"));
		entity.setGmtModified(new Date());
		this.updateById(entity);

		return result;
	}

	@Override
	public Map<String, String> testConnectionInline(Destination destination) {
		return doTestConnection(
				destination.getProviderType() != null ? destination.getProviderType() : "opensearch",
				destination.getConnectionConfig());
	}

	@Override
	public List<Map<String, String>> getProviderTypes() {
		List<Map<String, String>> types = new ArrayList<>();
		Map<String, String> opensearch = new LinkedHashMap<>();
		opensearch.put("type", "opensearch");
		opensearch.put("label", "OpenSearch");
		opensearch.put("description", "OpenSearch / Elasticsearch compatible search engine");
		types.add(opensearch);
		return types;
	}

	// ---- Private helpers ----

	private Map<String, String> doTestConnection(String providerType, Map<String, Object> config) {
		Map<String, String> result = new LinkedHashMap<>();

		if (!"opensearch".equalsIgnoreCase(providerType)) {
			result.put("status", "FAIL");
			result.put("message", "Unsupported provider type: " + providerType);
			return result;
		}

		String url = getConfigString(config, "url", "");
		String username = getConfigString(config, "username", "");
		String password = getConfigString(config, "password", "");

		if (url.isEmpty()) {
			result.put("status", "FAIL");
			result.put("message", "URL is required");
			return result;
		}

		try {
			// Test by hitting the root endpoint of OpenSearch
			HttpHeaders headers = new HttpHeaders();
			if (!username.isEmpty()) {
				String auth = Base64.getEncoder()
					.encodeToString((username + ":" + password).getBytes(StandardCharsets.UTF_8));
				headers.set("Authorization", "Basic " + auth);
			}

			HttpEntity<String> entity = new HttpEntity<>(headers);
			String testUrl = url.endsWith("/") ? url : url + "/";
			ResponseEntity<String> response = restTemplate.exchange(testUrl, HttpMethod.GET, entity, String.class);

			if (response.getStatusCode().is2xxSuccessful()) {
				result.put("status", "PASS");
				// Try to extract cluster info from response
				try {
					Map<String, Object> body = objectMapper.readValue(response.getBody(),
							new TypeReference<Map<String, Object>>() {
							});
					String clusterName = String.valueOf(body.getOrDefault("cluster_name", "unknown"));
					@SuppressWarnings("unchecked")
					Map<String, Object> version = (Map<String, Object>) body.getOrDefault("version",
							Collections.emptyMap());
					String versionNumber = String.valueOf(version.getOrDefault("number", "unknown"));
					String distribution = String.valueOf(version.getOrDefault("distribution", "elasticsearch"));
					result.put("message",
							"Connected to " + distribution + " cluster '" + clusterName + "' v" + versionNumber);
					result.put("cluster_name", clusterName);
					result.put("version", versionNumber);
					result.put("distribution", distribution);
				}
				catch (Exception e) {
					result.put("message", "Connected successfully");
				}
			}
			else {
				result.put("status", "FAIL");
				result.put("message", "HTTP " + response.getStatusCode().value());
			}
		}
		catch (Exception e) {
			result.put("status", "FAIL");
			String msg = e.getMessage();
			if (msg != null && msg.length() > 200) {
				msg = msg.substring(0, 200) + "...";
			}
			result.put("message", "Connection failed: " + msg);
		}

		return result;
	}

	private DestinationEntity findByDestinationId(String destinationId) {
		LambdaQueryWrapper<DestinationEntity> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(DestinationEntity::getDestinationId, destinationId).ne(DestinationEntity::getStatus, -1);
		return this.getOne(wrapper);
	}

	private Destination toDto(DestinationEntity entity) {
		Destination dto = new Destination();
		dto.setDestinationId(entity.getDestinationId());
		dto.setWorkspaceId(entity.getWorkspaceId());
		dto.setName(entity.getName());
		dto.setDescription(entity.getDescription());
		dto.setProviderType(entity.getProviderType());
		dto.setStatus(entity.getStatus());
		dto.setConnectionConfig(deserializeConfig(entity.getConnectionConfig()));
		dto.setTestResult(entity.getTestResult());
		dto.setGmtCreate(entity.getGmtCreate());
		dto.setGmtModified(entity.getGmtModified());
		dto.setCreator(entity.getCreator());
		dto.setModifier(entity.getModifier());
		return dto;
	}

	private String serializeConfig(Map<String, Object> config) {
		if (config == null) {
			return "{}";
		}
		try {
			return objectMapper.writeValueAsString(config);
		}
		catch (JsonProcessingException e) {
			throw new BizException(ErrorCode.SYSTEM_ERROR.toError());
		}
	}

	private Map<String, Object> deserializeConfig(String configJson) {
		if (StringUtils.isBlank(configJson)) {
			return new HashMap<>();
		}
		try {
			return objectMapper.readValue(configJson, new TypeReference<Map<String, Object>>() {
			});
		}
		catch (JsonProcessingException e) {
			return new HashMap<>();
		}
	}

	private String getConfigString(Map<String, Object> config, String key, String defaultValue) {
		if (config == null) {
			return defaultValue;
		}
		Object val = config.get(key);
		return val != null ? String.valueOf(val) : defaultValue;
	}

}
