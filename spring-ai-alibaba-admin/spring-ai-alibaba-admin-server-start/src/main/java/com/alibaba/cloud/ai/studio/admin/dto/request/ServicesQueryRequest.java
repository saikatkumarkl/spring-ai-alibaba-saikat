package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ServicesQueryRequest {

    @NotBlank(message = "Start time cannot be empty")
    private String startTime;

    @NotBlank(message = "End time cannot be empty")
    private String endTime;
}
