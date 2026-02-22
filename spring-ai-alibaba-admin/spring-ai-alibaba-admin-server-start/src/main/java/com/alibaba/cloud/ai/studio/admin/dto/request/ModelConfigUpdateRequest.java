package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.ModelParameterDef;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ModelConfigUpdateRequest {
    
    /**
     * Model configuration ID
     */
    private Long id;

    /**
     * Model name
     */
    @Size(max = 100, message = "Model name cannot exceed 100 characters")
    private String name;

    /**
     * provider
     */
    @Size(max = 50, message = "Provider cannot exceed 50 characters")
    private String provider;

    /**
     * model identifier
     */
    @Size(max = 100, message = "Model identifier cannot exceed 100 characters")
    private String modelName;

    /**
     * Model service address
     */
    @Size(max = 500, message = "The model service address cannot exceed 500 characters")
    private String baseUrl;

    /**
     * API key
     */
    @Size(max = 500, message = "API keys cannot exceed 500 characters")
    private String apiKey;

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
}
