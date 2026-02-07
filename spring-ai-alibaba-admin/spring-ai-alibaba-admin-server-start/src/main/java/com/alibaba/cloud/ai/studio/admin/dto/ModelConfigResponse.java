package com.alibaba.cloud.ai.studio.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ModelConfigResponse {

    /**
     * Primary key ID
     */
    private Long id;

    /**
     * Model name
     */
    private String name;

    /**
     * provider
     */
    private String provider;

    /**
     * model identifier
     */
    private String modelName;

    /**
     * Model service address
     */
    private String baseUrl;

    /**
     * Default parameter configuration
     */
    private Map<String, Object> defaultParameters;

    /**
     * Supported parameter definitions
     */
    private List<ModelParameterDef> supportedParameters;

    /**
     * Status: 1-enabled, 0-disabled
     */
    private Integer status;

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;
}
