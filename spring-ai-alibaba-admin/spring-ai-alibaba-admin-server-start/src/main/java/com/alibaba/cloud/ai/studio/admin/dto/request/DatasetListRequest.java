package com.alibaba.cloud.ai.studio.admin.dto.request;

import lombok.Data;


@Data
public class DatasetListRequest {

    /**
     * page number
     */
    private Integer pageNumber = 1;

    /**
     * page size
     */
    private Integer pageSize = 10;

    /**
     * Query according to datasetName
     */
    private String datasetName;


} 
