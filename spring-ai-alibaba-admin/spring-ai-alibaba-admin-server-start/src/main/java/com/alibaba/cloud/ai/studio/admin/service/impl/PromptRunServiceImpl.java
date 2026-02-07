package com.alibaba.cloud.ai.studio.admin.service.impl;

import com.alibaba.cloud.ai.studio.admin.dto.ChatMessage;
import com.alibaba.cloud.ai.studio.admin.dto.ChatMessageMetrics;
import com.alibaba.cloud.ai.studio.admin.dto.ChatSession;
import com.alibaba.cloud.ai.studio.admin.dto.MockTool;
import com.alibaba.cloud.ai.studio.admin.dto.PromptRunResponse;
import com.alibaba.cloud.ai.studio.admin.dto.request.PromptRunRequest;
import com.alibaba.cloud.ai.studio.admin.service.ChatSessionService;
import com.alibaba.cloud.ai.studio.admin.service.PromptRunService;
import com.alibaba.cloud.ai.studio.admin.service.advisors.TraceIdEnrichAdvisor;
import com.alibaba.cloud.ai.studio.admin.service.client.ChatClientFactoryDelegate;
import com.alibaba.cloud.ai.studio.admin.utils.ModelConfigParser;
import com.alibaba.cloud.ai.studio.admin.utils.SessionUtils;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.micrometer.observation.ObservationRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.AdvisorUtils;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

@Slf4j
@Service
@RequiredArgsConstructor
public class PromptRunServiceImpl implements PromptRunService {
    
    private final ChatSessionService chatSessionService;
    
    private final ChatClientFactoryDelegate chatClientFactoryDelegate;
    
    private final ModelConfigParser modelConfigParser;
    
    private final ObjectMapper objectMapper;
    
    private final ObservationRegistry observationRegistry;
    
    @Override
    public Flux<PromptRunResponse> run(PromptRunRequest request) {
        log.info("运行带会话的Prompt调试: {}", request);
        
        try {
            //1. Get or create a session
            ChatSession session = getOrCreateSession(request);
            
            //2. Add user messages to the conversation
            session.addUserMessage(request.getMessage());
            chatSessionService.updateSession(session);
            if (StringUtils.hasText(request.getPromptKey())) {
                session.setPromptKey(request.getPromptKey());
            }
            if (StringUtils.hasText(request.getVersion())) {
                session.setVersion(request.getVersion());
            }
            
            session.setTemplate(request.getTemplate());
            session.setVariables(request.getVariables());
            session.setMockTools(request.getMockTools());
            session.setModelConfig(modelConfigParser.checkAndGetModelConfigInfo(request.getModelConfig()));
            
            //3. Return session information
            PromptRunResponse sessionInfo = PromptRunResponse.createSessionInfoResponse(session);
            
            return Flux.concat(
                    //First return session information
                    Flux.just(sessionInfo),
                    
                    //Then return the real AI streaming response
                    generateRealAIResponse(session, request).onErrorResume(error -> {
                        log.error("模型调用失败，返回错误响应", error);
                        return Flux.just(PromptRunResponse.createErrorResponse(session.getSessionId(),
                                "模型调用失败: " + error.getMessage()));
                    }));
            
        } catch (Exception e) {
            log.error("处理会话请求失败", e);
            return Flux.just(PromptRunResponse.createErrorResponse(null, "处理请求失败: " + e.getMessage()));
        }
    }
    
    @Override
    public ChatSession getSession(String sessionId) {
        return chatSessionService.getSession(sessionId);
    }
    
    @Override
    public void deleteSession(String sessionId) {
        chatSessionService.deleteSession(sessionId);
    }
    
    /**
     * Get or create a session
     */
    private ChatSession getOrCreateSession(PromptRunRequest request) {
        //If a new session is forced to be created or no sessionId is provided, create a new session
        if (Boolean.TRUE.equals(request.getNewSession()) || request.getSessionId() == null || request.getSessionId()
                .trim().isEmpty()) {
            return chatSessionService.createSessionWithMockTools(request.getPromptKey(), request.getVersion(),
                    request.getTemplate(), request.getVariables(), request.getModelConfig(), request.getMockTools());
        }
        
        //Try to get existing session
        ChatSession existingSession = chatSessionService.getSession(request.getSessionId());
        if (existingSession != null) {
            return existingSession;
        }
        
        //Session does not exist, create a new session
        log.warn("会话 {} 不存在，创建新会话", request.getSessionId());
        return chatSessionService.createSessionWithMockTools(request.getPromptKey(), request.getVersion(),
                request.getTemplate(), request.getVariables(), request.getModelConfig(), request.getMockTools());
    }
    
    /**
     * Generate realistic AI streaming responses
     *
     * @param session session object
     * @return streaming response
     */
    private Flux<PromptRunResponse> generateRealAIResponse(ChatSession session, PromptRunRequest request) {
        //Container for collecting full responses
        AtomicReference<StringBuilder> completeResponse = new AtomicReference<>(new StringBuilder());
        AtomicReference<ChatMessageMetrics> metrics = new AtomicReference<>(ChatMessageMetrics.builder().build());
        
        String fullPrompt = modelConfigParser.replaceVariables(session.getTemplate(), session.getVariables());
        Map<String, String> observationMetadata = new HashMap<>(4);
        if (StringUtils.hasText(request.getPromptKey()) && !"playground".equals(request.getPromptKey())) {
            observationMetadata.put("promptKey", request.getPromptKey());
            observationMetadata.put("promptVersion", request.getVersion());
            observationMetadata.put("promptTemplate", request.getTemplate());
            observationMetadata.put("promptVariables", request.getVariables());
            observationMetadata.put("studioSource", "prompt");
        } else {
            observationMetadata.put("promptKey", "playground-" + System.currentTimeMillis());
            observationMetadata.put("promptTemplate", request.getTemplate());
            observationMetadata.put("promptVariables", request.getVariables());
            observationMetadata.put("studioSource", "playground");
        }
        List<Advisor> advisors = new ArrayList<>();
        advisors.add(new TraceIdEnrichAdvisor(observationRegistry));
        ChatClient client = chatClientFactoryDelegate.createChatClient(session.getModelConfig().getModelId(),
                session.getModelConfig().getParameters(), advisors, observationMetadata);
        
        List<ToolCallback> functionToolCallbacks = buildMockToolBacks(request.getMockTools());
        
        List<Message> messages = new ArrayList<>();
        messages.add(new SystemMessage(fullPrompt));
        messages.addAll(SessionUtils.convertChatMessages(session.getMessages()));
        Prompt prompt = new Prompt(messages);
        return client.prompt(prompt).toolCallbacks(functionToolCallbacks).stream().chatClientResponse()
                .map(response -> {
                    //Collect full response
                    ChatResponse chatResponse = response.chatResponse();
                    assert chatResponse != null;
                    if (AdvisorUtils.onFinishReason().test(response)) {
                        Usage usage = chatResponse.getMetadata().getUsage();
                        String traceId = (String) response.context().get("traceId");
                        metrics.set(ChatMessageMetrics.builder().usage(usage).traceId(traceId).build());
                        return PromptRunResponse.createMetricsResponse(session.getSessionId(), metrics.get());
                    } else if (chatResponse.getResult() != null && chatResponse.getResult().getOutput() != null) {
                        String content = chatResponse.getResult().getOutput().getText();
                        completeResponse.get().append(content);
                        return PromptRunResponse.createMessageResponse(session.getSessionId(), content);
                    } else {
                        return PromptRunResponse.createMessageResponse(session.getSessionId(), "");
                    }
                }).doOnComplete(() -> {
                    //When the response is complete, add the full response to the session history
                    try {
                        String fullResponse = completeResponse.get().toString();
                        if (StringUtils.hasText(fullResponse)) {
                            ChatMessage assistantMessage = ChatMessage.createAssistantMessage(fullResponse);
                            assistantMessage.setMetrics(metrics.get());
                            session.addMessage(assistantMessage);
                            chatSessionService.updateSession(session);
                            log.info("会话 {} 完成AI响应，响应长度: {}", session.getSessionId(), fullResponse.length());
                        }
                    } catch (Exception e) {
                        log.error("更新会话历史失败: sessionId={}", session.getSessionId(), e);
                    }
                });
    }
    
    public List<ToolCallback> buildMockToolBacks(List<MockTool> mockTools) {
        List<ToolCallback> mockToolCallbacks = new ArrayList<>();
        if (mockTools == null) {
            return mockToolCallbacks;
        }
        for (MockTool mockTool : mockTools) {
            String name = mockTool.getToolDefinition().getName();
            String description = mockTool.getToolDefinition().getDescription();
            String output = mockTool.getOutput();
            String inputSchema = mockTool.getToolDefinition().getParameters();
            MockFunction mockFunction = new MockFunction(output, inputSchema);
            ToolCallback functionToolCallback = FunctionToolCallback.builder(name, mockFunction)
                    .description(description).inputSchema(inputSchema).inputType(Map.class).build();
            mockToolCallbacks.add(functionToolCallback);
        }
        return mockToolCallbacks;
    }
    
    
    public class MockFunction implements Function<Map<String, Object>, String> {
        
        private final String output;
        
        private final String inputSchema;
        
        public MockFunction(String output, String inputSchema) {
            this.output = output;
            this.inputSchema = inputSchema;
        }
        
        @Override
        public String apply(Map<String, Object> inputMap) {
            try {
                JsonNode schemaNode = objectMapper.readTree(inputSchema);
                JsonNode dataNode = objectMapper.valueToTree(inputMap);
                
                JsonSchemaFactory factory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V7);
                JsonSchema schema = factory.getSchema(schemaNode);
                Set<ValidationMessage> errors = schema.validate(dataNode);
                
                if (!errors.isEmpty()) {
                    throw new IllegalArgumentException("Tool Calls Invalid input data: " + errors);
                }
                return this.output;
                
            } catch (JsonProcessingException e) {
                log.error("JSON 处理失败: ", e);
                throw new RuntimeException("JSON 处理失败: " + e.getMessage(), e);
            } catch (Exception e) {
                log.error("JSON 处理失败: ", e);
                throw new RuntimeException("Schema 校验失败: " + e.getMessage(), e);
            }
        }
        
    }
    
}
