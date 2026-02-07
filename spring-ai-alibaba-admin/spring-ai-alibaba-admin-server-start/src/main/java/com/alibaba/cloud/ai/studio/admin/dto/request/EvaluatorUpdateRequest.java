package com.alibaba.cloud.ai.studio.admin.dto.request;


import lombok.Data;

@Data
public class EvaluatorUpdateRequest {
    /**
     * Primary key ID
     */
    private Long id;

    /**
     * evaluator name
     */
    private String name;


    /**
     * Evaluator description
     */
    private String description;


}
