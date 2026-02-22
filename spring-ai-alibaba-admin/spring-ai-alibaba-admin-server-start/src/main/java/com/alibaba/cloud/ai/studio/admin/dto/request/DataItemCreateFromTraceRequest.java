package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.DatasetColumn;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DataItemCreateFromTraceRequest {
    /**
     * Evaluation set ID
     */
    @NotNull(message = "Evaluation set ID cannot be empty")
    private Long datasetId;


    @NotNull(message = "Evaluation set version ID cannot be empty")
    private Long datasetVersionId;

    private List<String> dataContent;

    /**
     * Column structure configuration (JSON format)
     */

    private List<DatasetColumn> columnsConfig;

}
