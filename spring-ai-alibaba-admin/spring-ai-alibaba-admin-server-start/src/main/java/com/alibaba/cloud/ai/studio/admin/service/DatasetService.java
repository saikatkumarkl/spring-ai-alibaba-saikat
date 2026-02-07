package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.Dataset;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetUpdateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.DatasetDO;

public interface DatasetService {

    /**
     * Create a review set
     */
    Dataset create(DatasetCreateRequest request);

    /**
     * Query the evaluation set list by pagination
     */
    PageResult<Dataset> list(DatasetListRequest request);

    /**
     * Get the evaluation set based on ID
     */
    Dataset getById(Long id);

    /**
     * Update review set
     */
    Dataset update(DatasetUpdateRequest request);

    /**
     * Delete a review set based on ID
     */
    void deleteById(Long id);
} 
