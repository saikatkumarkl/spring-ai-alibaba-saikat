package com.alibaba.cloud.ai.studio.admin.dto.request;

import lombok.Data;

@Data
public class EvaluatorVersionCreateRequest {

    /**
     * EvaluatorId
     */
    private String evaluatorId;

    /**
     * Evaluator version description
     */
    private String description;

    /**
     * Model ID
     */
    private String modelConfig;

    /**
     * Prompt
     */
    private String prompt;


    /**
     * Evaluator version number
     */

    private String version;



    /**
     * version status
     */
    private String status;


    private String variables;

} 
