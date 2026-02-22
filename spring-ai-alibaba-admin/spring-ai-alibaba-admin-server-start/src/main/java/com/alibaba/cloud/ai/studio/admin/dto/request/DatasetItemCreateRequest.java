package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.DatasetColumn;
import lombok.Data;
import jakarta.validation.constraints.NotNull;

import java.util.List;

@Data
public class DatasetItemCreateRequest {

    /**
     * Evaluation set ID
     */
    @NotNull(message = "Evaluation set ID cannot be empty")
    private Long datasetId;

    private List<String> dataContent;

    /**
     * Column structure configuration (JSON format)
     */

    private List<DatasetColumn> columnsConfig;

} 
