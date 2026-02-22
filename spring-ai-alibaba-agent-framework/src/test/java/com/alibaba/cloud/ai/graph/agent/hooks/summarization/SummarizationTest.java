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
package com.alibaba.cloud.ai.graph.agent.hooks.summarization;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
public class SummarizationTest {

    private ChatModel chatModel;

    @BeforeEach
    void setUp() {
        DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();
        this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
    }

    @Test
    public void testSummarizationEffect() throws Exception {
        // mock
        List<Message> longConversation = createLongConversation(50);


        SummarizationHook hook = SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(200) //Set a lower threshold to trigger summarization
                .messagesToKeep(10) //Keep the last 10 messages
                .build();

        ReactAgent agent = createAgent(hook, "test-summarization-agent", chatModel);

        System.out.println("=== Testing dialogue with summary function ===");
        System.out.println("Initial number of messages:" + longConversation.size());
        
        //Calling agent should trigger summary
        Optional<OverAllState> result = agent.invoke(longConversation);

        //Verification results
        assertTrue(result.isPresent(), "The result should exist");
        Object messagesObj = result.get().value("messages").get();
        assertNotNull(messagesObj, "The message should be present in the result");

        if (messagesObj instanceof List) {
            List<Message> messages = (List<Message>) messagesObj;
            System.out.println("Number of messages after summary:" + messages.size());

            if (!messages.isEmpty()) {
                Message firstMessage = messages.get(0);
                if (firstMessage.getText().contains("summary of the conversation")) {
                    System.out.println("Summary function");
                    System.out.println("Summary message preview:" + firstMessage.getText().substring(0, 
                        Math.min(100, firstMessage.getText().length())) + "...");
                }
            }
        }
    }

    @Test
    public void testWithoutSummarization() throws Exception {
        // mock
        List<Message> shortConversation = createShortConversation();

        ReactAgent agent = ReactAgent.builder()
                .name("test-no-summarization-agent")
                .model(chatModel)
                .saver(new MemorySaver())
                .build();

        System.out.println("\n=== Test dialogue without summary function ===");
        System.out.println("Initial number of messages:" + shortConversation.size());

        //call agent
        Optional<OverAllState> result = agent.invoke(shortConversation);

        //Verification results
        assertTrue(result.isPresent(), "The result should exist");
        Object messagesObj = result.get().value("messages").get();
        assertNotNull(messagesObj, "The message should be present in the result");
        
        if (messagesObj instanceof List) {
            List<Message> messages = (List<Message>) messagesObj;
            System.out.println("Number of messages processed:" + messages.size());
            System.out.println("✓ Normal dialogue flow, no summary triggered");
        }
    }

    private List<Message> createLongConversation(int messageCount) {
        List<Message> messages = new ArrayList<>();
        //Add initial system message
        messages.add(new UserMessage("Let's start a long conversation to test the summary feature."));
        messages.add(new AssistantMessage("OK, I understand.Let's run a long conversation test."));
        
        //Added a large number of alternating user and assistant messages
        for (int i = 0; i < messageCount; i++) {
            if (i % 2 == 0) {
                messages.add(new UserMessage("User messages" + i + ": This is a user message in the conversation, containing some content to increase the number of tokens. We need enough text to ensure that the summary function can be triggered."));
            } else {
                messages.add(new AssistantMessage("Assistant message" + i + ": This is an assistant reply in the conversation, and it also contains some content to increase the number of tokens. We need enough text to ensure that the summary function can be triggered."));
            }
        }
        
        //Add last few messages
        messages.add(new UserMessage("This is the second to last message."));
        messages.add(new AssistantMessage("I received your message."));
        messages.add(new UserMessage("This is the last message, please summarize the above conversation."));
        return messages;
    }

    private List<Message> createShortConversation() {
        List<Message> messages = new ArrayList<>();
        messages.add(new UserMessage("Hello"));
        messages.add(new AssistantMessage("Hello!Is there anything I can do to help you?"));
        messages.add(new UserMessage("I want to understand how the summary function works"));
        messages.add(new AssistantMessage("The summary feature automatically summarizes earlier content when a conversation becomes long to avoid exceeding the token limit."));
        messages.add(new UserMessage("thank you for your explanation"));
        return messages;
    }

    public ReactAgent createAgent(SummarizationHook hook, String name, ChatModel model) throws GraphStateException {
        return ReactAgent.builder()
                .name(name)
                .model(model)
                .hooks(List.of(hook))
                .saver(new MemorySaver())
                .build();
    }

    @Test
    public void testSystemMessagePreservation() throws Exception {
        List<Message> conversation = new ArrayList<>();
        
        String firstUserPrompt = "I need your help analyzing a complex technical problem.";
        conversation.add(new UserMessage(firstUserPrompt));
        conversation.add(new AssistantMessage("OK, I'll be happy to help you.Please describe your problem in detail."));
        for (int i = 0; i < 50; i++) {
            conversation.add(new UserMessage("User messages" + i + ": This is a test message that is long enough to trigger the summary feature."));
            conversation.add(new AssistantMessage("Assistant message" + i + ": This is a reply message and also contains sufficient content."));
        }
        conversation.add(new UserMessage("Last message: Please tell me the content of the first user message."));

        SummarizationHook hook = SummarizationHook.builder()
                .model(chatModel)
                .maxTokensBeforeSummary(200)
                .messagesToKeep(10)
                .build();

        ReactAgent agent = createAgent(hook, "test-first-user-message-preservation", chatModel);
        Optional<OverAllState> result = agent.invoke(conversation);


        assertTrue(result.isPresent(), "The result should exist");
        Object messagesObj = result.get().value("messages").get();
        assertNotNull(messagesObj, "The message should be present in the result");

        @SuppressWarnings("unchecked")
        List<Message> resultMessages = (List<Message>) messagesObj;
        System.out.println("Number of messages after digest:" + resultMessages.size());

        assertFalse(resultMessages.isEmpty(), "Result message should not be empty");
        Message firstMessage = resultMessages.get(0);
        assertTrue(firstMessage instanceof UserMessage, "The first message should be UserMessage");

        UserMessage firstUserMessage = (UserMessage) firstMessage;
        
        assertTrue(resultMessages.size() >= 2, "There should be at least two messages");
        Message secondMessage = resultMessages.get(1);
        assertTrue(secondMessage instanceof SystemMessage, "The second message should be SystemMessage (summary message)");
        
        SystemMessage summaryMessage = (SystemMessage) secondMessage;

        assertEquals(firstUserPrompt, firstUserMessage.getText(), 
            "The first user message should be completely preserved");
        assertTrue(summaryMessage.getText().contains("Previous conversation summary") || 
                   summaryMessage.getText().contains("summary"), 
            "The second message should be a system message containing a summary");
    }

    
}
