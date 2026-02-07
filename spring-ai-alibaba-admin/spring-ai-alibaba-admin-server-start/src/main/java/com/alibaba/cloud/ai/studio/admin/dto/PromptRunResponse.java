package com.alibaba.cloud.ai.studio.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PromptRunResponse {
    
    /**
     * Session ID
     */
    private String sessionId;
    
    /**
     * Is it a new session?
     */
    private Boolean newSession;
    
    /**
     * Message content (used when streaming responses)
     */
    private String content;
    
    /**
     * Response type: message-message content, session_info-session information, error-error information, metrics-metric information
     */
    private String type;
    
    /**
     * Session message history (included with session information)
     */
    private List<ChatMessage> messages;
    
    /**
     * Total number of messages
     */
    private Integer messageCount;
    
    /**
     * Error message (used in case of error)
     */
    private String error;
    
    /**
     * Metric information (included in metrics)
     */
    private ChatMessageMetrics metrics;
    
    /**
     * Create message response
     */
    public static PromptRunResponse createMessageResponse(String sessionId, String content) {
        return PromptRunResponse.builder().sessionId(sessionId).content(content).type("message").build();
    }
    
    /**
     * Create session information response
     */
    public static PromptRunResponse createSessionInfoResponse(ChatSession session) {
        return PromptRunResponse.builder().sessionId(session.getSessionId()).newSession(session.isNewSession())
                .type("session_info").messages(session.getMessages()).messageCount(session.getMessageCount()).build();
    }
    
    /**
     * Create error response
     */
    public static PromptRunResponse createErrorResponse(String sessionId, String error) {
        return PromptRunResponse.builder().sessionId(sessionId).error(error).type("error").build();
    }
    
    public static PromptRunResponse createMetricsResponse(String sessionId, ChatMessageMetrics metrics) {
        return PromptRunResponse.builder().sessionId(sessionId).type("metrics").metrics(metrics).build();
    }
}
