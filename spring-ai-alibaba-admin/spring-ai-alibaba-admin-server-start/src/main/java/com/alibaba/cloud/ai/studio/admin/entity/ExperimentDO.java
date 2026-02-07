package com.alibaba.cloud.ai.studio.admin.entity;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class ExperimentDO {

    /**
     * Primary key ID
     */
    private Long id;

    /**
     * Experiment name
     */
    private String name;

    /**
     * Experiment description
     */
    private String description;

    /**
     * Data set ID
     */
    private Long datasetId;

    /**
     * Dataset version
     */
    private Long datasetVersionId;


    /**
     * Dataset version number
     */
    private String datasetVersion;

    /**
     * Evaluation object configuration (JSON format)
     */
    private String evaluationObjectConfig;


    /**
     * Evaluator configuration
     */
    private String evaluatorConfig;

    /**
     * Status: DRAFT - draft, RUNNING - running, COMPLETED - completed, FAILED - failed, STOPPED - stopped
     */
    private String status;

    /**
     * Progress percentage: 0-100
     */
    private Integer progress;

    /**
     * completion time
     */
    private LocalDateTime completeTime;


    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;
}
