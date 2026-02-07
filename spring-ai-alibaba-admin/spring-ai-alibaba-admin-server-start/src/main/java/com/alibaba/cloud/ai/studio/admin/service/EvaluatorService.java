package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.EvaluatorDebugResult;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorTestRequest;
import com.alibaba.cloud.ai.studio.admin.dto.Evaluator;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorUpdateRequest;

import java.util.Map;

public interface EvaluatorService {

    /**
     * Create evaluator
     */
    Evaluator create(EvaluatorCreateRequest request);

    /**
     * Paginated query evaluator list
     */
    PageResult<Evaluator> list(EvaluatorListRequest request);

    /**
     * Get evaluator based on ID
     */
    Evaluator getById(Long id);

    /**
     * Update evaluator
     */
    Evaluator update(EvaluatorUpdateRequest request);

    /**
     * Remove evaluator based on ID
     */
    void deleteById(Long id);

    /**
     * Debug evaluator
     */
    EvaluatorDebugResult debug(EvaluatorTestRequest request);


} 
