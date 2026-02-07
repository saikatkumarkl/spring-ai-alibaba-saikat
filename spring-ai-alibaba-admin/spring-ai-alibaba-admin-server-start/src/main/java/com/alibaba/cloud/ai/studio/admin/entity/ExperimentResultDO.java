package com.alibaba.cloud.ai.studio.admin.entity;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class ExperimentResultDO {

    /**
     * Primary key ID
     */
    private Long id;

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
} 
