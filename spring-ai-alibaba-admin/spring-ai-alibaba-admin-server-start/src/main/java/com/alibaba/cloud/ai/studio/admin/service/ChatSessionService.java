package com.alibaba.cloud.ai.studio.admin.service;

import com.alibaba.cloud.ai.studio.admin.dto.ChatSession;
import com.alibaba.cloud.ai.studio.admin.dto.MockTool;
import org.springframework.ai.chat.client.ChatClient;

import java.util.List;
import java.util.Map;

public interface ChatSessionService {
    
    /**
     * Create new session
     *
     * @param promptKey   Prompt Key
     * @param version version number
     * @param template Prompt template
     * @param variables variable configuration
     * @param modelConfig model configuration
     * @return session object
     */
    default ChatSession createSession(String promptKey, String version, String template, String variables, String modelConfig){
        return createSessionWithMockTools(promptKey, version, template, variables, modelConfig, null);
    }
    
    
    /**
     * Create new session
     *
     * @param promptKey   Prompt Key
     * @param version version number
     * @param template Prompt template
     * @param variables variable configuration
     * @param modelConfig model configuration
     * @param mockTools list of simulation tools
     * @return session object
     */
    ChatSession createSessionWithMockTools(String promptKey, String version, String template, String variables, String modelConfig, List<MockTool> mockTools);


    /**
     * Create new session
     *
     * @param variables variable configuration
     * @param modelConfig model configuration
     * @return session object
     */
    ChatSession createEvaluatorSession(String prompt, String variables, String modelConfig);
    
    /**
     * Get session
     *
     * @param sessionId session ID
     * @return session object, return null if it does not exist
     */
    ChatSession getSession(String sessionId);
    
    /**
     * update session
     *
     * @param session session object
     */
    void updateSession(ChatSession session);
    
    /**
     * Delete session
     *
     * @param sessionId session ID
     */
    void deleteSession(String sessionId);
    
    /**
     * Clean up expired sessions
     */
    void cleanExpiredSessions();
    
    /**
     * Get the session-bound ChatClient
     *
     * @param sessionId session ID
     * @return ChatClient or null
     */
    ChatClient getSessionChatClient(String sessionId);
    
    /**
     * Get or create the ChatClient of the session
     *
     * @param sessionId session ID
     * @return ChatClient instance
     */
    ChatClient getOrCreateSessionChatClient(String sessionId, Map<String, String> observationMetadata);
}
