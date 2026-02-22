package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.Dataset;
import com.alibaba.cloud.ai.studio.admin.dto.DatasetColumn;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.DatasetUpdateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.DatasetDO;
import com.alibaba.cloud.ai.studio.admin.entity.DatasetVersionDO;
import com.alibaba.cloud.ai.studio.admin.mapper.DatasetMapper;
import com.alibaba.cloud.ai.studio.admin.mapper.DatasetVersionMapper;
import com.alibaba.cloud.ai.studio.admin.service.DatasetService;
import com.alibaba.fastjson.JSONObject;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

@Slf4j
@Service
public class DatasetServiceImpl implements DatasetService {

    @Resource
    private DatasetMapper datasetMapper;

    @Resource
    private DatasetVersionMapper datasetVersionMapper;




    private static final String INPUT_COLUMN_TYPE = "input";
    private static final String REFERENCE_OUTPUT_COLUMN_TYPE = "reference_output";


    @Override
    @Transactional
    public Dataset create(DatasetCreateRequest request) {
        log.info("Create a review set: {}", request);

        if (!StringUtils.hasText(request.getName())) {
            throw new IllegalArgumentException("Evaluation set name cannot be empty");
        }


        if (request == null || request.getColumnsConfig() == null ||
                !hasRequiredColumns(request.getColumnsConfig())) {
            throw new IllegalArgumentException("The evaluation set column configuration is incorrect and must contain two columns: input and reference_output.");
        }

        DatasetDO datasetDO = DatasetDO.builder()
                .name(request.getName())
                .description(request.getDescription())
                .columnsConfig(JSONObject.toJSONString(request.getColumnsConfig()))
                .build();

        datasetMapper.insert(datasetDO);
        log.info("Evaluation set created successfully: {}", datasetDO);
        return Dataset.fromDO(datasetDO);
    }

    @Override
    public PageResult<Dataset> list(DatasetListRequest request) {
        log.info("Query the evaluation set list: {}", request);

        //Calculate paging parameters
        int pageNumber = request.getPageNumber() != null ? request.getPageNumber() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        long offset = (pageNumber - 1L) * pageSize;
        
        //Get search criteria
        String name = request.getDatasetName();
        
        //Query data
        List<DatasetDO> datasetDOList = datasetMapper.selectList(name, offset, pageSize);

        List<Dataset> datasetList = datasetDOList.stream()
                .map(Dataset::fromDO)
                .peek(dataset -> {
                    DatasetVersionDO datasetVersionDO = datasetVersionMapper.selectLatestVersion(dataset.getId());
                    if (Objects.nonNull(datasetVersionDO)) {
                        dataset.setDataCount(datasetVersionDO.getDataCount());
                        dataset.setLatestVersion(datasetVersionDO.getVersion());
                    }
                })
                .toList();
        int total = datasetMapper.selectCount(name);
        

        PageResult<Dataset> result = new PageResult<>(
                (long) total, 
                (long) pageNumber, 
                (long) pageSize,
                datasetList
        );
        
        return result;
    }

    @Override
    public Dataset getById(Long id) {
        log.info("Query evaluation set details: {}", id);
        DatasetDO datasetDO = datasetMapper.selectById(id);
            
        if (datasetDO == null) {
            log.warn("Review set with ID {} ​​not found", id);
            return null;
        }
        Dataset dataset = Dataset.fromDO(datasetDO);

        DatasetVersionDO datasetVersionDO = datasetVersionMapper.selectLatestVersion(dataset.getId());

        if(Objects.nonNull(datasetVersionDO)){
            dataset.setDataCount(datasetVersionDO.getDataCount());
            dataset.setLatestVersion(datasetVersionDO.getVersion());
            dataset.setLatestVersionId(datasetVersionDO.getId());
        }
        
        return dataset;
    }

    @Override
    public Dataset update(DatasetUpdateRequest request) {
        log.info("Update review set: {}", request);

         DatasetDO existingDataset = datasetMapper.selectById(request.getDatasetId());
         if (existingDataset == null) {
             throw new IllegalArgumentException("The evaluation set does not exist:" + request.getDatasetId());
         }


        datasetMapper.update(request.getDatasetId(),request.getName(),request.getDescription());
        existingDataset.setName(request.getName());
        existingDataset.setDescription(request.getDescription());

        return Dataset.fromDO(existingDataset);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        log.info("Delete review set: {}", id);
        datasetMapper.deleteById(id);
        log.info("Evaluation set deleted successfully: {}", id);
    }


    private boolean hasRequiredColumns(List<DatasetColumn> columns) {
        if (columns == null) {
            return false;
        }

        return columns.stream()
                .filter(Objects::nonNull)
                .anyMatch(column -> INPUT_COLUMN_TYPE.equals(column.getName())) &&
                columns.stream()
                        .filter(Objects::nonNull)
                        .anyMatch(column -> REFERENCE_OUTPUT_COLUMN_TYPE.equals(column.getName()));
    }
}
