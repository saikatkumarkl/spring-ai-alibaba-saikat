package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.entity.ModelConfigDO;

import java.util.List;

/**
 * Model configuration bridge service
 * Convert the data of the underlying Manager layer (ModelManager, ProviderManager) to ModelConfigDO format
 * For use by upper-layer services such as ChatClientFactory
 */
public interface ModelConfigBridgeService {

    /**
     * Find model configuration based on ID
     * @param id model configuration ID (can be the id or name of ModelEntity)
     * @return ModelConfigDO, return null if it does not exist
     */
    ModelConfigDO findById(Long id);

    /**
     * Check if model configuration exists
     * @param id model configuration ID
     * @return true if exists, false otherwise
     */
    boolean existsById(Long id);

    /**
     * Query model configuration list (supports paging and filtering)
     * @param name model name (fuzzy matching)
     * @param provider provider name
     * @param status status: 1-enabled, 0-disabled
     * @param offset offset
     * @param limit limit quantity
     * @return model configuration list
     */
    List<ModelConfigDO> list(String name, String provider, Integer status, int offset, int limit);

    /**
     * Count the number of model configurations that meet the conditions
     * @param name model name (fuzzy matching)
     * @param provider provider name
     * @param status status: 1-enabled, 0-disabled
     * @return quantity
     */
    int count(String name, String provider, Integer status);

    /**
     * Get a list of all enabled model configurations
     * @return enabled model configuration list
     */
    List<ModelConfigDO> listEnabled();

    /**
     * Find model configuration based on provider and modelId
     * @param provider provider name
     * @param modelId model ID
     * @return ModelConfigDO, return null if it does not exist
     */
    ModelConfigDO findByProviderAndModelId(String provider, String modelId);
}

