package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.Experiment;
import com.alibaba.cloud.ai.studio.admin.dto.ExperimentEvaluatorResult;
import com.alibaba.cloud.ai.studio.admin.dto.ExperimentEvaluatorResultDetail;
import com.alibaba.cloud.ai.studio.admin.dto.request.ExperimentCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ExperimentEvaluatorResultDetailListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ExperimentListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorExperimentsListRequest;

import java.util.List;
import java.util.Map;

public interface ExperimentService {

    /**
     * Create an experiment
     */
    Experiment create(ExperimentCreateRequest request);

    /**
     * Query the experiment list by page
     */
    PageResult<Experiment> list(ExperimentListRequest request);

    /**
     * Get experiment by ID
     */
    Experiment getById(Long id);


    List<ExperimentEvaluatorResult> getResults(Long ExperimentId);


    PageResult<ExperimentEvaluatorResultDetail> getResult(ExperimentEvaluatorResultDetailListRequest request);



    /**
     * Stop experiment
     */
    Experiment stop(Long id);

    /**
     * Delete experiment by ID
     */
    void deleteById(Long id);


    /**
     * Delete experiment by ID
     */
    void restartById(Long id);

    /**
     * Get a list of experiments using the specified evaluator
     *
     * @param request query request
     * @return paging experimental results
     */
    PageResult<Experiment> getExperimentsByEvaluator(EvaluatorExperimentsListRequest request);
}
