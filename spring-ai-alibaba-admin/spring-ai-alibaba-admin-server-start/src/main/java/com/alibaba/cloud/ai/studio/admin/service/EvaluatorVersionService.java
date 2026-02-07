package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.EvaluatorVersion;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorVersionCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorVersionListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorVersionUpdateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorVersionDO;

public interface EvaluatorVersionService {

    /**
     * Create evaluator version
     */
    EvaluatorVersion create(EvaluatorVersionCreateRequest request);

    /**
     * Paginated query evaluator list
     */
    PageResult<EvaluatorVersion>list(EvaluatorVersionListRequest request);

    /**
     * Get evaluator version by ID
     */
    EvaluatorVersion getById(Long id);

    /**
     * Update evaluator version
     */
    EvaluatorVersion update(EvaluatorVersionUpdateRequest request);



} 
