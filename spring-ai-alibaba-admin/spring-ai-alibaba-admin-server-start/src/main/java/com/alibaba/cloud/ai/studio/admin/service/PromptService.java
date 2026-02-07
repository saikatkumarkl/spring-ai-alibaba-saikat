package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.common.PageResult;
import com.alibaba.cloud.ai.studio.admin.dto.Prompt;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptCreateRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptListRequest;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptUpdateRequest;
import com.alibaba.cloud.ai.studio.admin.exception.StudioException;

public interface PromptService {

    /**
     * CreatePrompt
     *
     * @param request create request
     * @return Prompt
     */
    Prompt create(PromptCreateRequest request) throws StudioException;

    /**
     * Get Prompt based on Prompt Key
     *
     * @param promptKey Prompt Key
     * @return Prompt
     */
    Prompt getByPromptKey(String promptKey) throws StudioException;

    /**
     * Query Prompt list by pagination
     *
     * @param request query request
     * @return paginated results
     */
    PageResult<Prompt> list(PromptListRequest request) throws StudioException;

    /**
     * UpdatePrompt
     *
     * @param request update request
     * @return Prompt
     */
    Prompt update(PromptUpdateRequest request) throws StudioException;

    /**
     * Delete Prompt based on Prompt Key
     *
     * @param promptKey Prompt Key
     */
    void deleteByPromptKey(String promptKey) throws StudioException;

    /**
     * Update to latest version
     *
     * @param promptKey     Prompt Key
     * @param latestVersion latest version
     */
    void updateLatestVersion(String promptKey, String latestVersion);
}
