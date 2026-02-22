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

package com.alibaba.cloud.ai.graph.agent.flow;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.Agent;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LoopAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.loop.LoopMode;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import org.springframework.ai.chat.model.ChatModel;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
public class LoopAgentTest {

	private static final Logger logger = LoggerFactory.getLogger(LoopAgentTest.class);

	private ChatModel chatModel;

	private SequentialAgent blogAgent;

    private SequentialAgent sqlAgent;

	@BeforeEach
	void setUp() throws GraphStateException {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();

        ReactAgent writerAgent = ReactAgent.builder()
                .name("writer_agent")
                .model(chatModel)
                .description("You can write articles.")
                .instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
                .outputKey("article")
                .build();

        ReactAgent reviewerAgent = ReactAgent.builder()
                .name("reviewer_agent")
                .model(chatModel)
                .description("Articles can be commented and modified.")
                .instruction("You are a well-known critic who is good at commenting and revising articles.For prose articles, please ensure that the article must include a description of the scenery of West Lake.Finally, only the revised article will be returned without any comment information.")
                .outputKey("reviewed_article")
                .build();

        this.blogAgent = SequentialAgent.builder()
                .name("blog_agent")
                .description("You can write an article based on a topic given by the user and then submit the article to reviewers for comment.")
                .subAgents(List.of(writerAgent, reviewerAgent))
                .build();

        ReactAgent sqlGenerateAgent = ReactAgent.builder()
                .name("sqlGenerateAgent")
                .model(chatModel)
                .description("MySQL SQL code can be generated based on the user's natural language.")
                .instruction("You are a little assistant who is familiar with MySQL database. Please output the corresponding SQL according to the user's natural language.")
                .outputSchema("""
                        {
                           "query": "user's request",
                           "output": "generated SQL result"
                        }
                        """)
                .outputKey("sql")
                .build();

        ReactAgent sqlRatingAgent = ReactAgent.builder()
                .name("sqlRatingAgent")
                .model(chatModel)
                .description("Scoring can be based on the matching degree of the input natural language and SQL statements.")
                .instruction("You are a little assistant who is familiar with MySQL database. Please output a rating based on the natural language input by the user and the corresponding SQL statement.The rating is a floating point number between 0 and 1.The closer it is to 1, the better SQL matches natural language.")
                .outputSchema("Your output has and only one floating point number between 0 and 1. **Do not output any extra characters**")
                .outputKey("score")
                .build();

        this.sqlAgent = SequentialAgent.builder()
                .name("sql_agent")
                .description("SQL statements can be generated and scored based on user input.")
                .subAgents(List.of(sqlGenerateAgent, sqlRatingAgent))
                .build();
	}

    @Test
    void testCountMode() throws Exception {
        LoopAgent loopAgent = LoopAgent.builder()
                .name("loop_agent")
                .description("Loop through a task until a condition is met.")
                .subAgent(this.blogAgent)
                .loopStrategy(LoopMode.count(2))
                .build();
        OverAllState state = loopAgent.invoke("Help me write a Python Socket programming demo and optimize the code").orElseThrow();
        logger.info("Result: {}", state.data());
        Optional<Object> optional = state.value("messages");
        assert optional.isPresent();
        Object object = optional.get();
        assert object instanceof List;
        List<?> messages = (List<?>) object;
        assert !messages.isEmpty();
    }

    @Test
    void testConditionMode() throws Exception {
        LoopAgent loopAgent = LoopAgent.builder()
                .name("loop_agent")
                .description("Loop through a task until a condition is met.")
                .subAgent(this.sqlAgent)
                .loopStrategy(LoopMode.condition(messages -> {
                    logger.info("Messages: {}", messages);
                    if(messages.isEmpty()) {
                        return false;
                    }
                    String text = messages.get(messages.size() - 1).getText();
                    try {
                        double score = Double.parseDouble(text);
                        return score > 0.5;
                    } catch (Exception e) {
                        return false;
                    }
                }))
                .build();
        OverAllState state = loopAgent.invoke("Now there is a user table named user with columns (id, name, password). Now I want to find all users whose names start with s. How to write the corresponding SQL?").orElseThrow();
        logger.info("Result: {}", state.data());
        Optional<Object> optional = state.value("messages");
        assert optional.isPresent();
        Object object = optional.get();
        assert object instanceof List;
        List<?> messages = (List<?>) object;
        assert !messages.isEmpty();
    }

    @Test
    void testArrayMode() throws Exception {
        LoopAgent loopAgent = LoopAgent.builder()
                .name("loop_agent")
                .description("Execute tasks in a loop.")
                .subAgent(this.sqlAgent)
                .loopStrategy(LoopMode.array())
                .build();
        OverAllState state = loopAgent.invoke("""
                ["Now there is a user table named user with columns (id, name, password). Now I want to find all users whose names start with s. How to write the corresponding SQL?",
                "Now there is a user table named user with columns (id, name, password). Now I want to find all users whose names start with t. How to write the corresponding SQL?",
                "Now there is a user table named user. Now I want to find all users. How to write the corresponding SQL?"]
                """).orElseThrow();
        logger.info("Result: {}", state.data());
        Optional<Object> optional = state.value("messages");
        assert optional.isPresent();
        Object object = optional.get();
        assert object instanceof List;
        List<?> messages = (List<?>) object;
        assert !messages.isEmpty();
    }

    @Test
    void testLoopAgentWithExecutor() throws Exception {
        ExecutorService customExecutor = Executors.newFixedThreadPool(4);
        try {
            LoopAgent loopAgent = LoopAgent.builder()
                    .name("loop_agent_with_executor")
                    .description("Loop agent with executor")
                    .subAgent(this.blogAgent)
                    .loopStrategy(LoopMode.count(2))
                    .executor(customExecutor)
                    .build();

            assertNotNull(loopAgent, "LoopAgent should not be null");

            // Verify executor is set and passed to RunnableConfig
            RunnableConfig config = buildNonStreamConfig(loopAgent, null);
            assertNotNull(config, "RunnableConfig should not be null");
            
            assertTrue(config.metadata(RunnableConfig.DEFAULT_PARALLEL_EXECUTOR_KEY).isPresent(),
                "Default parallel executor should be present in metadata");
            assertEquals(customExecutor, 
                config.metadata(RunnableConfig.DEFAULT_PARALLEL_EXECUTOR_KEY).get(),
                "Executor in metadata should match configured executor");
        } finally {
            customExecutor.shutdown();
        }
    }

    @Test
    void testLoopAgentExecutorWithExistingConfig() throws Exception {
        Executor customExecutor = Executors.newFixedThreadPool(4);

        LoopAgent loopAgent = LoopAgent.builder()
                .name("loop_agent_executor_config")
                .description("Loop agent with executor and existing config")
                .subAgent(this.sqlAgent)
                .loopStrategy(LoopMode.count(1))
                .executor(customExecutor)
                .build();

        // Create an existing RunnableConfig
        RunnableConfig existingConfig = RunnableConfig.builder()
                .threadId("test-thread")
                .build();

        // Build config with existing config
        RunnableConfig newConfig = buildNonStreamConfig(loopAgent, existingConfig);
        
        // Verify existing config properties are preserved
        assertTrue(newConfig.threadId().isPresent());
        assertEquals("test-thread", newConfig.threadId().get());
        
        // Verify executor is added
        assertTrue(newConfig.metadata(RunnableConfig.DEFAULT_PARALLEL_EXECUTOR_KEY).isPresent());
        assertEquals(customExecutor, 
            newConfig.metadata(RunnableConfig.DEFAULT_PARALLEL_EXECUTOR_KEY).get());
    }

    /**
     * Helper method to call protected buildNonStreamConfig using reflection.
     */
    private RunnableConfig buildNonStreamConfig(Agent agent, RunnableConfig config) throws Exception {
        Method method = Agent.class.getDeclaredMethod("buildNonStreamConfig", RunnableConfig.class);
        method.setAccessible(true);
        return (RunnableConfig) method.invoke(agent, config);
    }

}
