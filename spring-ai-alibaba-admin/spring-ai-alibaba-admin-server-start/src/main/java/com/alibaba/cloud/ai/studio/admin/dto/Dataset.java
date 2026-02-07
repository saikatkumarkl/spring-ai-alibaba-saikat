package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.DatasetDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;


@Builder
@Data
public class Dataset {

    /**
     * ID
     */
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
     * Number of data items
     */
    private Integer dataCount;

    /**
     * latest version
     */
    private String latestVersion;


    /**
     * latest version
     */
    private Long latestVersionId;

    /**
     * creation time
     */
    private LocalDateTime createTime;

    /**
     * Update time
     */
    private LocalDateTime updateTime;






    public static Dataset fromDO(DatasetDO DatasetDO){
        return Dataset.builder()
                .id(DatasetDO.getId())
                .name(DatasetDO.getName())
                .description(DatasetDO.getDescription())
                .columnsConfig(DatasetDO.getColumnsConfig())
                .createTime(DatasetDO.getCreateTime())
                .updateTime(DatasetDO.getUpdateTime())
                .build();
    }

}
