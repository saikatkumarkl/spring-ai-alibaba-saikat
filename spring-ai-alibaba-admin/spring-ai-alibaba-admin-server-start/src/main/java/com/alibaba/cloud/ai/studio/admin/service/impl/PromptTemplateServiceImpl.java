package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.PromptTemplate;
import com.alibaba.cloud.ai.studio.admin.dto.PromptTemplateDetail;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptTemplateListRequest;
import com.alibaba.cloud.ai.studio.admin.entity.PromptTemplateDO;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;
import com.alibaba.cloud.ai.studio.admin.mapper.PromptTemplateMapper;
import com.alibaba.cloud.ai.studio.admin.service.PromptTemplateService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptTemplateServiceImpl implements PromptTemplateService {

    private final PromptTemplateMapper promptTemplateMapper;

    @Override
    public PromptTemplateDetail getByPromptTemplateKey(String promptTemplateKey) throws StudioException {
        log.info("Query Prompt template details: {}", promptTemplateKey);

        PromptTemplateDO promptTemplateDO = promptTemplateMapper.selectByPromptTemplateKey(promptTemplateKey);
        if (promptTemplateDO == null) {
            throw new StudioException(StudioException.NOT_FOUND, "Prompt template does not exist:" + promptTemplateKey);
        }
        return PromptTemplateDetail.fromDO(promptTemplateDO);
    }

    @Override
    public PageResult<PromptTemplate> list(PromptTemplateListRequest request) throws StudioException {
        log.info("Query Prompt template list: {}", request);
        
        //Validate search pattern parameters
        if (request.getSearch() != null && 
            !"accurate".equals(request.getSearch()) &&
            !"blur".equals(request.getSearch())) {
            throw new StudioException(StudioException.INVALID_PARAM, "Search mode must be accurate or blur");
        }

        int offset = (request.getPageNo() - 1) * request.getPageSize();

        List<PromptTemplateDO> promptTemplateDOList = promptTemplateMapper.selectList(
                request.getSearch(),
                request.getTag(),
                request.getPromptTemplateKey(),
                offset,
                request.getPageSize()
        );

        int totalCount = promptTemplateMapper.selectCount(
                request.getSearch(),
                request.getTag(),
                request.getPromptTemplateKey()
        );

        List<PromptTemplate> promptTemplateList = promptTemplateDOList.stream()
                .map(PromptTemplate::fromDO)
                .collect(Collectors.toList());

        return new PageResult<>((long) totalCount, (long) request.getPageNo(), (long) request.getPageSize(), promptTemplateList);
    }
}
