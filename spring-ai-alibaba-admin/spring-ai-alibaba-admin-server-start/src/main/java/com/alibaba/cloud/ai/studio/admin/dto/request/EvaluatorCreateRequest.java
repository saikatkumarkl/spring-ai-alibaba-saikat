package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class EvaluatorCreateRequest {

    /**
     * evaluator name
     */
    @NotNull
    private String name;

    /**
     * Evaluator description
     */
    private String description;



} 
