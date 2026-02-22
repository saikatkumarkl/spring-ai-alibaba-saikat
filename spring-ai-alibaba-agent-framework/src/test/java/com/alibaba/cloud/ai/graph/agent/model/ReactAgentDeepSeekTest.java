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
package com.alibaba.cloud.ai.graph.agent.model;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AI_DEEPSEEK_API_KEY", matches = ".+")
class ReactAgentDeepSeekTest {

	private ChatModel chatModel;

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

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DeepSeekApi deepSeekApi = DeepSeekApi.builder()
			.apiKey(System.getenv("AI_DEEPSEEK_API_KEY"))
//			.baseUrl(System.getenv("AI_DEEPSEEK_API_BASE_URL"))
			.build();

		DeepSeekChatModel deepSeekChatModel = DeepSeekChatModel.builder()
				.defaultOptions(DeepSeekChatOptions.builder()
						.model("deepseek-chat")
						.build())
				.deepSeekApi(deepSeekApi).build();

		// Create DashScope ChatModel instance
		this.chatModel = deepSeekChatModel;
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
			assertEquals(2, ((List)state1.value("messages").get()).size(), "There should be 2 messages in the first result");
			Object messages1 = state1.value("messages").get();
			assertNotNull(messages1, "Messages should not be null in first result");

			assertTrue(result2.isPresent(), "Second result should be present");
			OverAllState state2 = result2.get();
			assertTrue(state2.value("messages").isPresent(), "Messages should be present in second result");
			assertEquals(4, ((List<?>)state2.value("messages").get()).size(), "There should be 2 messages in the first result");
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
				.build();

		Optional<OverAllState> result = agent.invoke("Analyze this sentence: Spring is here, and everything is revived and full of vitality.");

		assertTrue(result.isPresent(), "Result should be present");
		System.out.println("=== Full state output ===");
		System.out.println(result.get());
	}

	@Test
	public void testAgentToolBasic() throws Exception {


		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("You can write articles.")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.saver(new MemorySaver())
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Articles can be commented and modified.")
				.instruction("You are a well-known critic who is good at commenting and revising articles.For prose articles, please ensure that the article must include a description of the scenery of West Lake.")
				.saver(new MemorySaver())
				.build();

		ReactAgent blogAgent = ReactAgent.builder()
				.name("blog_agent")
				.model(chatModel)
				.instruction("First, write an article based on a topic given by the user, and then submit the article to reviewers for review and modifications if necessary.")
				.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent),
						AgentTool.getFunctionToolCallback(reviewerAgent)))
				.saver(new MemorySaver())
				.build();

		try {
			Optional<OverAllState> result = blogAgent
					.invoke(new UserMessage("Help me write a prose of about 100 words"));

			assertTrue(result.isPresent(), "Result should be present");

			OverAllState state = result.get();

			assertTrue(state.value("messages").isPresent(), "Messages should be present in state");

			Object messages = state.value("messages").get();
			assertNotNull(messages, "Messages should not be null");

			System.out.println("=== Basic Agent Tool Test ===");
			System.out.println(result.get());
		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("Agent tool execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testAgentToolWithInputSchema() throws Exception {


		//Use inputSchema to define the input format of the tool
		String writerInputSchema = """
				{
					"type": "object",
					"properties": {
						"topic": {
							"type": "string"
						},
						"wordCount": {
							"type": "integer"
						},
						"style": {
							"type": "string"
						}
					},
					"required": ["topic", "wordCount", "style"]
				}
				""";

		MemorySaver saver = new MemorySaver();
		ReactAgent writerAgent = ReactAgent.builder()
				.name("structured_writer_agent")
				.model(chatModel)
				.description("Write articles based on structured input")
				.instruction("You are a professional writer.Please create articles strictly in accordance with the entered topic, word count and style requirements.")
				.inputSchema(writerInputSchema)
				.saver(saver)
				.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
				.name("coordinator_agent")
				.model(chatModel)
				.instruction("You need to call the writing tool to complete the user's writing request.Please use structured parameters to call the writing tool according to user needs.")
				.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent)))
				.saver(saver)
				.build();

		try {
			Optional<OverAllState> result = coordinatorAgent
					.invoke("Please write a lyrical prose about spring, about 150 words, write directly and don’t ask me again");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("=== Agent Tool with InputSchema Test ===");
			System.out.println(result.get());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("Agent tool with inputSchema execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testAgentToolWithOutputSchema() throws Exception {


		//Use outputSchema to define the output format of the tool
		String writerOutputSchema = """
				{
					"type": "object",
					"properties": {
						"title": {
							"type": "string"
						},
						"content": {
							"type": "string"
						},
						"characterCount": {
							"type": "integer"
						}
					}
				}
				""";

		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_with_output_schema")
				.model(chatModel)
				.description("Write articles and return structured output")
				.instruction("You are a professional writer.Please create an article and return the results strictly in the specified JSON format.")
				.outputSchema(writerOutputSchema)
				.saver(new MemorySaver())
				.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
				.name("coordinator_output_schema")
				.model(chatModel)
				.instruction("Call the writing tool to complete the user request, and the tool will return structured article data.")
				.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent)))
				.saver(new MemorySaver())
				.build();

		try {
			Optional<OverAllState> result = coordinatorAgent
					.invoke("Write a short essay about winter");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("=== Agent Tool with OutputSchema Test ===");
			System.out.println(result.get());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("Agent tool with outputSchema execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testAgentToolWithOutputType() throws Exception {


		//Using outputType, the framework automatically generates the output schema
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_with_output_type")
				.model(chatModel)
				.description("Write an article and return typed output")
				.instruction("You are a professional writer.Please create an article and return structured results containing title, content, and style.")
				.outputType(PoemOutput.class)
				.saver(new MemorySaver())
				.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
				.name("coordinator_output_type")
				.model(chatModel)
				.instruction("Call the writing tool to complete the user request.")
				.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent)))
				.saver(new MemorySaver())
				.build();

		try {
			Optional<OverAllState> result = coordinatorAgent
					.invoke("Write a short poem about summer");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("=== Agent Tool with OutputType Test ===");
			System.out.println(result.get());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("Agent tool with outputType execution failed: " + e.getMessage());
		}
	}

	private static CompileConfig getCompileConfig() {
		SaverConfig saverConfig = SaverConfig.builder()
				.register(new MemorySaver())
				.build();
		CompileConfig compileConfig = CompileConfig.builder().saverConfig(saverConfig).build();
		return compileConfig;
	}

}
