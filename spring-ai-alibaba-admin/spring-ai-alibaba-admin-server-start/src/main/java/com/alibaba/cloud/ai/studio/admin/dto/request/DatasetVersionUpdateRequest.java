package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.DatasetColumn;
import lombok.Data;

import java.util.List;

@Data
public class DatasetVersionUpdateRequest {

    /**
     * Dataset version ID description
     */
    private Long datasetVersionId;


    /**
     * Dataset version description
     */
    private String description;


    /**
     * Dataset version status
     */

    private String status;

} 
