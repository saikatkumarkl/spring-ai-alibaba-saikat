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
    @Size(max = 100, message = "模型名称不能超过100个字符")
    private String name;

    /**
     * provider
     */
    @Size(max = 50, message = "提供商不能超过50个字符")
    private String provider;

    /**
     * model identifier
     */
    @Size(max = 100, message = "模型标识符不能超过100个字符")
    private String modelName;

    /**
     * Model service address
     */
    @Size(max = 500, message = "模型服务地址不能超过500个字符")
    private String baseUrl;

    /**
     * API key
     */
    @Size(max = 500, message = "API密钥不能超过500个字符")
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
