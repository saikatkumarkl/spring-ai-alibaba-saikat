package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class ExperimentCreateRequest {

    /**
     * Experiment name
     */
    @NotBlank
    private String name;

    /**
     * Experiment description
     */
    private String description;

    /**
     * Data set ID
     */
    @NotNull
    private Long datasetId;

    /**
     * Dataset version
     */
    @NotBlank
    private Long datasetVersionId;

    @NotBlank
    private String datasetVersion;

    /**
     * Evaluation object configuration (JSON format)
     */
    private String evaluationObjectConfig;



    /**
     * Evaluator configuration
     */
    private String evaluatorConfig;


}
