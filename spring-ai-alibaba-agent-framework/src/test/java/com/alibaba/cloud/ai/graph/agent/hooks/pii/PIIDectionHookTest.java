/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.graph.agent.hooks.pii;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIDetectionHook;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIType;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIDetectors;
import com.alibaba.cloud.ai.graph.agent.hook.pii.RedactionStrategy;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
public class PIIDectionHookTest {

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();
        this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
    }

    @Test
    public void testPIIDetectionWithRedactStrategy() throws Exception {
        PIIDetectionHook hook = PIIDetectionHook.builder()
                .piiType(PIIType.EMAIL)
                .strategy(RedactionStrategy.REDACT)
                .applyToInput(true)
                .applyToOutput(true)
                .build();

        ReactAgent agent = createAgent(hook, "test-pii-redact-agent", chatModel);

        System.out.println("=== Test PII detection (REDACT strategy) ===");

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("My email address is test@example.com, please remember it."));

        Optional<OverAllState> result = agent.invoke(messages);

        assertTrue(result.isPresent(), "The result should exist");
        Object messagesObj = result.get().value("messages").get();
        assertNotNull(messagesObj, "The message should be present in the result");

        if (messagesObj instanceof List) {
            List<Message> resultMessages = (List<Message>) messagesObj;
            System.out.println("Number of messages returned:" + resultMessages.size());

            for (Message message : resultMessages) {
                if (message instanceof UserMessage) {
                    String content = message.getText();
                    if (content.contains("[REDACTED_EMAIL]")) {
                        System.out.println("✓ Successfully detect and replace email addresses in user messages");
                    }
                } else if (message instanceof AssistantMessage) {
                    String content = message.getText();
                    System.out.println("AI reply:" + content);
                }
            }
        }
    }

    @Test
    public void testPIIDetectionWithMaskStrategy() throws Exception {
        PIIDetectionHook hook = PIIDetectionHook.builder()
                .piiType(PIIType.CREDIT_CARD)
                .strategy(RedactionStrategy.MASK)
                .applyToInput(true)
                .applyToOutput(true)
                .build();

        ReactAgent agent = createAgent(hook, "test-pii-mask-agent", chatModel);

        System.out.println("\n=== Test PII detection (MASK strategy) ===");

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("My credit card number is 1234 5678 9012 3456, please check it for me."));

        Optional<OverAllState> result = agent.invoke(messages);

        assertTrue(result.isPresent(), "The result should exist");
        Object messagesObj = result.get().value("messages").get();
        assertNotNull(messagesObj, "The message should be present in the result");

        if (messagesObj instanceof List) {
            List<Message> resultMessages = (List<Message>) messagesObj;
            System.out.println("Number of messages returned:" + resultMessages.size());
            for (Message message : resultMessages) {
                if (message instanceof UserMessage) {
                    String content = message.getText();
                    if (content.contains("****") && content.contains("3456")) {
                        System.out.println("Successfully detects and partially masks credit card numbers in user messages");
                    }
                    System.out.println("Processed user message:" + content);
                }
            }
        }
    }

    @Test
    public void testPIIDetectionWithBlockStrategy() throws Exception {
        PIIDetectionHook hook = PIIDetectionHook.builder()
                .piiType(PIIType.IP)
                .strategy(RedactionStrategy.BLOCK)
                .applyToInput(true)
                .build();

        ReactAgent agent = createAgent(hook, "test-pii-block-agent", chatModel);

        System.out.println("\n=== Test PII detection (BLOCK strategy) ===");

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("My server IP address is 192.168.1.100, please do not disclose it."));

        try {
            Optional<OverAllState> result = agent.invoke(messages);
            System.out.println("No exception was thrown, maybe the IP was not detected correctly");
        } catch (Exception e) {
            if (e.getCause() instanceof com.alibaba.cloud.ai.graph.agent.hook.pii.PIIDetectionException) {
                System.out.println("✓ IP address successfully detected and blocked for processing:" + e.getCause().getMessage());
            } else {
                System.out.println("Throw other exceptions:" + e.getMessage());
            }
        }
    }

    @Test
    public void testWithoutPIIDetection() throws Exception {
        ReactAgent agent = ReactAgent.builder()
                .name("test-no-pii-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .build();

        System.out.println("\n=== Testing the conversation without PII detection ===");

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("Hello, how can I help you?"));

        Optional<OverAllState> result = agent.invoke(messages);

        assertTrue(result.isPresent(), "The result should exist");
        Object messagesObj = result.get().value("messages").get();
        assertNotNull(messagesObj, "The message should be present in the result");

        if (messagesObj instanceof List) {
            List<Message> resultMessages = (List<Message>) messagesObj;
            System.out.println("Number of messages returned:" + resultMessages.size());
            System.out.println("✓ Normal conversation flow, no PII detection triggered");
        }
    }

    @Test
    public void testCustomPIIDetector() throws Exception {
        PIIDetectionHook hook = PIIDetectionHook.builder()
                .piiType(PIIType.CUSTOM)
                .strategy(RedactionStrategy.REDACT)
                .detector(PIIDetectors.regexDetector("PHONE", "\\b1[3-9]\\d{9}\\b"))
                .applyToInput(true)
                .build();

        ReactAgent agent = createAgent(hook, "test-custom-pii-agent", chatModel);

        System.out.println("\n=== Test custom PII detector ===");

        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("My mobile phone number is 13812345678, please save it."));

        Optional<OverAllState> result = agent.invoke(messages);

        assertTrue(result.isPresent(), "The result should exist");
        Object messagesObj = result.get().value("messages").get();
        assertNotNull(messagesObj, "The message should be present in the result");

        if (messagesObj instanceof List) {
            List<Message> resultMessages = (List<Message>) messagesObj;
            System.out.println("Number of messages returned:" + resultMessages.size());

            for (Message message : resultMessages) {
                if (message instanceof UserMessage) {
                    String content = message.getText();
                    if (content.contains("[REDACTED_PHONE]")) {
                        System.out.println("Successfully detected and replaced mobile phone numbers in user messages");
                    }
                    System.out.println("Processed user message:" + content);
                }
            }
        }
    }

    public ReactAgent createAgent(PIIDetectionHook hook, String name, ChatModel model) throws Exception {
        return ReactAgent.builder()
                .name(name)
                .model(model)
                .hooks(List.of(hook))
                .saver(new MemorySaver())
                .build();
    }

    
}
