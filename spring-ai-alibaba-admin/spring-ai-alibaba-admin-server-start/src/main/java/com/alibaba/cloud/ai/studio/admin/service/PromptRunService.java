package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.dto.ChatSession;
import com.alibaba.cloud.ai.studio.admin.dto.PromptRunResponse;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptRunRequest;
import reactor.core.publisher.Flux;

public interface PromptRunService {

    /**
     * Run Prompt debugging (supports continuous interaction)
     *
     * @param request debugging request
     * @return streaming response, including session information
     */
    Flux<PromptRunResponse> run(PromptRunRequest request);

    /**
     * Get session information
     *
     * @param sessionId session ID
     * @return session object
     */
    ChatSession getSession(String sessionId);

    /**
     * Delete session
     *
     * @param sessionId session ID
     */
    void deleteSession(String sessionId);
}
