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

package com.alibaba.cloud.ai.studio.admin.builder.controller;

import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.core.base.entity.ModelEntity;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ModelConfigInfo;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ModelCredential;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ProviderConfigInfo;
import com.alibaba.cloud.ai.studio.core.base.manager.ModelManager;
import com.alibaba.cloud.ai.studio.core.base.manager.ProviderManager;
import com.alibaba.cloud.ai.studio.core.utils.common.IdGenerator;
import com.alibaba.cloud.ai.studio.runtime.utils.JsonUtils;
import com.google.common.collect.Lists;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.CollectionUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.ResponseEntity;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Model Management Controller This controller provides APIs for managing and retrieving
 * model information. It supports: 1. Model selection by type and provider 2. Grouping
 * models by provider 3. Filtering enabled providers and their models
 *
 * @since 1.0.0.3
 */
@Slf4j
@RestController
@Tag(name = "model_function")
@RequestMapping("/console/v1/models")
public class ModelController {

	private final ModelManager modelManager;

	private final ProviderManager providerManager;

	public ModelController(ModelManager modelManager, ProviderManager providerManager) {
		this.modelManager = modelManager;
		this.providerManager = providerManager;
	}

	/**
	 * Model Selector API Retrieves a list of models grouped by their providers for a
	 * specific model type. Only returns models from enabled providers.
	 *
	 * For providers with a configured endpoint (e.g. Ollama), this method auto-discovers
	 * models at runtime by querying the provider's API, then syncs the DB:
	 * - New models found in the provider are auto-registered
	 * - Models no longer served by the provider are auto-removed
	 *
	 * @param modelType The type of models to retrieve (e.g., "llm", "text_embedding")
	 * @return Result containing a list of ModelProviderGroup objects
	 */
	@GetMapping("/{modelType}/selector")
	public Result<List<ModelProviderGroup>> getModelSelector(@PathVariable("modelType") String modelType) {
		try {
			List<ProviderConfigInfo> providers = providerManager.queryProviders(null);
			if (CollectionUtils.isEmpty(providers)) {
				return Result.success(Lists.newArrayList());
			}
			List<ProviderConfigInfo> enableProviders = providers.stream()
				.filter(provider -> BooleanUtils.isTrue(provider.getEnable()))
				.toList();
			if (CollectionUtils.isEmpty(enableProviders)) {
				return Result.success(Lists.newArrayList());
			}

			// Auto-sync models from providers that have a live endpoint
			for (ProviderConfigInfo provider : enableProviders) {
				syncModelsFromProvider(provider);
			}

			List<ModelConfigInfo> allModels = modelManager.queryModels(null);
			if (CollectionUtils.isEmpty(allModels)) {
				return Result.success(Lists.newArrayList());
			}
			// group by provider
			Map<String, List<ModelConfigInfo>> groupedModels = allModels.stream()
				.filter(model -> model.getType().equals(modelType))
				.collect(Collectors.groupingBy(ModelConfigInfo::getProvider));
			List<ModelProviderGroup> modelProviderGroups = Lists.newArrayList();
			for (ProviderConfigInfo providerConfig : enableProviders) {
				if (!CollectionUtils.isEmpty(groupedModels.get(providerConfig.getProvider()))) {
					ModelProviderGroup modelProviderGroup = new ModelProviderGroup();
					modelProviderGroup.setProvider(providerConfig);
					modelProviderGroup.setModels(groupedModels.get(providerConfig.getProvider()));
					modelProviderGroups.add(modelProviderGroup);
				}
			}

			if (CollectionUtils.isEmpty(modelProviderGroups)) {
				return Result.success(Lists.newArrayList());
			}
			return Result.success(modelProviderGroups);
		}
		catch (BizException e) {
			return Result.error(IdGenerator.uuid(), e.getError());
		}
		catch (Exception e) {
			log.error("getModelSelector error", e);
			return Result.error(IdGenerator.uuid(), ErrorCode.SYSTEM_ERROR);
		}
	}

	/**
	 * Auto-sync models from a provider's live endpoint.
	 * Queries the provider's API to discover what models are actually available,
	 * then adds missing ones and removes stale ones from the DB.
	 */
	@SuppressWarnings("unchecked")
	private void syncModelsFromProvider(ProviderConfigInfo provider) {
		// Only sync providers that have an endpoint configured
		ModelCredential credential = provider.getCredential();
		if (credential == null || StringUtils.isBlank(credential.getEndpoint())) {
			return;
		}

		String baseUrl = credential.getEndpoint().replaceAll("/+$", "");
		String providerName = provider.getProvider();

		try {
			List<String> liveModelNames = fetchLiveModels(baseUrl);
			if (liveModelNames == null || liveModelNames.isEmpty()) {
				return;
			}

			// Get current DB models for this provider
			List<ModelConfigInfo> dbModels = modelManager.queryModels(providerName);
			Set<String> dbModelIds = dbModels.stream()
				.map(ModelConfigInfo::getModelId)
				.collect(Collectors.toSet());
			Set<String> liveModelIds = new HashSet<>(liveModelNames);

			// Add models that are in Ollama but not in DB
			for (String modelName : liveModelNames) {
				if (!dbModelIds.contains(modelName)) {
					String type = inferModelType(modelName);
					ModelConfigInfo newModel = new ModelConfigInfo();
					newModel.setModelId(modelName);
					newModel.setName(modelName);
					newModel.setProvider(providerName);
					newModel.setType(type);
					newModel.setEnable(true);
					newModel.setTags(inferTags(modelName, type));
					newModel.setSource("auto");
					modelManager.addModel(newModel);
					log.info("[AUTO-SYNC] Registered new model from {}: {} (type={})", providerName, modelName, type);
				}
			}

			// Fix misclassified model types (e.g., rerank model registered as llm)
			Map<String, ModelConfigInfo> dbModelMap = dbModels.stream()
				.collect(Collectors.toMap(ModelConfigInfo::getModelId, m -> m, (a, b) -> a));
			for (String modelName : liveModelNames) {
				ModelConfigInfo existing = dbModelMap.get(modelName);
				if (existing != null) {
					String inferredType = inferModelType(modelName);
					if (!inferredType.equals(existing.getType())) {
						existing.setType(inferredType);
						existing.setTags(inferTags(modelName, inferredType));
						modelManager.updateModel(existing);
						log.info("[AUTO-SYNC] Fixed model type for {}: {} -> {}", providerName, modelName,
								inferredType);
					}
				}
			}

			// Remove models from DB that are no longer served by the provider
			for (ModelConfigInfo dbModel : dbModels) {
				if (!liveModelIds.contains(dbModel.getModelId())) {
					modelManager.deleteModel(providerName, dbModel.getModelId());
					log.info("[AUTO-SYNC] Removed stale model from {}: {}", providerName, dbModel.getModelId());
				}
			}
		}
		catch (Exception e) {
			log.warn("[AUTO-SYNC] Failed to sync models from {} ({}): {}", providerName, baseUrl, e.getMessage());
		}
	}

	/**
	 * Fetch live model names from a provider endpoint.
	 * Tries OpenAI-compatible /v1/models first, then falls back to Ollama's /api/tags.
	 */
	@SuppressWarnings("unchecked")
	private List<String> fetchLiveModels(String baseUrl) {
		RestTemplate restTemplate = new RestTemplate();

		// Try OpenAI-compatible /v1/models first
		try {
			String url = baseUrl + "/v1/models";
			ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
			Map<String, Object> body = response.getBody();
			if (body != null && body.containsKey("data")) {
				List<Map<String, Object>> data = (List<Map<String, Object>>) body.get("data");
				return data.stream()
					.map(m -> (String) m.get("id"))
					.filter(StringUtils::isNotBlank)
					// Normalize — remove ":latest" suffix for consistency
					.map(name -> name.endsWith(":latest") ? name.substring(0, name.length() - 7) : name)
					.collect(Collectors.toList());
			}
		}
		catch (Exception e) {
			// Fall through to Ollama API
		}

		// Fallback: Ollama-specific /api/tags
		try {
			String url = baseUrl + "/api/tags";
			ResponseEntity<Map> response = restTemplate.getForEntity(url, Map.class);
			Map<String, Object> body = response.getBody();
			if (body != null && body.containsKey("models")) {
				List<Map<String, Object>> models = (List<Map<String, Object>>) body.get("models");
				return models.stream()
					.map(m -> (String) m.get("name"))
					.filter(StringUtils::isNotBlank)
					// Normalize — remove ":latest" suffix for consistency
					.map(name -> name.endsWith(":latest") ? name.substring(0, name.length() - 7) : name)
					.collect(Collectors.toList());
			}
		}
		catch (Exception e) {
			log.debug("Failed to fetch models from {}: {}", baseUrl, e.getMessage());
		}

		return Collections.emptyList();
	}

	/**
	 * Infer model type from its name.
	 * Embedding models: nomic-embed-*, mxbai-embed-*, all-minilm*, bge-*
	 * Vision models: llava*, bakllava*
	 * Everything else: llm
	 */
	private String inferModelType(String modelName) {
		String lower = modelName.toLowerCase();
		if (lower.contains("embed") || lower.startsWith("all-minilm") || lower.startsWith("bge-")
				|| lower.startsWith("snowflake-arctic-embed") || lower.contains("e5-")) {
			return "text_embedding";
		}
		if (lower.startsWith("llava") || lower.startsWith("bakllava")) {
			return "llm"; // vision models are still LLMs
		}
		if (lower.contains("rerank")) {
			return "rerank";
		}
		return "llm";
	}

	/**
	 * Infer tags from model name and type.
	 */
	private List<String> inferTags(String modelName, String type) {
		if ("text_embedding".equals(type)) {
			return List.of("embedding");
		}
		if ("rerank".equals(type)) {
			return List.of("rerank");
		}
		String lower = modelName.toLowerCase();
		if (lower.contains("deepseek-r1") || lower.contains("reasoning")) {
			return List.of("reasoning");
		}
		if (lower.startsWith("llava") || lower.startsWith("bakllava")) {
			return List.of("vision");
		}
		if (lower.contains("coder") || lower.contains("codestral") || lower.contains("starcoder")) {
			return List.of("function_call");
		}
		return List.of("function_call");
	}

	/**
	 * Get all enabled models for prompt usage
	 * This API returns models in a format compatible with the legacy prompt API
	 * @return Result containing a list of models with id field (Long) for prompt compatibility
	 */
	@GetMapping("/enabled")
	public Result<List<Map<String, Object>>> getEnabledModels() {
		try {
			List<ModelEntity> modelEntities = modelManager.queryEnabledModelEntities();
			if (CollectionUtils.isEmpty(modelEntities)) {
				return Result.success(Lists.newArrayList());
			}
			
			// Convert to format compatible with legacy prompt API
			List<Map<String, Object>> models = modelEntities.stream().map(entity -> {
				Map<String, Object> model = new HashMap<>();
				model.put("id", entity.getId()); // Long id for prompt compatibility
				model.put("name", entity.getName());
				model.put("provider", entity.getProvider());
				model.put("modelName", entity.getModelId()); // model_id as modelName for compatibility
				model.put("baseUrl", ""); // Not available in ModelEntity
				model.put("defaultParameters", new HashMap<>()); // Not available in ModelEntity
				model.put("supportedParameters", Lists.newArrayList()); // Not available in ModelEntity
				model.put("status", (entity.getEnable() != null && entity.getEnable() == 1) ? 1 : 0);
				return model;
			}).collect(Collectors.toList());
			
			return Result.success(models);
		}
		catch (BizException e) {
			return Result.error(IdGenerator.uuid(), e.getError());
		}
		catch (Exception e) {
			log.error("getEnabledModels error", e);
			return Result.error(IdGenerator.uuid(), ErrorCode.SYSTEM_ERROR);
		}
	}

	/**
	 * Model Provider Group Data structure representing a group of models under a specific
	 * provider
	 */
	@Data
	public static class ModelProviderGroup {

		private ProviderConfigInfo provider;

		private List<ModelConfigInfo> models;

	}

}
