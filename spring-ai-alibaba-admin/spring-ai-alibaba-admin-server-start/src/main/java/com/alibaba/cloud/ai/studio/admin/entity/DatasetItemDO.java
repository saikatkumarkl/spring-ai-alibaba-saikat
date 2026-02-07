package com.alibaba.cloud.ai.studio.admin.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DatasetItemDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Data set ID
     */
    private Long datasetId;

    /**
     * Column structure configuration (JSON format)
     */

    private String columnsConfig;

    /**
     * Data content (JSON format)
     */

    private String dataContent;


    /**
     * creation time
     */

    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;

}
