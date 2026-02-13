/*
 * Copyright 2024-2026 the original author or authors.
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
package com.alibaba.cloud.ai.studio.core.base.manager;

import com.alibaba.cloud.ai.studio.runtime.exception.BizException;
import com.alibaba.cloud.ai.studio.runtime.enums.DataSourceEnum;
import com.alibaba.cloud.ai.studio.runtime.enums.ErrorCode;
import com.alibaba.cloud.ai.studio.runtime.domain.RequestContext;
import com.alibaba.cloud.ai.studio.core.context.RequestContextHolder;
import com.alibaba.cloud.ai.studio.core.base.entity.ModelEntity;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ModelConfigInfo;
import com.alibaba.cloud.ai.studio.core.model.llm.domain.ProviderConfigInfo;
import com.alibaba.cloud.ai.studio.core.base.mapper.ModelMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Model management service for handling model operations
 */
@Slf4j
@Component
public class ModelManager {

	@Resource
	private ProviderManager providerManager;

	@Resource
	private ModelMapper modelMapper;

	/**
	 * Add a new model
	 * @param modelConfigInfo Model configuration information
	 * @return true if model was added successfully
	 */
	public boolean addModel(ModelConfigInfo modelConfigInfo) {
		log.info("=== ADD MODEL CALLED === modelId={}, type={}, provider={}", 
			modelConfigInfo.getModelId(), modelConfigInfo.getType(), modelConfigInfo.getProvider());
		
		RequestContext context = RequestContextHolder.getRequestContext();
		//Check if the provider exists
		ProviderConfigInfo providerDetail = providerManager.getProviderDetail(modelConfigInfo.getProvider(), false);
		if (providerDetail == null) {
			log.error("提供商[{}]不存在", modelConfigInfo.getProvider());
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("input_params", "provider is invalid"));
		}

		QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("model_id", modelConfigInfo.getModelId());
		queryWrapper.eq("provider", modelConfigInfo.getProvider());
		queryWrapper.eq("workspace_id", context.getWorkspaceId());
		ModelEntity existModelEntity = modelMapper.selectOne(queryWrapper);
		
		if (existModelEntity != null) {
			// Model exists - update it instead of throwing error
			log.info("模型[{}]已存在，更新模型信息 - 当前type={}, 新type={}", 
				modelConfigInfo.getModelId(), existModelEntity.getType(), modelConfigInfo.getType());
			existModelEntity.setGmtModified(new Date());
			existModelEntity.setName(modelConfigInfo.getName());
			existModelEntity.setType(modelConfigInfo.getType());
			String tags = modelConfigInfo.getTags() != null 
				? modelConfigInfo.getTags().stream().collect(Collectors.joining(","))
				: "";
			existModelEntity.setTags(tags);
			if (modelConfigInfo.getIcon() != null) {
				existModelEntity.setIcon(modelConfigInfo.getIcon());
			}
			int update = modelMapper.updateById(existModelEntity);
			log.info("Update via addModel: rows affected={}", update);
			return update > 0;
		}

		// Model doesn't exist - create new
		log.info("创建新模型: {}", modelConfigInfo.getModelId());
		ModelEntity modelEntity = new ModelEntity();
		modelEntity.setWorkspaceId(context.getWorkspaceId());
		modelEntity.setGmtCreate(new Date());
		modelEntity.setGmtModified(new Date());
		modelEntity.setIcon(modelConfigInfo.getIcon());
		modelEntity.setName(modelConfigInfo.getName());
		modelEntity.setProvider(modelConfigInfo.getProvider());
		modelEntity.setSource(DataSourceEnum.custom.name());
		modelEntity.setEnable(1);
		modelEntity.setType(modelConfigInfo.getType());
		modelEntity.setModelId(modelConfigInfo.getModelId());
		String tags = modelConfigInfo.getTags() != null 
			? modelConfigInfo.getTags().stream().collect(Collectors.joining(","))
			: "";
		modelEntity.setTags(tags);
		int insert = modelMapper.insert(modelEntity);
		log.info("Insert result: rows affected={}", insert);
		return insert > 0;
	}

	/**
	 * Update an existing model
	 * @param modelConfigInfo Model configuration information
	 * @return true if model was updated successfully
	 */
	public boolean updateModel(ModelConfigInfo modelConfigInfo) {
		log.info("=== UPDATE MODEL CALLED === modelId={}, type={}, provider={}", 
			modelConfigInfo.getModelId(), modelConfigInfo.getType(), modelConfigInfo.getProvider());
		
		RequestContext context = RequestContextHolder.getRequestContext();
		//Check if the provider exists
		ProviderConfigInfo providerDetail = providerManager.getProviderDetail(modelConfigInfo.getProvider(), false);
		if (providerDetail == null) {
			log.error("提供商[{}]不存在", modelConfigInfo.getProvider());
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("input_params", "provider is invalid"));
		}

		//Check if the model exists
		QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("provider", providerDetail.getProvider());
		queryWrapper.eq("model_id", modelConfigInfo.getModelId());
		if (StringUtils.isNotBlank(context.getWorkspaceId())) {
			queryWrapper.eq("workspace_id", context.getWorkspaceId());
		}
		ModelEntity existingModel = modelMapper.selectOne(queryWrapper);
		if (existingModel == null) {
			log.error("模型[{}]不存在", modelConfigInfo.getModelId());
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("input_params", "model not found"));
		}

		log.info("Existing model type={}, new type={}", existingModel.getType(), modelConfigInfo.getType());

		//Update model information
		existingModel.setGmtModified(new Date());
		if (StringUtils.isNotBlank(modelConfigInfo.getName())) {
			existingModel.setName(modelConfigInfo.getName());
		}
		if (StringUtils.isNotBlank(modelConfigInfo.getProvider())) {
			existingModel.setProvider(modelConfigInfo.getProvider());
		}
		if (modelConfigInfo.getTags() != null && !modelConfigInfo.getTags().isEmpty()) {
			existingModel.setTags(modelConfigInfo.getTags().stream().collect(Collectors.joining(",")));
		}
		if (StringUtils.isNotBlank(modelConfigInfo.getIcon())) {
			existingModel.setIcon(modelConfigInfo.getIcon());
		}
		if (StringUtils.isNotBlank(modelConfigInfo.getType())) {
			log.info("Setting new type: {}", modelConfigInfo.getType());
			existingModel.setType(modelConfigInfo.getType());
		}
		if (modelConfigInfo.getEnable() != null) {
			existingModel.setEnable(modelConfigInfo.getEnable() ? 1 : 0);
		}

		int update = modelMapper.updateById(existingModel);
		log.info("Update result: rows affected={}", update);
		return update > 0;
	}

	/**
	 * Delete a model
	 * @param provider Model provider
	 * @param modelId Model ID
	 * @return true if model was deleted successfully
	 */
	public boolean deleteModel(String provider, String modelId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		//Check if the model exists
		QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("model_id", modelId);
		queryWrapper.eq("provider", provider);
		if (StringUtils.isNotBlank(context.getWorkspaceId())) {
			queryWrapper.eq("workspace_id", context.getWorkspaceId());
		}
		ModelEntity existingModel = modelMapper.selectOne(queryWrapper);
		if (existingModel == null) {
			log.error("模型[{}]不存在", modelId);
			throw new BizException(ErrorCode.INVALID_PARAMS.toError("input_params", "model not found"));
		}

		//Delete model
		int delete = modelMapper.deleteById(existingModel.getId());
		return delete > 0;
	}

	/**
	 * Query models by provider
	 * @param provider Model provider
	 * @return List of model configurations
	 */
	public List<ModelConfigInfo> queryModels(String provider) {
		RequestContext context = RequestContextHolder.getRequestContext();
		QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("workspace_id", context.getWorkspaceId());
		if (StringUtils.isNotBlank(provider)) {
			queryWrapper.eq("provider", provider);
		}

		List<ModelEntity> modelEntities = modelMapper.selectList(queryWrapper);
		return modelEntities.stream().map(this::convertToModelConfigInfo).collect(Collectors.toList());
	}

	/**
	 * Query enabled models from enabled providers
	 * @return List of enabled model configurations
	 */
	public List<ModelConfigInfo> queryEnabledModels() {
		RequestContext context = RequestContextHolder.getRequestContext();
		// Get all enabled providers
		List<ProviderConfigInfo> enabledProviders = providerManager.queryProviders(null).stream()
				.filter(provider -> Boolean.TRUE.equals(provider.getEnable()))
				.collect(Collectors.toList());
		
		if (enabledProviders.isEmpty()) {
			return new ArrayList<>();
		}
		
		// Get all enabled models from enabled providers
		QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("workspace_id", context.getWorkspaceId());
		queryWrapper.eq("enable", 1);
		List<String> enabledProviderNames = enabledProviders.stream()
				.map(ProviderConfigInfo::getProvider)
				.collect(Collectors.toList());
		if (!enabledProviderNames.isEmpty()) {
			queryWrapper.in("provider", enabledProviderNames);
		}
		
		List<ModelEntity> modelEntities = modelMapper.selectList(queryWrapper);
		return modelEntities.stream().map(this::convertToModelConfigInfo).collect(Collectors.toList());
	}

	/**
	 * Query enabled model entities from enabled providers (returns entities with id field)
	 * @return List of enabled model entities
	 */
	public List<ModelEntity> queryEnabledModelEntities() {
		RequestContext context = RequestContextHolder.getRequestContext();
		// Get all enabled providers
		List<ProviderConfigInfo> enabledProviders = providerManager.queryProviders(null).stream()
				.filter(provider -> Boolean.TRUE.equals(provider.getEnable()))
				.collect(Collectors.toList());
		
		if (enabledProviders.isEmpty()) {
			return new ArrayList<>();
		}
		
		// Get all enabled models from enabled providers
		QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
		queryWrapper.eq("workspace_id", context.getWorkspaceId());
		queryWrapper.eq("enable", 1);
		List<String> enabledProviderNames = enabledProviders.stream()
				.map(ProviderConfigInfo::getProvider)
				.collect(Collectors.toList());
		if (!enabledProviderNames.isEmpty()) {
			queryWrapper.in("provider", enabledProviderNames);
		}
		
		return modelMapper.selectList(queryWrapper);
	}

	/**
	 * Get model details
	 * @param provider Model provider
	 * @param modelId Model ID
	 * @return Model configuration information
	 */
	public ModelConfigInfo getModelDetail(String provider, String modelId) {
		RequestContext context = RequestContextHolder.getRequestContext();
		try {
			QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
			queryWrapper.eq("model_id", modelId);
			queryWrapper.eq("provider", provider);
			queryWrapper.eq("workspace_id", context.getWorkspaceId());

			ModelEntity modelEntity = modelMapper.selectOne(queryWrapper);
			if (modelEntity == null) {
				log.error("模型[{}]不存在", modelId);
				throw new BizException(ErrorCode.INVALID_PARAMS.toError("input_params", "model not found"));
			}

			return convertToModelConfigInfo(modelEntity);
		}
		catch (BizException e) {
			throw e;
		}
		catch (Exception e) {
			log.error("获取模型详情失败: " + e.getMessage(), e);
			return null;
		}
	}

	/**
	 * Find model entity by id (Long) or name (String)
	 * @param modelIdOrName Model ID (Long) or model name (String)
	 * @return Model entity if found, null otherwise
	 */
	public ModelEntity findModelByIdOrName(Object modelIdOrName) {
		RequestContext context = RequestContextHolder.getRequestContext();
		String workspaceId = (context != null) ? context.getWorkspaceId() : null;
		return findModelByIdOrName(modelIdOrName, workspaceId);
	}

	/**
	 * Find model entity by id (Long) or name (String) with optional workspaceId
	 * @param modelIdOrName Model ID (Long) or model name (String)
	 * @param workspaceId Optional workspace ID, if null then workspace filter is not applied
	 * @return Model entity if found, null otherwise
	 */
	public ModelEntity findModelByIdOrName(Object modelIdOrName, String workspaceId) {
		if (modelIdOrName == null) {
			return null;
		}
		
		QueryWrapper<ModelEntity> queryWrapper = new QueryWrapper<>();
		//Add filter condition only if workspaceId is not null
		if (StringUtils.isNotBlank(workspaceId)) {
			queryWrapper.eq("workspace_id", workspaceId);
		}
		
		if (modelIdOrName instanceof Long) {
			//Find by id
			queryWrapper.eq("id", modelIdOrName);
		} else if (modelIdOrName instanceof String) {
			//Find by name or model_id
			String value = (String) modelIdOrName;
			queryWrapper.and(wrapper -> wrapper.eq("name", value).or().eq("model_id", value));
		} else {
			return null;
		}
		
		return modelMapper.selectOne(queryWrapper);
	}

	/**
	 * Convert model entity to model configuration info
	 * @param entity Model entity
	 * @return Model configuration information
	 */
	private ModelConfigInfo convertToModelConfigInfo(ModelEntity entity) {
		ModelConfigInfo modelConfigInfo = new ModelConfigInfo();
		modelConfigInfo.setModelId(entity.getModelId());
		modelConfigInfo.setName(entity.getName());
		modelConfigInfo.setProvider(entity.getProvider());
		modelConfigInfo.setIcon(entity.getIcon());
		if (StringUtils.isNotBlank(entity.getTags())) {
			modelConfigInfo.setTags(Arrays.asList(entity.getTags().split(",")));
		}
		else {
			modelConfigInfo.setTags(new ArrayList<>());
		}
		modelConfigInfo.setType(entity.getType());
		modelConfigInfo.setSource(entity.getSource());
		modelConfigInfo.setEnable(entity.getEnable() != null && entity.getEnable() == 1);
		return modelConfigInfo;
	}

}
