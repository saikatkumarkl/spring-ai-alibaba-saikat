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
import com.alibaba.cloud.ai.graph.OverAllState;

import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class AgentToolTest {

	private ChatModel chatModel;

	// Input type for testing inputType parameter
	public static class ArticleRequest {
		private String topic;
		private int wordCount;
		private String style;

		public String getTopic() {
			return topic;
		}

		public void setTopic(String topic) {
			this.topic = topic;
		}

		public int getWordCount() {
			return wordCount;
		}

		public void setWordCount(int wordCount) {
			this.wordCount = wordCount;
		}

		public String getStyle() {
			return style;
		}

		public void setStyle(String style) {
			this.style = style;
		}
	}

	// Output type for testing outputType parameter
	public static class ArticleOutput {
		private String title;
		private String content;
		private int characterCount;

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

		public int getCharacterCount() {
			return characterCount;
		}

		public void setCharacterCount(int characterCount) {
			this.characterCount = characterCount;
		}
	}

	// Review output type
	public static class ReviewOutput {
		private String comment;
		private boolean approved;
		private List<String> suggestions;

		public String getComment() {
			return comment;
		}

		public void setComment(String comment) {
			this.comment = comment;
		}

		public boolean isApproved() {
			return approved;
		}

		public void setApproved(boolean approved) {
			this.approved = approved;
		}

		public List<String> getSuggestions() {
			return suggestions;
		}

		public void setSuggestions(List<String> suggestions) {
			this.suggestions = suggestions;
		}
	}

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testAgentToolBasic() throws Exception {
		ReactAgent writerAgent = ReactAgent.builder()
			.name("writer_agent")
			.model(chatModel)
			.description("You can write articles.")
			.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
			.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
			.name("reviewer_agent")
			.model(chatModel)
			.description("Articles can be commented and modified.")
			.instruction("You are a well-known critic who is good at commenting and revising articles.For prose articles, please ensure that the article must include a description of the scenery of West Lake.")
			.build();

		ReactAgent blogAgent = ReactAgent.builder()
			.name("blog_agent")
			.model(chatModel)
			.instruction("First, write an article based on a topic given by the user, and then submit the article to reviewers for review and modifications if necessary.")
			.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent),
					AgentTool.getFunctionToolCallback(reviewerAgent)))
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

		ReactAgent writerAgent = ReactAgent.builder()
			.name("structured_writer_agent")
			.model(chatModel)
			.description("Write articles based on structured input")
			.instruction("You are a professional writer.Please create articles strictly in accordance with the entered topic, word count and style requirements.")
			.inputSchema(writerInputSchema)
			.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
			.name("coordinator_agent")
			.model(chatModel)
			.instruction("You need to call the writing tool to complete the user's writing request.Please use structured parameters to call the writing tool according to user needs.")
			.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent)))
			.build();

		try {
			Optional<OverAllState> result = coordinatorAgent
				.invoke("Please write a prose about spring, about 150 words");

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
	public void testAgentToolWithInputType() throws Exception {
		//Using inputType, the framework automatically generates JSON Schema
		ReactAgent writerAgent = ReactAgent.builder()
			.name("typed_writer_agent")
			.model(chatModel)
			.description("Write articles based on typed input")
			.instruction("You are a professional writer.Please strictly follow the entered topic, wordCount and style requirements to create the article.")
			.inputType(ArticleRequest.class)
			.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
			.name("coordinator_with_type_agent")
			.model(chatModel)
			.instruction("You need to call the writing tool to complete the user's writing request.The tool accepts parameters in JSON format.")
			.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent)))
			.build();

		try {
			Optional<OverAllState> result = coordinatorAgent
				.invoke("Please write a modern poem about autumn, about 100 words");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("=== Agent Tool with InputType Test ===");
			System.out.println(result.get());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("Agent tool with inputType execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testAgentToolWithOutputSchema() throws Exception {
		//Use outputSchema to define the output format of the tool
		String writerOutputSchema = """
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
						"characterCount": {
							"type": "integer"
						}
					},
					"additionalProperties": false
				}
				""";

		ReactAgent writerAgent = ReactAgent.builder()
			.name("writer_with_output_schema")
			.model(chatModel)
			.description("Write articles and return structured output")
			.instruction("You are a professional writer.Please create an article and return the results strictly in the specified JSON format.")
			.outputSchema(writerOutputSchema)
			.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
			.name("coordinator_output_schema")
			.model(chatModel)
			.instruction("Call the writing tool to complete the user request, and the tool will return structured article data.")
			.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent)))
			.outputType(ArticleOutput.class)
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
			.instruction("You are a professional writer.Please create an article and return structured results containing title, content, and characterCount.")
			.outputType(ArticleOutput.class)
			.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
			.name("coordinator_output_type")
			.model(chatModel)
			.instruction("Call the writing tool to complete the user request.")
			.tools(List.of(AgentTool.getFunctionToolCallback(writerAgent)))
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

	@Test
	public void testAgentToolWithAllSchemaTypes() throws Exception {
		//Comprehensive test: using both inputType and outputType
		ReactAgent writerAgent = ReactAgent.builder()
			.name("full_typed_writer")
			.model(chatModel)
			.description("Completely typed writing tool")
			.instruction("Create articles based on structured input (topic, wordCount, style) and return structured output (title, content, characterCount).")
			.inputType(ArticleRequest.class)
			.outputType(ArticleOutput.class)
			.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
			.name("typed_reviewer")
			.model(chatModel)
			.description("Completely typed review tool")
			.instruction("Review the article and return review comments (comments, approved, suggestions).")
			.outputType(ReviewOutput.class)
			.build();

		ReactAgent orchestratorAgent = ReactAgent.builder()
			.name("orchestrator")
			.model(chatModel)
			.instruction("Coordinate the writing and review process.First call the writing tool to create the article, and then call the review tool for review.")
			.tools(List.of(
					AgentTool.getFunctionToolCallback(writerAgent),
					AgentTool.getFunctionToolCallback(reviewerAgent)))
			.build();

		try {
			Optional<OverAllState> result = orchestratorAgent
				.invoke("Please write an essay about friendship, about 200 words, needs to be reviewed");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("=== Agent Tool with All Schema Types Test ===");
			System.out.println(result.get());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("Agent tool with all schema types execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testAgentToolWithMixedSchemas() throws Exception {
		// Mixed use:inputSchema + outputType
		String customInputSchema = """
				{
					"type": "object",
					"properties": {
						"articleText": {
							"type": "string"
						},
						"criteria": {
							"type": "string"
						}
					},
					"required": ["articleText", "criteria"]
				}
				""";

		ReactAgent reviewerAgent = ReactAgent.builder()
			.name("mixed_schema_reviewer")
			.model(chatModel)
			.description("Review tools using mixed schemas")
			.instruction("Conduct reviews based on given article content and review criteria, and return structured review results.")
			.inputSchema(customInputSchema)
			.outputType(ReviewOutput.class)
			.build();

		ReactAgent mainAgent = ReactAgent.builder()
			.name("main_agent")
			.model(chatModel)
			.instruction("Use review tools to review user-contributed content.")
			.tools(List.of(AgentTool.getFunctionToolCallback(reviewerAgent)))
			.build();

		try {
			Optional<OverAllState> result = mainAgent
				.invoke("Please review this passage: Spring is here and the flowers are blooming.Judging criteria: Literary talent and expressiveness");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("=== Agent Tool with Mixed Schemas Test ===");
			System.out.println(result.get());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("Agent tool with mixed schemas execution failed: " + e.getMessage());
		}
	}
}
