package com.alibaba.cloud.ai.studio.admin.entity;

import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Builder
@Data
public class EvaluatorVersionDO {
    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Evaluator ID
     */
    private Long evaluatorId;

    /**
     * Evaluator description
     */
    private String description;

    /**
     * version number
     */
    private String version;

    /**
     * Model ID
     */
    private String modelConfig;

    /**
     * Prompt configuration (JSON format)
     */
    private String prompt;

    /**
     * Variable parameters in evaluators
     */
    private String variables;

    /**
     * version status
     */
    private String status;

    /**
     * Experimental collection (one-to-many relationship)
     */
    private String experiments;


    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;
}
