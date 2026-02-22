package com.alibaba.cloud.ai.studio.admin.controller;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.runtime.domain.Result;
import com.alibaba.cloud.ai.studio.admin.dto.EvaluatorDebugResult;
import com.alibaba.cloud.ai.studio.admin.dto.EvaluatorTemplate;
import com.alibaba.cloud.ai.studio.admin.dto.EvaluatorVersion;
import com.alibaba.cloud.ai.studio.admin.dto.Experiment;
import com.alibaba.cloud.ai.studio.admin.dto.request.*;
import com.alibaba.cloud.ai.studio.admin.dto.Evaluator;
import com.alibaba.cloud.ai.studio.admin.service.EvaluatorTemplateService;
import com.alibaba.cloud.ai.studio.admin.service.EvaluatorService;
import com.alibaba.cloud.ai.studio.admin.service.EvaluatorVersionService;
import com.alibaba.cloud.ai.studio.admin.service.ExperimentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/evaluator")
@RequiredArgsConstructor
public class EvaluatorController {

    private final EvaluatorService evaluatorService;

    private final EvaluatorVersionService evaluatorVersionService;

    private final EvaluatorTemplateService evaluatorPromptTemplateService;

    private final ExperimentService experimentService;

    /**
     * Create evaluator
     */
    @PostMapping("/evaluator")
    public Result<Evaluator> create(@Validated @RequestBody EvaluatorCreateRequest request) {
        log.info("Create evaluator request: {}", request);
        try {
            Evaluator evaluator = evaluatorService.create(request);
            return Result.success(evaluator);
        } catch (Exception e) {
            log.error("Failed to create evaluator", e);
            return Result.error("Failed to create evaluator:" + e.getMessage());
        }
    }

    /**
     * Create evaluator version
     */
    @PostMapping("/evaluatorVersion")
    public Result<EvaluatorVersion> createVersion(@RequestBody EvaluatorVersionCreateRequest request) {
        log.info("Create evaluator version request: {}", request);
        try {
            EvaluatorVersion evaluatorVersion = evaluatorVersionService.create(request);
            return Result.success(evaluatorVersion);
        } catch (Exception e) {
            log.error("Failed to create evaluator version", e);
            return Result.error("Failed to create evaluator version:" + e.getMessage());
        }
    }

    /**
     * Get list of evaluators
     */
    @GetMapping("/evaluators")
    public Result<PageResult<Evaluator>> list(EvaluatorListRequest evaluatorListRequest){
        log.info("Query evaluator list request: {}", evaluatorListRequest);
        try {
            PageResult<Evaluator> result = evaluatorService.list(evaluatorListRequest);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Querying evaluator list failed", e);
            return Result.error("Querying evaluator list failed:" + e.getMessage());
        }
    }

    /**
     * Get evaluator details
     */
    @GetMapping("/evaluator")
    public Result<Evaluator> get(Long id) {
        log.info("Query evaluator details request: {}", id);
        try {
            Evaluator evaluator = evaluatorService.getById(id);
            if (evaluator == null) {
                return Result.error(404, "Evaluator does not exist");
            }
            return Result.success(evaluator);
        } catch (Exception e) {
            log.error("Failed to query evaluator details", e);
            return Result.error("Failed to query evaluator details:" + e.getMessage());
        }
    }

    //Get a list of evaluator versions
    @GetMapping("/evaluatorVersions")
    public Result<PageResult<EvaluatorVersion>> listVersions(EvaluatorVersionListRequest request) {
        log.info("Query evaluator version list request: {}", request);
        try {
            PageResult<EvaluatorVersion> result = evaluatorVersionService.list(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Querying evaluator version list failed", e);
            return Result.error("Failed to query evaluator version list:" + e.getMessage());
        }
    }

    /**
     * Update evaluator
     */
    @PutMapping("/evaluator")
    public Result<Evaluator> update(@RequestBody EvaluatorUpdateRequest request) {
        log.info("Update evaluator request: {}", request);
        try {
            Evaluator updatedEvaluator = evaluatorService.update(request);
            return Result.success(updatedEvaluator);
        } catch (Exception e) {
            log.error("Update evaluator failed", e);
            return Result.error("Failed to update evaluator:" + e.getMessage());
        }
    }

    /**
     * Remove evaluator
     */
    @DeleteMapping("/evaluator")
    public Result<Void> delete(@RequestParam Long id) {
        log.info("Delete evaluator request: {}", id);
        try {
            evaluatorService.deleteById(id);
            return Result.success();
        } catch (Exception e) {
            log.error("Removing evaluator failed", e);
            return Result.error("Removing evaluator failed:" + e.getMessage());
        }
    }

    /**
     * Debug evaluator
     */
    @PostMapping("/debug")
    public Result<EvaluatorDebugResult> debug(@RequestBody EvaluatorTestRequest request) {
        log.info("Debug evaluator request: {}", request);
        try {
            EvaluatorDebugResult result = evaluatorService.debug(request);
            return Result.success(result);
        } catch (Exception e) {
            log.error("Debugging evaluator failed", e);
            return Result.error("Debugging evaluator failed:" + e.getMessage());
        }
    }

    /**
     * Get a list of assessment templates
     */
    @GetMapping("/templates")
    public Result<PageResult<EvaluatorTemplate>> getTemplates(EvaluatorTemplateListRequest request) {
        log.info("Get evaluation template list request");
        try {
            PageResult<EvaluatorTemplate> templates = evaluatorPromptTemplateService.list(request);
            return Result.success(templates);
        } catch (Exception e) {
            log.error("Failed to get list of evaluation templates", e);
            return Result.error("Failed to get list of evaluation templates:" + e.getMessage());
        }
    }


    /**
     * Get a list of assessment templates
     */
    @GetMapping("/template")
    public Result<EvaluatorTemplate> getTemplate(Long templateId) {
        log.info("Get evaluation template list request");
        try {
            EvaluatorTemplate templates = evaluatorPromptTemplateService.get(templateId);
            return Result.success(templates);
        } catch (Exception e) {
            log.error("Get assessment template details", e);
            return Result.error("Failed to get assessment template details:" + e.getMessage());
        }
    }

    /**
     * Get the experiment associated with the evaluator
     */
    @GetMapping("/experiments")
    public Result<PageResult<Experiment>> getExperiments(EvaluatorExperimentsListRequest request) {
        log.info("Get the experiment associated with the evaluator: {}", request);
        try {
            PageResult<Experiment> experiments = experimentService.getExperimentsByEvaluator(request);
            return Result.success(experiments);
        } catch (Exception e) {
            log.error("Failed to get the experiment associated with the evaluator", e);
            return Result.error("Failed to get the experiment associated with the evaluator:" + e.getMessage());
        }
    }

} 
