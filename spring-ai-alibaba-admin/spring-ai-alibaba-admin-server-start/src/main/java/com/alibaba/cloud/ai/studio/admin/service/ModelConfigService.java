package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.ModelConfigResponse;
import com.alibaba.cloud.ai.studio.admin.dto.request.ModelConfigCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ModelConfigQueryRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ModelConfigUpdateRequest;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;

import java.util.List;

public interface ModelConfigService {

    /**
     * Create model configuration
     *
     * @param request create request
     * @return model configuration response
     */
    ModelConfigResponse create(ModelConfigCreateRequest request) throws StudioException;

    /**
     * Update model configuration
     *
     * @param request update request
     * @return model configuration response
     */
    ModelConfigResponse update(ModelConfigUpdateRequest request) throws StudioException;

    /**
     * Delete model configuration
     *
     * @param id model configuration ID
     */
    void delete(Long id) throws StudioException;

    /**
     * Get model configuration list
     *
     * @param request query request
     * @return paginated results
     */
    PageResult<ModelConfigResponse> list(ModelConfigQueryRequest request);

    /**
     * Get model configuration details based on ID
     *
     * @param id model configuration ID
     * @return model configuration response
     */
    ModelConfigResponse getById(Long id) throws StudioException;

    /**
     * Get the list of enabled model configurations
     *
     * @return enabled model configuration list
     */
    List<ModelConfigResponse> getEnabledConfigs();
    
    /**
     * List of supported model providers
     *
     * @return list of supported model providers
     */
    List<String> getSupportedProviders();
    
}
