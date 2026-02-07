package com.alibaba.cloud.ai.studio.admin.entity;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class EvaluatorDO {

    /**
     * Primary key ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * evaluator name
     */
    private String name;

    /**
     * Evaluator description
     */
    private String description;



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
