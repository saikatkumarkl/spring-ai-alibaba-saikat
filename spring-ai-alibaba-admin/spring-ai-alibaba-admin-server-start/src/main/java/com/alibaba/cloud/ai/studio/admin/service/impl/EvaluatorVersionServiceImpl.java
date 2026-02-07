package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.SaaStudioAdmin;
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
        log.info("创建评估器版本: {}", request);

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
            log.info("评估器版本创建成功: {}", evaluatorVersionDO.getId());
            return EvaluatorVersion.fromDO(evaluatorVersionDO);
        } else {
            throw new RuntimeException("创建评估器版本失败");
        }
    }

    @Override
    public PageResult<EvaluatorVersion> list(EvaluatorVersionListRequest request) {
        log.info("查询评估器版本列表: {}", request);

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
        log.info("查询评估器版本详情: {}", id);

        return EvaluatorVersion.fromDO(evaluatorVersionMapper.selectById(id));
    }

    @Override
    public EvaluatorVersion update(EvaluatorVersionUpdateRequest request) {
        log.info("更新评估器版本: {}", request.getEvaluatorVersionId());


        //Check whether the version already exists and verify the status
        EvaluatorVersionDO exists = evaluatorVersionMapper.selectById(request.getEvaluatorVersionId());

        if (Objects.isNull(exists)){
            throw new RuntimeException("评估器版本不存在");
        }

        evaluatorVersionMapper.update(request.getEvaluatorVersionId(), request.getDescription(), request.getStatus());

        return getById(request.getEvaluatorVersionId());
    }
}
