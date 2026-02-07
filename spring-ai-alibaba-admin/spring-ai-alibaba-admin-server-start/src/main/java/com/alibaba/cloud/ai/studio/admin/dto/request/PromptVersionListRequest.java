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
    @NotBlank(message = "Prompt Key不能为空")
    private String promptKey;

    /**
     * Version status filtering: pre-pre-release version, release-official version, all-all status
     */
    @Pattern(regexp = "^(pre|release|all)$", message = "版本状态必须是pre、release或all")
    private String status = "all";

    /**
     * page number
     */
    @Min(value = 1, message = "页码必须大于0")
    private Integer pageNo = 1;

    /**
     * Quantity per page
     */
    @Min(value = 1, message = "每页大小必须大于0")
    @Max(value = 100, message = "每页大小不能超过100")
    private Integer pageSize = 10;
}
