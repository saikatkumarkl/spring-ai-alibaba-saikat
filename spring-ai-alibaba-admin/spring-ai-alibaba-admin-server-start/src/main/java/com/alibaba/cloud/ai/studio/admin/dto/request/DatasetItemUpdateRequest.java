package com.alibaba.cloud.ai.studio.admin.dto.request;

import lombok.Data;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Data
public class DatasetItemUpdateRequest {

    /**
     * Data item ID
     */
    @NotNull(message = "Data item ID cannot be empty")
    private Long id;

    /**
     * Data content (JSON format)
     */
    @NotBlank(message = "Data content cannot be empty")
    private String dataContent;

} 
