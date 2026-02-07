package com.alibaba.cloud.ai.studio.admin.dto.request;


import lombok.Data;

@Data
public class EvaluatorVersionUpdateRequest {
    /**
     * Primary key ID
     */
    private Long evaluatorVersionId;


    /**
     * Evaluator version description
     */
    private String description;

    private String status;


}
