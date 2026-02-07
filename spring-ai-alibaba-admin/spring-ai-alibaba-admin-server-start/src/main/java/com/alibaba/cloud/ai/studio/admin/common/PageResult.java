package com.alibaba.cloud.ai.studio.admin.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {

    /**
     * Total number of records
     */
    private Long totalCount;

    /**
     * Total pages
     */
    private Long totalPage;

    /**
     * Current page
     */
    private Long pageNumber;

    /**
     * page size
     */
    private Long pageSize;

    /**
     * Data list
     */
    private List<T> pageItems;

    /**
     * Constructor
     */
    public PageResult() {
    }

    /**
     * Constructor
     */
    public PageResult(Long totalCount, Long pageNumber, Long pageSize, List<T> pageItems) {
        this.totalCount = totalCount;
        this.pageNumber = pageNumber;
        this.pageSize = pageSize;
        this.pageItems = pageItems;
        this.totalPage = (totalCount + pageSize - 1) / pageSize;
    }
} 
