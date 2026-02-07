package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.ExperimentDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class Experiment {

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

    /**
     * Convert from DO object to DTO object
     *
     * @param experimentDO DO object
     * @return DTO object
     */
    public static Experiment fromDO(ExperimentDO experimentDO) {
        if (experimentDO == null) {
            return null;
        }
        return Experiment.builder()
                .id(experimentDO.getId())
                .name(experimentDO.getName())
                .description(experimentDO.getDescription())
                .datasetId(experimentDO.getDatasetId())
                .datasetVersion(experimentDO.getDatasetVersion())
                .evaluationObjectConfig(experimentDO.getEvaluationObjectConfig())
                .evaluatorConfig(experimentDO.getEvaluatorConfig())
                .status(experimentDO.getStatus())
                .progress(experimentDO.getProgress())
                .completeTime(experimentDO.getCompleteTime())
                .createTime(experimentDO.getCreateTime())
                .updateTime(experimentDO.getUpdateTime())
                .build();
    }

} 
