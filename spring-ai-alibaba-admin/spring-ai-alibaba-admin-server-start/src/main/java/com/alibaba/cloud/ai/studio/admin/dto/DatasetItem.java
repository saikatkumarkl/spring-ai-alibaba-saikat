package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.DatasetItemDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class DatasetItem {

    /**
     * Primary key ID
     */
    private Long id;

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

    /**
     * Convert from DO object to DTO object
     *
     * @param datasetItemDO DO object
     * @return DTO object
     */
    public static DatasetItem fromDO(DatasetItemDO datasetItemDO) {
        if (datasetItemDO == null) {
            return null;
        }
        return DatasetItem.builder()
                .id(datasetItemDO.getId())
                .columnsConfig(datasetItemDO.getColumnsConfig())
                .dataContent(datasetItemDO.getDataContent())
                .createTime(datasetItemDO.getCreateTime())
                .updateTime(datasetItemDO.getUpdateTime())
                .build();
    }
} 
