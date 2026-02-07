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
    @NotNull(message = "测评集ID不能为空")
    private Long datasetId;


    @NotNull(message = "测评集版本ID不能为空")
    private Long datasetVersionId;

    private List<String> dataContent;

    /**
     * Column structure configuration (JSON format)
     */

    private List<DatasetColumn> columnsConfig;

}
