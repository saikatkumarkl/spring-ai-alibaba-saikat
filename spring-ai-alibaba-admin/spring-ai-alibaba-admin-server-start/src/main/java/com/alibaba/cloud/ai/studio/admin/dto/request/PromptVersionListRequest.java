package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PromptVersionListRequest {

    /**
     * Prompt Key
     */
    @NotBlank(message = "Prompt Key cannot be empty")
    private String promptKey;

    /**
     * Version status filtering: pre-pre-release version, release-official version, all-all status
     */
    @Pattern(regexp = "^(pre|release|all)$", message = "Version status must be pre, release or all")
    private String status = "all";

    /**
     * page number
     */
    @Min(value = 1, message = "Page number must be greater than 0")
    private Integer pageNo = 1;

    /**
     * Quantity per page
     */
    @Min(value = 1, message = "Each page size must be greater than 0")
    @Max(value = 100, message = "Each page size cannot exceed 100")
    private Integer pageSize = 10;
}
