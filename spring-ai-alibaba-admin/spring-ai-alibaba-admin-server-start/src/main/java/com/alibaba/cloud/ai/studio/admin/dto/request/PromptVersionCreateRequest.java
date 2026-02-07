package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptVersionCreateRequest {

    /**
     * Prompt Key
     */
    @NotBlank(message = "Prompt Key不能为空")
    @Size(min = 1, max = 255, message = "Prompt Key长度必须在1-255个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Prompt Key只能包含字母、数字、下划线和短横线")
    private String promptKey;

    /**
     * version number
     */
    @NotBlank(message = "版本号不能为空")
    @Size(min = 1, max = 32, message = "版本号长度必须在1-32个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "版本号只能包含字母、数字、点、下划线和短横线")
    private String version;

    /**
     * Version description
     */
    @Size(max = 255, message = "版本描述不能超过255个字符")
    private String versionDescription;

    /**
     * Prompt content
     */
    @NotBlank(message = "Prompt内容不能为空")
    private String template;

    /**
     * Variable value in Prompt, JSON format
     */
    private String variables;

    /**
     * Model related parameters used, JSON format
     */
    private String modelConfig;

    /**
     * Version status: pre-pre-release version, release-official version
     */
    @Pattern(regexp = "^(pre|release)$", message = "版本状态必须是pre或release")
    private String status = "pre";
}
