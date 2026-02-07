package com.alibaba.cloud.ai.studio.admin.dto.request;

import lombok.Data;

@Data
public class DatasetExperimentsListRequest {
    /**
     * page number
     */
    private Integer pageNumber = 1;

    /**
     * page size
     */
    private Integer pageSize = 10;

    private Long datasetId;
}
