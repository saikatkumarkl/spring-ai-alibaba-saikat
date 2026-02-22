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
package com.alibaba.cloud.ai.graph.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.serializer.StateSerializer;
import com.alibaba.cloud.ai.graph.serializer.plain_text.jackson.SpringAIJacksonStateSerializer;
import com.alibaba.cloud.ai.graph.serializer.std.SpringAIStateSerializer;
import com.alibaba.cloud.ai.graph.streaming.OutputType;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.ListOutputConverter;
import org.springframework.ai.converter.MapOutputConverter;

import org.springframework.ai.support.ToolCallbacks;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.convert.support.DefaultConversionService;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class ReactAgentTest {

	private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testReactAgent() throws Exception {
		ReactAgent agent = ReactAgent.builder().name("single_agent").model(chatModel).saver(new MemorySaver()).build();

		try {
			Optional<OverAllState> result = agent.invoke("Help me write an essay of about 100 words.");
			Optional<OverAllState> result2 = agent.invoke(new UserMessage("Write me a modern poem."));
			Optional<OverAllState> result3 = agent.invoke("Help me write a modern poem2.");

			assertTrue(result.isPresent(), "First result should be present");
			OverAllState state1 = result.get();
			assertTrue(state1.value("messages").isPresent(), "Messages should be present in first result");
			assertEquals(2, ((List) state1.value("messages")
					.get()).size(), "There should be 2 messages in the first result");
			Object messages1 = state1.value("messages").get();
			assertNotNull(messages1, "Messages should not be null in first result");

			assertTrue(result2.isPresent(), "Second result should be present");
			OverAllState state2 = result2.get();
			assertTrue(state2.value("messages").isPresent(), "Messages should be present in second result");
			assertEquals(4, ((List<?>) state2.value("messages")
					.get()).size(), "There should be 2 messages in the first result");
			Object messages2 = state2.value("messages").get();
			assertNotNull(messages2, "Messages should not be null in second result");

			assertNotEquals(messages1, messages2, "Results should be different for different inputs");

			System.out.println(result.get());

		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("ReactAgent execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testReactAgentMessage() throws Exception {

		ReactAgent agent = ReactAgent.builder().name("single_agent").model(chatModel).saver(new MemorySaver())
				.build();
		AssistantMessage message = agent.call("Help me write an essay of about 100 words.");
		System.out.println(message.getText());
	}

	@Test
	public void testReactAgentWithOutputSchema() throws Exception {

		// Customized outputSchema
		String customSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
					"type": "object",
					"properties": {
						"title": {
							"type": "string"
						},
						"content": {
							"type": "string"
						},
						"style": {
							"type": "string"
						}
					},
					"additionalProperties": false
				}
				""";

		ReactAgent agent = ReactAgent.builder()
				.name("schema_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.outputSchema(customSchema)
				.build();

		AssistantMessage message = agent.call("Help me write a poem about spring.");
		assertNotNull(message, "Message should not be null");
		assertNotNull(message.getText(), "Message text should not be null");
		System.out.println("=== Output with custom schema ===");
		System.out.println(message.getText());

		assertTrue(message.getText().contains("title") || message.getText().contains("title"),
				"Output should contain title field");
	}

	@Test
	public void testReactAgentWithOutputType() throws Exception {

		// outputType will be automatically convert to schema
		ReactAgent agent = ReactAgent.builder()
				.name("type_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.outputType(PoemOutput.class)
				.build();

		AssistantMessage message = agent.call("Help me write a modern poem about autumn.");
		assertNotNull(message, "Message should not be null");
		assertNotNull(message.getText(), "Message text should not be null");
		System.out.println("=== Output with outputType (auto-generated schema) ===");
		System.out.println(message.getText());

		assertTrue(message.getText().contains("title") || message.getText().contains("content") ||
						message.getText().contains("style"),
				"Output should contain structured fields");
	}

	@Test
	public void testReactAgentWithOutputSchemaAndInvoke() throws Exception {

		String jsonSchema = """
				{
					"$schema": "https://json-schema.org/draft/2020-12/schema",
					"type": "object",
					"properties": {
						"summary": {
							"type": "string"
						},
						"keywords": {
							"type": "array",
							"items": {
								"type": "string"
							}
						},
						"sentiment": {
							"type": "string"
						}
					},
					"additionalProperties": false
				}
				""";

		ReactAgent agent = ReactAgent.builder()
				.name("analysis_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.outputSchema(jsonSchema)
				.enableLogging(true)
				.build();

		Optional<OverAllState> result = agent.invoke("Analyze this sentence: Spring is here, and everything is revived and full of vitality.");

		assertTrue(result.isPresent(), "Result should be present");
		System.out.println("=== Full state output ===");
		System.out.println(result.get());
	}

	@Test
	public void testAgentNameAndTokenUsage() throws Exception {
		ReactAgent agent = ReactAgent.builder()
				.name("test_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.enableLogging(true)
				.build();

		Optional<NodeOutput> nodeOutputOptional = agent.invokeAndGetOutput("Help me write an essay of about 100 words.");

		assertTrue(nodeOutputOptional.isPresent(), "Result should be present");

		NodeOutput nodeOutput = nodeOutputOptional.get();
		assertNotNull(nodeOutput, "NodeOutput should not be null");
		assertNotNull(nodeOutput.tokenUsage(), "TokenUsage should not be null");
		assertNotNull(nodeOutput.agent(), "Agent should not be null");
		assertEquals("test_agent", nodeOutput.agent(), "Agent name should match");

		System.out.println("=== NodeOutput ===");
		System.out.println("Agent: " + nodeOutput.agent());
		System.out.println("TokenUsage: " + nodeOutput.tokenUsage());
	}

	/**
	 * Print diagram of ReactAgent
	 *
	 * Use the getAndCompileGraph method to get and print ReactAgent's internal state graph
	 */
	private void printReactAgentGraph(ReactAgent agent) {
		GraphRepresentation representation = agent.getAndCompileGraph().stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);
		System.out.println(representation.content());
	}

	@Test
	public void testAgentNameAndTokenUsage2() throws Exception {
		ReactAgent agent = ReactAgent.builder()
				.name("test_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.enableLogging(true)
				.build();

		Flux<NodeOutput> flux = agent.stream(new UserMessage("Help me write an essay of about 100 words."));

		flux.doOnNext(output -> {
			if (output instanceof StreamingOutput<?> streamingOutput) {
				assertNotNull(streamingOutput, "NodeOutput should not be null");
				assertNotNull(streamingOutput.tokenUsage(), "TokenUsage should not be null");
				assertNotNull(streamingOutput.agent(), "Agent should not be null");
				assertEquals("test_agent", streamingOutput.agent(), "Agent name should match");

				System.out.println("=== NodeOutput ===");
				System.out.println("Agent: " + streamingOutput.agent());
				System.out.println("TokenUsage: " + streamingOutput.tokenUsage());
			}
		}).blockLast();
	}

	@Test
	public void testAgentSystemPrompt() throws Exception {
		ReactAgent agent = ReactAgent.builder()
				.name("test_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.systemPrompt("You are a poetry writing assistant and you can help me write a modern poem about spring.")
				.enableLogging(true)
				.build();

		AssistantMessage assistantMessage = agent.call("Help me write a modern poem about spring.");
		System.out.println(assistantMessage.getText());
	}

	/**
	 * Test that ReactAgent can be configured with SpringAIJacksonStateSerializer.
	 */
	@Test
	public void testReactAgentWithJacksonSerializer() throws Exception {
		StateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);

		ReactAgent agent = ReactAgent.builder()
				.name("jackson_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.stateSerializer(serializer)
				.build();

		// Verify serializer is set correctly in StateGraph
		StateGraph stateGraph = agent.getStateGraph();
		assertNotNull(stateGraph, "StateGraph should not be null");
		StateSerializer graphSerializer = stateGraph.getStateSerializer();
		assertNotNull(graphSerializer, "Serializer should not be null");
		assertInstanceOf(SpringAIJacksonStateSerializer.class, graphSerializer,
				"Serializer should be SpringAIJacksonStateSerializer");

		// Test that agent works correctly with the serializer
		Optional<OverAllState> result = agent.invoke("Help me write an essay of about 100 words.");
		assertTrue(result.isPresent(), "Result should be present");
		assertTrue(result.get().value("messages").isPresent(), "Messages should be present");
	}

	/**
	 * Test that ReactAgent can be configured with SpringAIStateSerializer.
	 */
	@Test
	public void testReactAgentWithSpringAIStateSerializer() throws Exception {
		StateSerializer serializer = new SpringAIStateSerializer();

		ReactAgent agent = ReactAgent.builder()
				.name("binary_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.stateSerializer(serializer)
				.build();

		// Verify serializer is set correctly in StateGraph
		StateGraph stateGraph = agent.getStateGraph();
		assertNotNull(stateGraph, "StateGraph should not be null");
		StateSerializer graphSerializer = stateGraph.getStateSerializer();
		assertNotNull(graphSerializer, "Serializer should not be null");
		assertInstanceOf(SpringAIStateSerializer.class, graphSerializer,
				"Serializer should be SpringAIStateSerializer");

		// Test that agent works correctly with the serializer
		Optional<OverAllState> result = agent.invoke("Help me write an essay of about 100 words.");
		assertTrue(result.isPresent(), "Result should be present");
		assertTrue(result.get().value("messages").isPresent(), "Messages should be present");
	}

	/**
	 * Test that ReactAgent uses default serializer (SpringAIJacksonStateSerializer) when not specified.
	 */
	@Test
	public void testReactAgentWithDefaultSerializer() throws Exception {
		ReactAgent agent = ReactAgent.builder()
				.name("default_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.build();

		// Verify default serializer is set (should be SpringAIJacksonStateSerializer)
		StateGraph stateGraph = agent.getStateGraph();
		assertNotNull(stateGraph, "StateGraph should not be null");
		StateSerializer graphSerializer = stateGraph.getStateSerializer();
		assertNotNull(graphSerializer, "Serializer should not be null");
		assertInstanceOf(SpringAIJacksonStateSerializer.class, graphSerializer,
				"Default serializer should be SpringAIJacksonStateSerializer");

		// Test that agent works correctly with default serializer
		Optional<OverAllState> result = agent.invoke("Help me write an essay of about 100 words.");
		assertTrue(result.isPresent(), "Result should be present");
		assertTrue(result.get().value("messages").isPresent(), "Messages should be present");
	}

	/**
	 * Test that serializer is used correctly during agent execution and state serialization.
	 */
	@Test
	public void testReactAgentSerializerUsedInExecution() throws Exception {
		StateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);

		ReactAgent agent = ReactAgent.builder()
				.name("execution_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.stateSerializer(serializer)
				.build();

		// Execute multiple invocations to test serialization/deserialization
		Optional<OverAllState> result1 = agent.invoke("Help me write an essay of about 100 words.");
		assertTrue(result1.isPresent(), "First result should be present");

		Optional<OverAllState> result2 = agent.invoke(new UserMessage("Write me a modern poem."));
		assertTrue(result2.isPresent(), "Second result should be present");

		// Verify messages are correctly serialized/deserialized
		assertTrue(result1.get().value("messages").isPresent(), "Messages should be present in first result");
		assertTrue(result2.get().value("messages").isPresent(), "Messages should be present in second result");

		// Verify serializer is still correctly set
		StateGraph stateGraph = agent.getStateGraph();
		StateSerializer graphSerializer = stateGraph.getStateSerializer();
		assertInstanceOf(SpringAIJacksonStateSerializer.class, graphSerializer);
	}

	/**
	 * Test that serializer works correctly with agent streaming.
	 */
	@Test
	public void testReactAgentSerializerWithStreaming() throws Exception {
		StateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);

		ReactAgent agent = ReactAgent.builder()
				.name("streaming_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.stateSerializer(serializer)
				.enableLogging(true)
				.chatOptions(DashScopeChatOptions.builder().enableThinking(true).build())
				.build();

		// Test streaming
		Flux<NodeOutput> flux = agent.stream(new UserMessage("Help me write an essay of about 100 words."));

		flux.doOnNext(output -> {
			assertNotNull(output, "NodeOutput should not be null");
			if (output instanceof StreamingOutput<?> streamingOutput) {
				assertNotNull(streamingOutput.agent(), "Agent name should not be null");
				assertEquals("streaming_agent", streamingOutput.agent(), "Agent name should match");
			}
		}).blockLast();

		// Verify serializer is still correctly set
		StateGraph stateGraph = agent.getStateGraph();
		StateSerializer graphSerializer = stateGraph.getStateSerializer();
		assertInstanceOf(SpringAIJacksonStateSerializer.class, graphSerializer);
	}

	/**
	 * Test that serializer works correctly with output schema.
	 */
	@Test
	public void testReactAgentSerializerWithOutputSchema() throws Exception {
		StateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);

		String customSchema = """
				Please output in the following JSON format:
				{
					"title": "poem title",
					"content": "Poetry text content",
					"style": "Poetry style (such as modern poetry, ancient poetry, etc.)"
				}
				""";

		ReactAgent agent = ReactAgent.builder()
				.name("schema_serializer_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.stateSerializer(serializer)
				.outputSchema(customSchema)
				.build();

		// Verify serializer is set
		StateGraph stateGraph = agent.getStateGraph();
		StateSerializer graphSerializer = stateGraph.getStateSerializer();
		assertInstanceOf(SpringAIJacksonStateSerializer.class, graphSerializer);

		// Test execution
		AssistantMessage message = agent.call("Help me write a poem about spring.");
		assertNotNull(message, "Message should not be null");
		assertNotNull(message.getText(), "Message text should not be null");
	}

	/**
	 * Test serializer consistency: same serializer instance should work across multiple agents.
	 */
	@Test
	public void testReactAgentSerializerConsistency() throws Exception {
		StateSerializer serializer = new SpringAIJacksonStateSerializer(OverAllState::new);

		ReactAgent agent1 = ReactAgent.builder()
				.name("agent1")
				.model(chatModel)
				.saver(new MemorySaver())
				.stateSerializer(serializer)
				.build();

		ReactAgent agent2 = ReactAgent.builder()
				.name("agent2")
				.model(chatModel)
				.saver(new MemorySaver())
				.stateSerializer(serializer)
				.build();

		// Both agents should use the same serializer type
		StateSerializer serializer1 = agent1.getStateGraph().getStateSerializer();
		StateSerializer serializer2 = agent2.getStateGraph().getStateSerializer();

		assertNotNull(serializer1);
		assertNotNull(serializer2);
		assertEquals(serializer1.getClass(), serializer2.getClass(),
				"Both agents should use the same serializer type");

		// Both agents should work correctly
		Optional<OverAllState> result1 = agent1.invoke("Help me write an essay of about 100 words.");
		Optional<OverAllState> result2 = agent2.invoke("Help me write an essay of about 100 words.");

		assertTrue(result1.isPresent(), "Agent1 result should be present");
		assertTrue(result2.isPresent(), "Agent2 result should be present");
	}

	@Test
	public void testReactAgentWithBeanOutputConverter() throws Exception {
		// Use BeanOutputConverter to generate outputSchema
		BeanOutputConverter<List<ActorsFilms>> outputConverter = new BeanOutputConverter<>(
				new ParameterizedTypeReference<List<ActorsFilms>>() { });

		String format = outputConverter.getFormat();

		ReactAgent agent = ReactAgent.builder()
				.name("actors_films_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.outputSchema(format)
				.enableLogging(true)
				.build();

		AssistantMessage message = agent.call("List 3 well-known actors and their representative works, and list 2-3 movies for each actor.");
		assertNotNull(message, "Message should not be null");
		assertNotNull(message.getText(), "Message text should not be null");
		System.out.println("=== Output with BeanOutputConverter generated schema ===");
		System.out.println(message.getText());

		assertTrue(message.getText().contains("actor") || message.getText().contains("films"),
				"Output should contain actor or films field");
	}

	@Test
	public void testReactAgentWithMapOutputConverter() throws Exception {
		// Use MapOutputConverter to generate outputSchema
		MapOutputConverter mapOutputConverter = new MapOutputConverter();
		String format = mapOutputConverter.getFormat();

		ReactAgent agent = ReactAgent.builder()
				.name("map_output_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.outputSchema(format)
				.enableLogging(true)
				.build();



		AssistantMessage message = agent.call("Please provide a JSON object containing name, age and occupation.");
		assertNotNull(message, "Message should not be null");
		assertNotNull(message.getText(), "Message text should not be null");
		System.out.println("=== Output with MapOutputConverter generated schema ===");
		System.out.println(message.getText());

		assertTrue(message.getText().length() > 0, "Output should not be empty");
	}

	@Test
	public void testReactAgentWithListOutputConverter() throws Exception {
		// Use ListOutputConverter to generate outputSchema
		ListOutputConverter listOutputConverter = new ListOutputConverter(new DefaultConversionService());
		String format = listOutputConverter.getFormat();

		ReactAgent agent = ReactAgent.builder()
				.name("list_output_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.outputSchema(format)
				.enableLogging(true)
				.build();

		AssistantMessage message = agent.call("Please list 5 of your favorite programming languages.");
		assertNotNull(message, "Message should not be null");
		assertNotNull(message.getText(), "Message text should not be null");
		System.out.println("=== Output with ListOutputConverter generated schema ===");
		System.out.println(message.getText());

        assertFalse(message.getText().isEmpty(), "Output should not be empty");
	}

    @Test
    public void testReactAgentWithTools() throws GraphRunnerException, NoSuchFieldException, IllegalAccessException {

        var react = ReactAgent.builder()
                .name("demoReactAgent")
                .model(chatModel)
                .instruction("The location is: {target_topic}")
                .tools(ToolCallbacks.from(new TestTools()))
                .systemPrompt("You are a weather forecast assistant, help me check the weather forecast for the specified location")
                .build();

        String output = react.call("Shanghai,Beijing").getText();
        System.out.println("ReactAgent Output: " + output);

        assertNotNull(output);
        assertFalse(output.isEmpty(), "Output should not be empty");

        //Verify hasTools to check if tool definitions are included
        assertTrue(testHasTools(react ), "Tools should have been set");
    }

	@Test
	public void testReactAgentStreamingWithTools() throws GraphRunnerException {

		// Define a simple ModelHook that returns custom data
		ModelHook streamingModelHook = new ModelHook() {
			@Override
			public String getName() {
				return "streaming_test_hook";
			}

			@Override
			public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
				return CompletableFuture.completedFuture(Map.of(
					"hook_type", "before_model",
					"custom_data", "streaming_hook_data",
					"timestamp", System.currentTimeMillis()
				));
			}

			@Override
			public HookPosition[] getHookPositions() {
				return new HookPosition[]{HookPosition.BEFORE_MODEL};
			}
		};

		var react = ReactAgent.builder()
				.name("demoReactAgent")
				.model(chatModel)
				.instruction("The location is: {target_topic}")
				.tools(ToolCallbacks.from(new TestTools()))
				.hooks(List.of(streamingModelHook))
				.systemPrompt("You are a weather forecast assistant, help me check the weather forecast for the specified location")
				.outputKey("final_answer")
				.build();

		// Track whether we've seen each expected output type
		AtomicBoolean hasAgentModelStreaming = new AtomicBoolean(false);
		AtomicBoolean hasAgentModelFinished = new AtomicBoolean(false);
		AtomicBoolean hasAgentToolFinished = new AtomicBoolean(false);
		AtomicBoolean hasAgentHookFinished = new AtomicBoolean(false);

		Flux<NodeOutput> flux = react.stream("Shanghai,Beijing");
		NodeOutput finalOutput = flux.doOnNext(output -> {
			// START
			if (output instanceof StreamingOutput<?> streamingOutput) {
				System.out.println("ReactAgent Streaming Output Chunk: " + streamingOutput.getOutputType());
				System.out.println("ReactAgent Streaming Output Chunk: " + streamingOutput.message());

				// Check for expected output types
				if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_STREAMING) {
					hasAgentModelStreaming.set(true);
				}
				if (streamingOutput.getOutputType() == OutputType.AGENT_MODEL_FINISHED) {
					hasAgentModelFinished.set(true);
				}
				if (streamingOutput.getOutputType() == OutputType.AGENT_TOOL_FINISHED) {
					hasAgentToolFinished.set(true);
				}
				if (streamingOutput.getOutputType() == OutputType.AGENT_HOOK_FINISHED) {
					hasAgentHookFinished.set(true);
				}
			}
			// END
		}).blockLast();

		if (finalOutput == null) {
			fail("ReactAgent stream completed without emitting any NodeOutput");
		}
		System.out.println("ReactAgent Final Output: " + finalOutput.state());

		// Verify that all expected output types were received
		assertTrue(hasAgentModelStreaming.get(), "Should have received AGENT_MODEL_STREAMING output");
		assertTrue(hasAgentModelFinished.get(), "Should have received AGENT_MODEL_FINISHED output");
		assertTrue(hasAgentToolFinished.get(), "Should have received AGENT_TOOL_FINISHED output");
		assertTrue(hasAgentHookFinished.get(), "Should have received AGENT_HOOK_FINISHED output");
	}

    @Test
    public void testReactAgentWithMultiple() throws GraphRunnerException, NoSuchFieldException, IllegalAccessException {

        var reactAgent1 = ReactAgent.builder()
                .name("demoReactAgent")
                .model(chatModel)
                .instruction("The location is: {target_topic}")
                .tools(ToolCallbacks.from(new TestTools()))
                .systemPrompt("You are a weather forecast assistant, help me check the weather forecast for the specified location")
                .build();

        var reactAgent2 = ReactAgent.builder()
                .name("demoReactAgent")
                .model(chatModel)
                .hooks(List.of(new TestModelHook(), new TestAgentHook()))
                .instruction("The topic is: {target_topic}")
                .systemPrompt("You are an expert in poetry writing. Please write a poem of about 200 words according to the given theme.")
                .build();

        var reactAgent3 = ReactAgent.builder()
                .name("demoReactAgent")
                .model(chatModel)
                .instruction("The location is: {target_topic}")
                .tools(ToolCallbacks.from(new TestTools()))
                .systemPrompt("You are a weather forecast assistant, help me check the weather forecast for the specified location")
                .build();

        //Ordinary call
        String output1 = reactAgent1.call("Shanghai,Beijing").getText();
        String output2 = reactAgent2.call("spring").getText();
        String output3 = reactAgent3.call("Hangzhou,Beijing").getText();

        System.out.println(output1);
        System.out.println(output2);
        System.out.println(output3);

        assertNotNull(output1);
        assertFalse(output1.isEmpty(), "Output should not be empty");
        assertNotNull(output2);
        assertFalse(output2.isEmpty(), "Output should not be empty");
        assertNotNull(output3);
        assertFalse(output3.isEmpty(), "Output should not be empty");

        //Verification tools include
        assertTrue(testHasTools(reactAgent1), "Tools should have been set");
        assertFalse(testHasTools(reactAgent2), "Tools should not have been set");
        assertTrue(testHasTools(reactAgent3), "Tools should have been set");
    }

    @Test
    public void testReactAgentWithHooks() throws GraphRunnerException {

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(outputStream));

        String agentOutput = ReactAgent.builder()
                .name("demoReactAgent")
                .model(chatModel)
                .hooks(List.of(new TestModelHook(), new TestAgentHook()))
                .instruction("The topic is: {target_topic}")
                .systemPrompt("You are an expert in poetry writing. Please write a poem of about 200 words according to the given theme.")
                .build()
                .call("spring")
                .getText();

        System.setOut(originalOut);

        System.out.println("ReactAgent Output: " + agentOutput);

        assertNotNull(agentOutput);
        assertFalse(agentOutput.isEmpty(), "Output should not be empty");

        //Verify whether the console output contains hooks content
        String consoleOutput = outputStream.toString();
        assertTrue(consoleOutput.contains("Prepare to call the model..."), "Console output should contain 'Prepare to call the model...'");
        assertTrue(consoleOutput.contains("Agent starts executing"), "Console output should contain 'Agent Start execution'");
    }

    static class TestTools {

        @Tool(name = "getWeatherByCity", description = "Get weather information by city  name", returnDirect = false)
        public String getWeatherByCity(@ToolParam(description = "City address list") List<String> cityNameList) {
            StringBuilder builder = new StringBuilder();
            for (String cityName : cityNameList) {
                builder.append(cityName + "nice weather");
            }

            return builder.toString();
        }
    }

    static class TestModelHook extends ModelHook {

        @Override
        public String getName() {
            return "test_model_hook";
        }

        @Override
        public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
            System.out.println("Prepare to call the model...");
            return CompletableFuture.completedFuture(Map.of("extra_context", "some additional information"));
        }
    }

    static class TestAgentHook extends AgentHook {

        @Override
        public String getName() {
            return "test_agent_hook";
        }

        @Override
        public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
            System.out.println("Agent starts executing");
            return CompletableFuture.completedFuture(Map.of("start_time", System.currentTimeMillis()));
        }
    }

	// Inner class for outputType example
	public static class PoemOutput {
		private String title;
		private String content;
		private String style;

		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public String getStyle() {
			return style;
		}

		public void setStyle(String style) {
			this.style = style;
		}

		@Override
		public String toString() {
			return "PoemOutput{" +
					"title='" + title + '\'' +
					", content='" + content + '\'' +
					", style='" + style + '\'' +
					'}';
		}
	}

	// Inner class for BeanOutputConverter example
	public static class ActorsFilms {
		private String actor;
		private List<String> films;

		public String getActor() {
			return actor;
		}

		public void setActor(String actor) {
			this.actor = actor;
		}

		public List<String> getFilms() {
			return films;
		}

		public void setFilms(List<String> films) {
			this.films = films;
		}
	}

    private static Boolean testHasTools(ReactAgent reactAgent) throws NoSuchFieldException, IllegalAccessException {

        Field hasToolsField = reactAgent.getClass().getDeclaredField("hasTools");
        hasToolsField.setAccessible(true);

        return (Boolean) hasToolsField.get(reactAgent);
    }

	/**
	 * Test that ReactAgent can be configured with executor.
	 */
	@Test
	public void testReactAgentWithExecutor() throws Exception {
		Executor customExecutor = Executors.newFixedThreadPool(4);
		try {
			ReactAgent agent = ReactAgent.builder()
					.name("executor_agent")
					.model(chatModel)
					.saver(new MemorySaver())
					.executor(customExecutor)
					.build();

			assertNotNull(agent, "Agent should not be null");

			// Verify executor is set and passed to RunnableConfig using reflection
			RunnableConfig config = buildNonStreamConfig(agent, null);
			assertNotNull(config, "RunnableConfig should not be null");
			
			assertTrue(config.metadata(RunnableConfig.DEFAULT_PARALLEL_EXECUTOR_KEY).isPresent(),
				"Default parallel executor should be present in metadata");
			assertEquals(customExecutor, 
				config.metadata(RunnableConfig.DEFAULT_PARALLEL_EXECUTOR_KEY).get(),
				"Executor in metadata should match configured executor");
		} finally {
			((java.util.concurrent.ExecutorService) customExecutor).shutdown();
		}
	}

	/**
	 * Test that ReactAgent without executor doesn't have executor in metadata.
	 */
	@Test
	public void testReactAgentWithoutExecutor() throws Exception {
		ReactAgent agent = ReactAgent.builder()
				.name("no_executor_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.build();

		assertNotNull(agent, "Agent should not be null");

		// Verify no executor in metadata when not configured
		RunnableConfig config = buildNonStreamConfig(agent, null);
		assertNotNull(config, "RunnableConfig should not be null");
		
		assertFalse(config.metadata(RunnableConfig.DEFAULT_PARALLEL_EXECUTOR_KEY).isPresent(),
			"Default parallel executor should not be present when not configured");
	}

	/**
	 * Helper method to call protected buildNonStreamConfig using reflection.
	 */
	private RunnableConfig buildNonStreamConfig(Agent agent, RunnableConfig config) throws Exception {
		Method method = Agent.class.getDeclaredMethod("buildNonStreamConfig", RunnableConfig.class);
		method.setAccessible(true);
		return (RunnableConfig) method.invoke(agent, config);
	}

	@Test
	public void testInstructionSentToLLM() throws Exception {
		String systemPromptText = "You are a professional technical Q&A assistant who is good at explaining complex technical concepts in concise and clear language.";
		String instructionText = "Please answer the user's question concisely in no more than 100 words, focusing on the core points.";

		ReactAgent agent = ReactAgent.builder()
				.name("instruction_test_agent")
				.model(chatModel)
				.systemPrompt(systemPromptText)
				.instruction(instructionText)
				.saver(new MemorySaver())
				.enableLogging(true)
				.build();

		assertNotNull(agent, "Agent should not be empty");

		AssistantMessage response = agent.call("What is a RESTful API?");
		assertNotNull(response, "Response should not be empty");
		assertNotNull(response.getText(), "Response text should not be empty");
		assertFalse(response.getText().isEmpty(), "Response text should not be an empty string");
		assertTrue(response.getText().length() > 0, "The response should have content");
	}


	@Test
	public void testDynamicSystemPromptUpdate() throws Exception {
		String initialSystemPrompt = "You are a professional technical assistant, so your answers should be concise and clear.";
		String updatedSystemPrompt = "You are an expert at writing poetry and answering questions with beautiful language.";
		String finalSystemPrompt = "You are a math expert and answer questions with precise numbers.";

		ReactAgent agent = ReactAgent.builder()
				.name("dynamic_system_prompt_agent")
				.model(chatModel)
				.systemPrompt(initialSystemPrompt)
				.saver(new MemorySaver())
				.enableLogging(true)
				.build();

		assertNotNull(agent, "Agent should not be empty");

		AssistantMessage response1 = agent.call("What is Java?");
		assertNotNull(response1, "The first response should not be empty");
		assertFalse(response1.getText().isEmpty(), "The first response should not be an empty string");
		System.out.println(response1.getText());

		agent.setSystemPrompt(updatedSystemPrompt);

		AssistantMessage response2 = agent.call("What is Spring?");
		assertNotNull(response2, "The second response should not be empty");
		assertFalse(response2.getText().isEmpty(), "The second response should not be an empty string");
		System.out.println(response2.getText());

		agent.setSystemPrompt(finalSystemPrompt);

		AssistantMessage response3 = agent.call("What is 1+1 equal to?");
		assertNotNull(response3, "The third response should not be empty");
		assertFalse(response3.getText().isEmpty(), "The third response should not be an empty string");
		System.out.println(response3.getText());

		assertTrue(response1.getText().length() > 0, "The first response should have content");
		assertTrue(response2.getText().length() > 0, "The second response should have content");
		assertTrue(response3.getText().length() > 0, "The third response should have content");
	}


	@Test
	void testChatOptionsImmutability() {
		ToolCallback tool1 = ToolCallbacks.from(new TestTools())[0];

		DashScopeChatOptions originalOptions = DashScopeChatOptions.builder()
			.model("qwen-plus")
			.temperature(0.7)
			.toolCallbacks(List.of(tool1))
			.build();

		int originalToolCount = originalOptions.getToolCallbacks().size();
		String originalModel = originalOptions.getModel();
		Double originalTemperature = originalOptions.getTemperature();
		String originalToolName = originalOptions.getToolCallbacks().get(0).getToolDefinition().name();

		ToolCallback tool2 = ToolCallbacks.from(new TestTools())[0];
		ReactAgent agent = ReactAgent.builder()
			.name("test-agent")
			.model(chatModel)
			.chatOptions(originalOptions)
			.tools(tool2)
			.saver(new MemorySaver())
			.build();
		assertEquals(originalToolCount, originalOptions.getToolCallbacks().size(),
			"The number of toolCallbacks of the original chatOptions should not change");
		assertEquals(originalModel, originalOptions.getModel(),
			"The model of the original chatOptions should not change");
		assertEquals(originalTemperature, originalOptions.getTemperature(),
			"The temperature of the original chatOptions should not change");

		List<ToolCallback> originalToolCallbacks = originalOptions.getToolCallbacks();
		assertEquals(1, originalToolCallbacks.size(), "The original toolCallbacks should only be 1");
		assertEquals(originalToolName, originalToolCallbacks.get(0).getToolDefinition().name(),
			"The tool name of the original toolCallbacks should not be changed");
	}

	@Test
	void testSharedChatOptionsAcrossAgents() {
		DashScopeChatOptions sharedOptions = DashScopeChatOptions.builder()
			.model("qwen-plus")
			.temperature(0.7)
			.build();

		ToolCallback tool1 = ToolCallbacks.from(new TestTools())[0];
		ToolCallback tool2 = ToolCallbacks.from(new TestTools())[0];

		ReactAgent agent1 = ReactAgent.builder()
			.name("agent1")
			.model(chatModel)
			.chatOptions(sharedOptions)
			.tools(tool1)
			.saver(new MemorySaver())
			.build();

		ReactAgent agent2 = ReactAgent.builder()
			.name("agent2")
			.model(chatModel)
			.chatOptions(sharedOptions)
			.tools(tool2)
			.saver(new MemorySaver())
			.build();

		List<ToolCallback> sharedToolCallbacks = sharedOptions.getToolCallbacks();
		assertTrue(sharedToolCallbacks == null || sharedToolCallbacks.isEmpty(),
			"Shared chatOptions should not be set toolCallbacks");

		assertNotNull(agent1);
		assertNotNull(agent2);
	}

}
