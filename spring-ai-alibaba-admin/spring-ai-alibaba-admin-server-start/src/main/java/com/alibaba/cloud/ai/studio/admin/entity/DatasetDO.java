package com.alibaba.cloud.ai.studio.admin.entity;


import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Builder
@Data
public class DatasetDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Evaluation set name
     */
    private String name;

    /**
     * Evaluation set description
     */
    private String description;

    /**
     * Column structure configuration (JSON format)
     */
    private String columnsConfig;


    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;

    /**
     * Tombstone identification: 0-not deleted, 1-deleted
     */
    private Integer deleted;

}
