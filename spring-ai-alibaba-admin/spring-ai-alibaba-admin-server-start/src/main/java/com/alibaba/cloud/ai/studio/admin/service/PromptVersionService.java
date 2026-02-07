package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.PromptVersion;
import com.alibaba.cloud.ai.studio.admin.dto.PromptVersionDetail;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptVersionCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptVersionListRequest;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;

public interface PromptVersionService {

    /**
     * Create prompt version
     *
     * @param request create request
     * @return Prompt version
     */
    PromptVersion create(PromptVersionCreateRequest request) throws StudioException;

    /**
     * Get Prompt version details based on Prompt Key and version
     *
     * @param promptKey Prompt Key
     * @param version version number
     * @return Prompt version details
     */
    PromptVersionDetail getByPromptKeyAndVersion(String promptKey, String version) throws StudioException;

    /**
     * Query Prompt version list by pagination
     *
     * @param request query request
     * @return paginated results
     */
    PageResult<PromptVersion> list(PromptVersionListRequest request);
}
