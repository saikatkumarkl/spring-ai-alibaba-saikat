package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.DatasetColumn;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DatasetVersionCreateRequest {

    /**
     * DatasetId
     */
    @NotNull
    private Long datasetId;

    /**
     * Dataset version description
     */
    private String description;

    /**
     * Column structure configuration
     */
    @NotNull
    private List<DatasetColumn> columnsConfig;

    /**
     * Data set ID
     */
    @NotNull
    private List<Long> datasetItems;


    String status;
} 
