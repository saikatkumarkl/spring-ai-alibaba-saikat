package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.ModelParameterDef;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class ModelConfigCreateRequest {

    /**
     * Model name
     */
    @NotBlank(message = "Model name cannot be empty")
    @Size(max = 100, message = "Model name cannot exceed 100 characters")
    private String name;

    /**
     * provider
     */
    @NotBlank(message = "Provider cannot be empty")
    @Size(max = 50, message = "Provider cannot exceed 50 characters")
    private String provider;

    /**
     * model identifier
     */
    @NotBlank(message = "Model identifier cannot be empty")
    @Size(max = 100, message = "Model identifier cannot exceed 100 characters")
    private String modelName;

    /**
     * Model service address
     */
    @NotBlank(message = "Model service address cannot be empty")
    @Size(max = 500, message = "The model service address cannot exceed 500 characters")
    private String baseUrl;

    /**
     * API key
     */
    @NotBlank(message = "API key cannot be empty")
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
}
