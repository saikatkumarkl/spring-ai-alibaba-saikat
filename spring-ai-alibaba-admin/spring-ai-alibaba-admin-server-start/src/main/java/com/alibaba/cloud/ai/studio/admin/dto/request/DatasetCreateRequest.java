package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.DatasetColumn;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class DatasetCreateRequest {

    /**
     * Data set name
     */
    @NotNull
    private String name;

    /**
     * Dataset description
     */
    private String description;

    /**
     * Column structure configuration
     */
    @NotNull
    private List<DatasetColumn> columnsConfig;



} 
