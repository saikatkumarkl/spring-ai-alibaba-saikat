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
    @NotBlank(message = "Prompt Key cannot be empty")
    @Size(min = 1, max = 255, message = "Prompt Key length must be between 1-255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Prompt Key can only contain letters, numbers, underscores and dashes")
    private String promptKey;

    /**
     * version number
     */
    @NotBlank(message = "Version number cannot be empty")
    @Size(min = 1, max = 32, message = "The version number length must be between 1-32 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Version numbers can only contain letters, numbers, dots, underscores, and dashes")
    private String version;

    /**
     * Version description
     */
    @Size(max = 255, message = "Version description cannot exceed 255 characters")
    private String versionDescription;

    /**
     * Prompt content
     */
    @NotBlank(message = "Prompt content cannot be empty")
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
    @Pattern(regexp = "^(pre|release)$", message = "Version status must be pre or release")
    private String status = "pre";
}
