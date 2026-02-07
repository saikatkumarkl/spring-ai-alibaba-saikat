package com.alibaba.cloud.ai.studio.admin.dto.request;

import lombok.Data;

@Data
public class ModelConfigQueryRequest {

    /**
     * Page number, starting from 1
     */
    private Integer page = 1;

    /**
     * page size
     */
    private Integer size = 10;

    /**
     * Model name (fuzzy query)
     */
    private String name;

    /**
     * provider
     */
    private String provider;

    /**
     * Status: 1-enabled, 0-disabled
     */
    private Integer status;
}
