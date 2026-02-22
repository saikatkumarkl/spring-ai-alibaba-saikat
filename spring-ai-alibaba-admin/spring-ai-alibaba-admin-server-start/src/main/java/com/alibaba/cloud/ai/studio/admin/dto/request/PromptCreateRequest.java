package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PromptCreateRequest {

    /**
     * Prompt Key (unique identifier)
     */
    @NotBlank(message = "Prompt Key cannot be empty")
    @Size(min = 1, max = 255, message = "Prompt Key length must be between 1-255 characters")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+$", message = "Prompt Key can only contain letters, numbers, underscores and dashes")
    private String promptKey;

    /**
     * PromptDescription
     */
    @Size(max = 255, message = "Prompt description cannot exceed 255 characters")
    private String promptDescription;

    /**
     * tags, comma separated
     */
    @Size(max = 255, message = "The total length of the label cannot exceed 255 characters")
    @Pattern(regexp = "^[^,]+(,[^,]+)*$|^$", message = "Label format is incorrect, should be a comma separated non-empty string")
    private String tags;
}
