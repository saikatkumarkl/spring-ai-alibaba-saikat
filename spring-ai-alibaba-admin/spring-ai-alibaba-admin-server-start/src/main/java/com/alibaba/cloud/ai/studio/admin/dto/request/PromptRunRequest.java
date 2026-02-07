package com.alibaba.cloud.ai.studio.admin.dto.request;

import com.alibaba.cloud.ai.studio.admin.dto.MockTool;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class PromptRunRequest {

    /**
     * Session ID (optional, if provided continues the conversation, otherwise creates a new session)
     */
    private String sessionId;

    /**
     * Prompt Key (optional)
     */
    @Size(max = 255, message = "Prompt Key长度不能超过255个字符")
    @Pattern(regexp = "^[a-zA-Z0-9_-]*$", message = "Prompt Key只能包含字母、数字、下划线和短横线")
    private String promptKey;

    /**
     * Version number (optional)
     */
    @Size(max = 32, message = "版本号长度不能超过32个字符")
    private String version;

    /**
     * Prompt content (used in new sessions)
     */
    private String template;

    /**
     * Variable value in Prompt, JSON format (used in new session)
     */
    private String variables;

    /**
     * Model related parameters used, JSON format (used in new sessions)
     */
    private String modelConfig;

    /**
     * User message content
     */
    @NotBlank(message = "用户消息不能为空")
    private String message;

    /**
     * Whether to create a new session (force to create a new session, ignore sessionId)
     */
    private Boolean newSession;
    
    
    /**
     * Tool list
     */
    private List<MockTool> mockTools;
}
