package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.PromptTemplate;
import com.alibaba.cloud.ai.studio.admin.dto.PromptTemplateDetail;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptTemplateListRequest;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;

public interface PromptTemplateService {

    /**
     * Get Prompt template details based on template Key
     *
     * @param promptTemplateKey template Key
     * @return Prompt template details
     */
    PromptTemplateDetail getByPromptTemplateKey(String promptTemplateKey) throws StudioException;

    /**
     * Paginated query prompt template list
     *
     * @param request query request
     * @return paginated results
     */
    PageResult<PromptTemplate> list(PromptTemplateListRequest request) throws StudioException;
}
