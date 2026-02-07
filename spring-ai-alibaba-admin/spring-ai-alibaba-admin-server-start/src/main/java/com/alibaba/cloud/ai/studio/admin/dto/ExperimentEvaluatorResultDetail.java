package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.ExperimentResultDO;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExperimentEvaluatorResultDetail {


    /**
     * Experiment ID
     */
    private Long experimentId;

    /**
     * Enter content
     */
    private String input;

    /**
     * actual output
     */
    private String actualOutput;

    /**
     * Reference output
     */
    private String referenceOutput;

    /**
     * Assessment score (0.0-1.0)
     */
    private BigDecimal score;

    /**
     * Assess the reasons
     */
    private String reason;

    /**
     * Assessment time
     */
    private LocalDateTime evaluationTime;

    /**
     * Evaluator version ID
     */
    private Long evaluatorVersionId;

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
     * @param experimentResultDO DO object
     * @return DTO object
     */
    public static ExperimentEvaluatorResultDetail fromDO(ExperimentResultDO experimentResultDO) {
        if (experimentResultDO == null) {
            return null;
        }
        return ExperimentEvaluatorResultDetail.builder()
                .experimentId(experimentResultDO.getId())
                .experimentId(experimentResultDO.getExperimentId())
                .input(experimentResultDO.getInput())
                .actualOutput(experimentResultDO.getActualOutput())
                .referenceOutput(experimentResultDO.getReferenceOutput())
                .score(experimentResultDO.getScore())
                .reason(experimentResultDO.getReason())
                .evaluationTime(experimentResultDO.getEvaluationTime())
                .evaluatorVersionId(experimentResultDO.getEvaluatorVersionId())
                .createTime(experimentResultDO.getCreateTime())
                .updateTime(experimentResultDO.getUpdateTime())
                .build();
    }
} 
