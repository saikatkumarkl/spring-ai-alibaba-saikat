package com.alibaba.cloud.ai.studio.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatSession {

    /**
     * Session ID
     */
    private String sessionId;

    /**
     * Prompt Key
     */
    private String promptKey;

    /**
     * Prompt version
     */
    private String version;

    /**
     * Prompt template
     */
    private String template;

    /**
     * Variable configuration (JSON string)
     */
    private String variables;

    /**
     * Model configuration (JSON string)
     */
    private ModelConfigInfo modelConfig;

    /**
     * Conversation message history
     */
    @Builder.Default
    private List<ChatMessage> messages = new ArrayList<>();
    
    @Builder.Default
    private List<MockTool> mockTools = new ArrayList<>();

    /**
     * Session creation time
     */
    private Long createTime;

    /**
     * Last updated
     */
    private Long lastUpdateTime;

    /**
     * Add message to conversation
     */
    public void addMessage(ChatMessage message) {
        this.messages.add(message);
        this.lastUpdateTime = System.currentTimeMillis();
    }

    /**
     * Add user message
     */
    public void addUserMessage(String content) {
        addMessage(ChatMessage.createUserMessage(content));
    }

    /**
     * Add assistant message
     */
    public void addAssistantMessage(String content) {
        addMessage(ChatMessage.createAssistantMessage(content));
    }

    /**
     * Get the number of messages
     */
    public int getMessageCount() {
        return messages.size();
    }

    /**
     * Whether it is a new session (no message history)
     */
    public boolean isNewSession() {
        return messages.isEmpty();
    }
}
