package com.alibaba.cloud.ai.studio.admin.dto.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class DatasetItemListRequest {

    /**
     * page number
     */
    private Integer pageNumber = 1;

    /**
     * page size
     */
    private Integer pageSize = 10;

    @NotNull
    private Long datasetVersionId;
}
