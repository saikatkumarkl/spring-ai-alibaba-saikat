package com.alibaba.cloud.ai.studio.admin.entity;

import com.alibaba.cloud.ai.studio.admin.dto.DatasetColumn;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DatasetVersionDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Evaluation set ID
     */

    private Long datasetId;

    /**
     * version number
     */
    private String version;

    /**
     * Version description
     */
    private String description;

    /**
     * The total amount of data in this version
     */
    private Integer dataCount;


    /**
     * version status versionStatus
     */
    private String status;

    /**
     * Experimental collection (one-to-many relationship)
     */
    private String experiments;

    /**
     * Collection of data items (one-to-many relationship)
     */
    private String datasetItems;


    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;





}
