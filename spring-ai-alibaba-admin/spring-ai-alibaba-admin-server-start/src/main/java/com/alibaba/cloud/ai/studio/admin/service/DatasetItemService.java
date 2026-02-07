package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.DatasetItem;
import com.alibaba.cloud.ai.studio.admin.dto.request.DataItemCreateFromTraceRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetItemCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetItemListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetItemUpdateRequest;

import java.util.List;

public interface DatasetItemService {

    /**
     * Create data item
     */
    List<DatasetItem> create(DatasetItemCreateRequest request);

    /**
     * Create data items from Trace
     */
    List<DatasetItem> createFromTrace(DataItemCreateFromTraceRequest request);

    /**
     * Paging query data item list
     */
    PageResult<DatasetItem> list(DatasetItemListRequest request);

    /**
     * Get data item based on ID
     */
    DatasetItem getById(Long id);

    /**
     * Update data item
     */
    DatasetItem update(DatasetItemUpdateRequest request);

    /**
     * Delete data items based on ID
     */
    void deleteById(Long id);

    /**
     * Delete data items in batches
     */
    void batchDelete(List<Long> ids);

} 
