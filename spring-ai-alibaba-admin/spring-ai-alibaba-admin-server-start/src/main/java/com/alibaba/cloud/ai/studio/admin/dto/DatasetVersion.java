package com.alibaba.cloud.ai.studio.admin.dto;

import com.alibaba.cloud.ai.studio.admin.entity.DatasetVersionDO;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class DatasetVersion {

    /**
     * Primary key ID
     */
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
     * creation time
     */
    private LocalDateTime createTime;



    /**
     * List of column structures (non-database fields)
     */
    private List<DatasetColumn> columnsConfig;


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
     * Convert from DO object to DTO object
     *
     * @param datasetVersionDO DO object
     * @return DTO object
     */
    public static DatasetVersion fromDO(DatasetVersionDO datasetVersionDO) {
        if (datasetVersionDO == null) {
            return null;
        }
        return DatasetVersion.builder()
                .id(datasetVersionDO.getId())
                .datasetId(datasetVersionDO.getDatasetId())
                .version(datasetVersionDO.getVersion())
                .description(datasetVersionDO.getDescription())
                .dataCount(datasetVersionDO.getDataCount())
                .createTime(datasetVersionDO.getCreateTime())
                .status(datasetVersionDO.getStatus())
                .experiments(datasetVersionDO.getExperiments())
                .datasetItems(datasetVersionDO.getDatasetItems())
                .build();
    }
} 
