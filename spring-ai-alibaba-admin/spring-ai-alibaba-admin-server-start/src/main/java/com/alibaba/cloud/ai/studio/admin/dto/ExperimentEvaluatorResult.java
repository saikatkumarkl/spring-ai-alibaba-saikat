package com.alibaba.cloud.ai.studio.admin.dto;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

@Data
@Builder
public class ExperimentEvaluatorResult {

    /**
     * Experiment ID
     */
    private Long experimentId;


    /**
     * Assessment average score (0.0-1.0)
     */
    private BigDecimal averageScore;


    /**
     * Evaluator version ID
     */
    private Long evaluatorVersionId;


    /**
     * schedule
     */
    private Integer progress;


    private Integer completeItemsCount;

    private Integer totalItemsCount;



} 
