package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.dto.ChatSession;
import com.alibaba.cloud.ai.studio.admin.dto.ModelConfigInfo;
import com.alibaba.cloud.ai.studio.admin.dto.MockTool;
import com.alibaba.cloud.ai.studio.admin.service.ChatSessionService;
import com.alibaba.cloud.ai.studio.admin.service.client.ChatClientFactoryDelegate;
import com.alibaba.cloud.ai.studio.admin.utils.ModelConfigParser;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatSessionServiceImpl implements ChatSessionService {
    
    /**
     * Session expiration time (30 minutes)
     */
    private static final long SESSION_EXPIRE_TIME = 30 * 60 * 1000L;
    
    private final ChatClientFactoryDelegate chatClientFactoryDelegate;
    
    private final ModelConfigParser modelConfigParser;
    
    /**
     * Session storage Map (Redis is recommended for production environments)
     */
    private final Map<String, ChatSession> sessionStore = new ConcurrentHashMap<>();
    
    /**
     * Binding relationship between session and ModelClient
     */
    private final Map<String, ChatClient> sessionClients = new ConcurrentHashMap<>();
    
    @Override
    public ChatSession createSessionWithMockTools(String promptKey, String version, String template, String variables,
            String modelConfig, List<MockTool> mockTools) {
        String sessionId = UUID.randomUUID().toString();
        long currentTime = System.currentTimeMillis();
        ModelConfigInfo modelConfigInfo = modelConfigParser.checkAndGetModelConfigInfo(modelConfig);
        ChatSession session = ChatSession.builder().sessionId(sessionId).promptKey(promptKey).version(version)
                .template(template).variables(variables).modelConfig(modelConfigInfo).createTime(currentTime)
                .lastUpdateTime(currentTime).mockTools(mockTools).build();
        sessionStore.put(sessionId, session);
        log.info("Create new session: {}", sessionId);
        return session;
    }
    
    @Override
    public ChatSession createEvaluatorSession(String prompt, String variables, String modelConfig) {
        String sessionId = UUID.randomUUID().toString();
        ModelConfigInfo modelConfigInfo = modelConfigParser.checkAndGetModelConfigInfo(modelConfig);
        ChatSession session = ChatSession.builder().template(prompt).variables(variables).modelConfig(modelConfigInfo)
                .sessionId(sessionId).createTime(System.currentTimeMillis()).lastUpdateTime(System.currentTimeMillis())
                .build();
        sessionStore.put(sessionId, session);
        return session;
    }
    
    @Override
    public ChatSession getSession(String sessionId) {
        if (sessionId == null || sessionId.trim().isEmpty()) {
            return null;
        }
        
        ChatSession session = sessionStore.get(sessionId);
        if (session == null) {
            log.warn("Session does not exist: {}", sessionId);
            return null;
        }
        
        //Check if the session has expired
        if (isSessionExpired(session)) {
            log.info("Session has expired, delete: {}", sessionId);
            sessionStore.remove(sessionId);
            return null;
        }
        
        return session;
    }
    
    @Override
    public void updateSession(ChatSession session) {
        if (session != null && session.getSessionId() != null) {
            session.setLastUpdateTime(System.currentTimeMillis());
            sessionStore.put(session.getSessionId(), session);
            log.debug("Update session: {}", session.getSessionId());
        }
    }
    
    @Override
    public void deleteSession(String sessionId) {
        if (sessionId != null) {
            sessionStore.remove(sessionId);
            sessionClients.remove(sessionId);
            log.info("Delete the session and its ModelClient: {}", sessionId);
        }
    }
    
    @Override
    @Scheduled(fixedRate = 10 * 60 * 1000) //Executed every 10 minutes
    public void cleanExpiredSessions() {
        final int[] cleanedCount = {0}; //Use arrays to work around final restrictions
        
        sessionStore.entrySet().removeIf(entry -> {
            ChatSession session = entry.getValue();
            if (isSessionExpired(session)) {
                log.debug("Clean up expired sessions: {}", entry.getKey());
                cleanedCount[0]++;
                return true;
            }
            return false;
        });
        
        if (cleanedCount[0] > 0) {
            log.info("Cleaned {} expired sessions", cleanedCount[0]);
        }
    }
    
    @Override
    public ChatClient getSessionChatClient(String sessionId) {
        return sessionClients.get(sessionId);
    }
    
    
    @Override
    public ChatClient getOrCreateSessionChatClient(String sessionId, Map<String, String> observationMetadata) {
        return sessionClients.computeIfAbsent(sessionId, key -> {
            ChatSession session = getSession(sessionId);
            if (session == null) {
                throw new RuntimeException("Session does not exist:" + sessionId);
            }
            return chatClientFactoryDelegate.createChatClient(session.getModelConfig().getModelId(),
                    session.getModelConfig().getParameters(), observationMetadata);
        });
    }
    
    /**
     * Check if the session has expired
     */
    private boolean isSessionExpired(ChatSession session) {
        long currentTime = System.currentTimeMillis();
        return (currentTime - session.getLastUpdateTime()) > SESSION_EXPIRE_TIME;
    }
    
    /**
     * Get the total number of current sessions (for monitoring)
     */
    public int getSessionCount() {
        return sessionStore.size();
    }
    
}
