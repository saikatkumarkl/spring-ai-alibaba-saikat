package com.alibaba.cloud.ai.studio.admin.controller;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.admin.dto.ExperimentEvaluatorResultDetail;
import com.alibaba.cloud.ai.studio.admin.dto.request.ExperimentCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.Experiment;
import com.alibaba.cloud.ai.studio.admin.dto.ExperimentEvaluatorResult;
import com.alibaba.cloud.ai.studio.admin.dto.request.ExperimentEvaluatorResultDetailListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.ExperimentListRequest;
import com.alibaba.cloud.ai.studio.admin.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ExperimentController {

    private final ExperimentService experimentService;

    /**
     * Create an experiment
     */
    @PostMapping("/experiment")
    public Result<Experiment> create(@RequestBody ExperimentCreateRequest request) {
        log.info("Create experiment request: {}", request);
        try {
            Experiment experiment = experimentService.create(request);
            return Result.success(experiment);
        } catch (Exception e) {
            log.error("Failed to create experiment", e);
            return Result.error("Failed to create experiment:" + e.getMessage());
        }
    }

    /**
     * Get experiment list
     */
    @GetMapping("/experiments")
    public Result<PageResult<Experiment>> list(ExperimentListRequest request) {
        log.info("Query experiment list request: {}", request);
        try {

            PageResult<Experiment> result = experimentService.list(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to query the experiment list", e);
            return Result.error("Failed to query the experiment list:" + e.getMessage());
        }
    }

    /**
     * Get experiment details
     */
    @GetMapping("/experiment")
    public Result<Experiment> get(@RequestParam(value = "experimentId") Long experimentId) {
        log.info("Query experiment details request: {}", experimentId);
        try {
            Experiment experiment = experimentService.getById(experimentId);
            if (experiment == null) {
                return Result.error(404, "Experiment does not exist");
            }
            return Result.success(experiment);
        } catch (Exception e) {
            log.error("Failed to query experiment details", e);
            return Result.error("Failed to query experiment details:" + e.getMessage());
        }
    }

    /**
     * Get experiment overview results
     */
    @GetMapping("/experiment/results")
    public Result<List<ExperimentEvaluatorResult>> getResults(
            @RequestParam(value = "experimentId") Long experimentId) {
        log.info("Query experiment result request: experimentId={}", experimentId);
        try {

            List<ExperimentEvaluatorResult> result = experimentService.getResults(experimentId);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to query experimental results", e);
            return Result.error("Failed to query experimental results:" + e.getMessage());
        }
    }


    /**
     * Get experimental detailed results
     */
    @GetMapping("/experiment/result")
    public Result<PageResult<ExperimentEvaluatorResultDetail>> getResult(@Validated ExperimentEvaluatorResultDetailListRequest request) {
        log.info("Query experiment result request details: experimentId={}, evaluatorVersionId={}", request.getExperimentId(), request.getEvaluatorVersionId());
        try {

            PageResult<ExperimentEvaluatorResultDetail> result = experimentService.getResult(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Failed to query experimental results", e);
            return Result.error("Failed to query experimental results:" + e.getMessage());
        }
    }


    /**
     * Stop experiment
     */
    @PutMapping("/experiment/stop")
    public Result<Experiment> stop(@RequestParam(value = "experimentId") Long experimentId) {
        log.info("Stop experiment request: {}", experimentId);
        try {
            Experiment experiment = experimentService.stop(experimentId);
            return Result.success(experiment);
        } catch (Exception e) {
            log.error("Stop experiment failed", e);
            return Result.error("Stopping experiment failed:" + e.getMessage());
        }
    }

    /**
     * Delete experiment
     */
    @DeleteMapping("/experiment")
    public Result<Void> delete(@RequestParam(value = "experimentId") Long experimentId) {
        log.info("Delete experiment request: {}", experimentId);
        try {
            experimentService.deleteById(experimentId);
            return Result.success();
        } catch (Exception e) {
            log.error("Delete experiment failed", e);
            return Result.error("Delete experiment failed:" + e.getMessage());
        }
    }

    /**
     * Delete experiment
     */
    @PutMapping("/experiment/restart")
    public Result<Void> restart(@RequestParam(value = "experimentId") Long experimentId) {
        log.info("Restart experiment: {}", experimentId);
        try {
            experimentService.restartById(experimentId);
            return Result.success();
        } catch (Exception e) {
            log.error("Restart experiment", e);
            return Result.error("Restart the experiment:" + e.getMessage());
        }
    }
}
