package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class PromptListRequest {

    /**
     * Query mode: accurate, blur
     */
    @Pattern(regexp = "^(accurate|blur)$", message = "Search mode must be accurate or blur")
    private String search = "blur";

    /**
     * Tag name
     */
    private String tag;

    /**
     * Prompt Key
     */
    private String promptKey;

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
