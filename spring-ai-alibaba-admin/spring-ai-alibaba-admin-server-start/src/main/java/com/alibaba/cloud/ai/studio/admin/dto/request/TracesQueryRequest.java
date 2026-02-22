package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TracesQueryRequest {

    private String serviceName;

    private String traceId;

    private String spanName;

    @NotBlank(message = "Start time cannot be empty")
    private String startTime;

    @NotBlank(message = "End time cannot be empty")
    private String endTime;

    @Min(value = 1, message = "The minimum page number is 1")
    private Integer pageNumber = 1;

    @Min(value = 1, message = "The minimum size per page is 1")
    @Max(value = 200, message = "Maximum size per page is 200")
    private Integer pageSize = 50;

    private String attributes;
}
