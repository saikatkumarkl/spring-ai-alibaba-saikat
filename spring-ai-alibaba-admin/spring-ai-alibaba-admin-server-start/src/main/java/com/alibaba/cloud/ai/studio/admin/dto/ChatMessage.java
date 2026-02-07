package com.alibaba.cloud.ai.studio.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessage {
    
    /**
     * Message role: user-user, assistant-assistant, system-system
     */
    private String role;
    
    /**
     * Message content
     */
    private String content;
    
    /**
     * Message timestamp
     */
    private Long timestamp;
    
    
    /**
     * message indicators
     */
    private ChatMessageMetrics metrics;
    
    /**
     * Create user message
     */
    public static ChatMessage createUserMessage(String content) {
        return ChatMessage.builder().role("user").content(content).timestamp(System.currentTimeMillis()).build();
    }
    
    /**
     * Create assistant message
     */
    public static ChatMessage createAssistantMessage(String content) {
        return ChatMessage.builder().role("assistant").content(content).timestamp(System.currentTimeMillis()).build();
    }
    
    /**
     * Create system message
     */
    public static ChatMessage createSystemMessage(String content) {
        return ChatMessage.builder().role("system").content(content).timestamp(System.currentTimeMillis()).build();
    }
}
