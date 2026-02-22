package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.EvaluatorVersion;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorVersionCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorVersionListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.EvaluatorVersionUpdateRequest;
import com.alibaba.cloud.ai.studio.admin.entity.EvaluatorVersionDO;
import com.alibaba.cloud.ai.studio.admin.mapper.EvaluatorVersionMapper;
import com.alibaba.cloud.ai.studio.admin.service.EvaluatorVersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

@Slf4j
@Service
@RequiredArgsConstructor
public class EvaluatorVersionServiceImpl implements EvaluatorVersionService {

    private final EvaluatorVersionMapper evaluatorVersionMapper;

    @Override
    public EvaluatorVersion create(EvaluatorVersionCreateRequest request) {
        log.info("Create evaluator version: {}", request);

        //Build DO object
        EvaluatorVersionDO evaluatorVersionDO = EvaluatorVersionDO.builder()
                .evaluatorId(Long.valueOf(request.getEvaluatorId()))
                .description(request.getDescription())
                .version(request.getVersion())
                .modelConfig(request.getModelConfig())
                .prompt(request.getPrompt())
                .variables(request.getVariables())
                .createTime(LocalDateTime.now())
                .updateTime(LocalDateTime.now())
                .build();

        //Insert into database
        int result = evaluatorVersionMapper.insert(evaluatorVersionDO);
        if (result > 0) {
            log.info("Evaluator version created successfully: {}", evaluatorVersionDO.getId());
            return EvaluatorVersion.fromDO(evaluatorVersionDO);
        } else {
            throw new RuntimeException("Failed to create evaluator version");
        }
    }

    @Override
    public PageResult<EvaluatorVersion> list(EvaluatorVersionListRequest request) {
        log.info("Query evaluator version list: {}", request);

        //Calculate paging parameters
        int pageNumber = request.getPageNumber() != null ? request.getPageNumber() : 1;
        int pageSize = request.getPageSize() != null ? request.getPageSize() : 10;
        long offset = (pageNumber - 1L) * pageSize;

        //Query data
        List<EvaluatorVersionDO> evaluatorVersionDOList = evaluatorVersionMapper.selectListByEvaluatorId(
                request.getEvaluatorId(), request.getName(), offset, pageSize);
        int total = evaluatorVersionMapper.countByEvaluatorId(request.getEvaluatorId(), null);

        //Encapsulate paginated results
        return new PageResult<>(
                (long) pageNumber,
                (long) total,
                (long) pageSize,
                evaluatorVersionDOList.stream()
                        .map(EvaluatorVersion::fromDO)
                        .toList());
    }

    @Override
    public EvaluatorVersion getById(Long id) {
        log.info("Query evaluator version details: {}", id);

        return EvaluatorVersion.fromDO(evaluatorVersionMapper.selectById(id));
    }

    @Override
    public EvaluatorVersion update(EvaluatorVersionUpdateRequest request) {
        log.info("Update evaluator version: {}", request.getEvaluatorVersionId());


        //Check whether the version already exists and verify the status
        EvaluatorVersionDO exists = evaluatorVersionMapper.selectById(request.getEvaluatorVersionId());

        if (Objects.isNull(exists)){
            throw new RuntimeException("Evaluator version does not exist");
        }

        evaluatorVersionMapper.update(request.getEvaluatorVersionId(), request.getDescription(), request.getStatus());

        return getById(request.getEvaluatorVersionId());
    }
}
