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
    @NotBlank(message = "模型名称不能为空")
    @Size(max = 100, message = "模型名称不能超过100个字符")
    private String name;

    /**
     * provider
     */
    @NotBlank(message = "提供商不能为空")
    @Size(max = 50, message = "提供商不能超过50个字符")
    private String provider;

    /**
     * model identifier
     */
    @NotBlank(message = "模型标识符不能为空")
    @Size(max = 100, message = "模型标识符不能超过100个字符")
    private String modelName;

    /**
     * Model service address
     */
    @NotBlank(message = "模型服务地址不能为空")
    @Size(max = 500, message = "模型服务地址不能超过500个字符")
    private String baseUrl;

    /**
     * API key
     */
    @NotBlank(message = "API密钥不能为空")
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
}
