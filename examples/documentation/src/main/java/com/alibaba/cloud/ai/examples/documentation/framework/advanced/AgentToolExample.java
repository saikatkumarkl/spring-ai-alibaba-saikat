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
package com.alibaba.cloud.ai.examples.documentation.framework.advanced;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.AgentTool;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

import java.util.List;
import java.util.Optional;

/**
 * Agent as tool example
 *
 * Demonstrates Multi-agent tool invocation patterns, including:
 * 1. Use sub-Agents as tools
 * 2. Customize input and output Schema
 * 3. Typed Agent tool call
 * 4. Complete tool call example
 *
 * Reference documentation: advanced_doc/agent-tool.md
 */
public class AgentToolExample {

	private final ChatModel chatModel;

	public AgentToolExample(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/**
	 * Main method: run all examples
	 *
	 * Note: A ChatModel instance needs to be configured to run
	 */
	public static void main(String[] args) {
		//Create a DashScope API instance
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		//Create ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		if (chatModel == null) {
			System.err.println("Error: Please configure ChatModel instance first");
			System.err.println("Please set the AI_DASHSCOPE_API_KEY environment variable");
			return;
		}

		//Create a sample instance
		AgentToolExample example = new AgentToolExample(chatModel);

		//Run all examples
		example.runAllExamples();
	}

	/**
	 * Example 1: Basic Agent Tool call
	 *
	 * The main Agent calls the sub-Agent as a tool, and the sub-Agent performs specific tasks and returns results.
	 */
	public void example1_basicAgentTool() throws GraphRunnerException {
		//Create sub-Agent - used as a tool
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Can write articles")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.build();

		//Create the main Agent and use the sub-Agent as a tool
		ReactAgent blogAgent = ReactAgent.builder()
				.name("blog_agent")
				.model(chatModel)
				.instruction("Write an article based on a topic given by the user.Use writing tools to complete tasks.")
				.tools(AgentTool.getFunctionToolCallback(writerAgent))
				.build();

		//use
		Optional<OverAllState> result = blogAgent.invoke("Help me write a prose of about 100 words");

		if (result.isPresent()) {
			System.out.println("Article generated successfully");
			//Processing results
		}
	}

	/**
	 * Example 2: Use inputSchema to control input of sub-Agent
	 *
	 * By defining the input schema, the sub-agent can receive structured input information.
	 */
	public void example2_agentToolWithInputSchema() throws GraphRunnerException {
		//Define the input schema of the sub-agent
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
				.tools(AgentTool.getFunctionToolCallback(writerAgent))
				.build();

		Optional<OverAllState> result = coordinatorAgent.invoke("Please write a prose about spring, about 150 words");

		if (result.isPresent()) {
			System.out.println("Structured input example executed successfully");
		}
	}

	/**
	 * Example 3: Defining typed input using inputType
	 *
	 * Use Java type definition input and the framework will automatically generate JSON Schema
	 */
	public void example3_agentToolWithInputType() throws GraphRunnerException {
		//Define input type
		record ArticleRequest(String topic, int wordCount, String style) { }

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
				.tools(AgentTool.getFunctionToolCallback(writerAgent))
				.build();

		Optional<OverAllState> result = coordinatorAgent.invoke("Please write a modern poem about autumn, about 100 words");

		if (result.isPresent()) {
			System.out.println("Typed input example executed successfully");
		}
	}

	/**
	 * Example 4: Use outputSchema to control the output of sub-Agents
	 *
	 * Define the output schema so that the sub-agent returns a structured output format
	 */
	public void example4_agentToolWithOutputSchema() throws GraphRunnerException {
		// Use BeanOutputConverter to generate outputSchema
		BeanOutputConverter<ArticleOutput> outputConverter = new BeanOutputConverter<>(ArticleOutput.class);
		String format = outputConverter.getFormat();

		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_with_output_schema")
				.model(chatModel)
				.description("Write articles and return structured output")
				.instruction("You are a professional writer.Please create an article and return the results strictly in the specified JSON format.")
				.outputSchema(format)
				.build();

		ReactAgent coordinatorAgent = ReactAgent.builder()
				.name("coordinator_output_schema")
				.model(chatModel)
				.instruction("Call the writing tool to complete the user request, and the tool will return structured article data.")
				.tools(AgentTool.getFunctionToolCallback(writerAgent))
				.build();

		Optional<OverAllState> result = coordinatorAgent.invoke("Write a short essay about winter");

		if (result.isPresent()) {
			System.out.println("Structured output example execution successful");
		}
	}

	/**
	 * Example 5: Defining typed output using outputType
	 *
	 * Define the output using Java types and the framework will automatically generate the output schema
	 */
	public void example5_agentToolWithOutputType() throws GraphRunnerException {
		//Define output type
		class ArticleOutput {
			private String title;
			private String content;
			private int characterCount;

			// getters and setters
			public String getTitle() {
				return title;
			}

			public String getContent() {
				return content;
			}

			public int getCharacterCount() {
				return characterCount;
			}

			public void setTitle(String title) {
				this.title = title;
			}


			public void setContent(String content) {
				this.content = content;
			}


			public void setCharacterCount(int characterCount) {
				this.characterCount = characterCount;
			}
		}

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
				.tools(AgentTool.getFunctionToolCallback(writerAgent))
				.build();

		Optional<OverAllState> result = coordinatorAgent.invoke("Write a short poem about summer");

		if (result.isPresent()) {
			System.out.println("Typed output example executed successfully");
		}
	}

	/**
	 * Example 6: Fully typed example
	 *
	 * Use both inputType and outputType for complete typed Agent tool calls
	 */
	public void example6_fullTypedAgentTool() throws GraphRunnerException {
		//Define input and output types
		record ArticleRequest(String topic, int wordCount, String style) { }

		class ArticleOutput {
			private String title;
			private String content;
			private int characterCount;

			public String getTitle() {
				return title;
			}

			public String getContent() {
				return content;
			}

			public int getCharacterCount() {
				return characterCount;
			}

			public void setTitle(String title) {
				this.title = title;
			}


			public void setContent(String content) {
				this.content = content;
			}


			public void setCharacterCount(int characterCount) {
				this.characterCount = characterCount;
			}
		}

		class ReviewOutput {
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

		//Create a fully typed Agent
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
				.tools(
						AgentTool.getFunctionToolCallback(writerAgent),
						AgentTool.getFunctionToolCallback(reviewerAgent)
				)
				.build();

		Optional<OverAllState> result = orchestratorAgent.invoke("Please write an essay about friendship, about 200 words, needs to be reviewed");

		if (result.isPresent()) {
			System.out.println("Completely typed example executed successfully");
		}
	}

	/**
	 * Example 7: Multiple sub-agents as tools
	 *
	 * The main Agent can access multiple different sub-Agent tools and call them as needed
	 */
	public void example7_multipleAgentTools() throws GraphRunnerException {
		//Create a writing agent
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Specialized in article creation and content generation")
				.instruction("You are a professional writer who is good at writing all kinds of articles.")
				.build();

		//Create translation agent
		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Specialized in text translation")
				.instruction("You are a professional translator who can accurately translate multiple languages.")
				.build();

		//Create summary agent
		ReactAgent summarizerAgent = ReactAgent.builder()
				.name("summarizer_agent")
				.model(chatModel)
				.description("Specialized in content summary and refining")
				.instruction("You are an expert at summarizing content and distilling key information.")
				.build();

		//Create a main agent and integrate multiple tools
		ReactAgent multiToolAgent = ReactAgent.builder()
				.name("multi_tool_coordinator")
				.model(chatModel)
				.instruction("You have access to multiple professional tools: writing, translating and summarizing." +
						"Choose the right tool to complete the task based on user needs.")
				.tools(
						AgentTool.getFunctionToolCallback(writerAgent),
						AgentTool.getFunctionToolCallback(translatorAgent),
						AgentTool.getFunctionToolCallback(summarizerAgent)
				)
				.build();

		//Test different requests
		multiToolAgent.invoke("Please write an article about AI, then translate it into English, and finally give an abstract");

		System.out.println("Multi-tool Agent example executed successfully");
	}

	/**
	 * Article output class - used in Example 4 and Example 5
	 */
	public static class ArticleOutput {
		private String title;
		private String content;
		private int characterCount;

		// Getters and Setters
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

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("=== Agent Tool Example ===\n");

		try {
			System.out.println("Example 1: Basic Agent Tool call");
			example1_basicAgentTool();
			System.out.println();

			System.out.println("Example 2: Use inputSchema to control input");
			example2_agentToolWithInputSchema();
			System.out.println();

			System.out.println("Example 3: Define typed input using inputType");
			example3_agentToolWithInputType();
			System.out.println();

			System.out.println("Example 4: Use outputSchema to control output");
			example4_agentToolWithOutputSchema();
			System.out.println();

			System.out.println("Example 5: Defining typed output using outputType");
			example5_agentToolWithOutputType();
			System.out.println();

			System.out.println("Example 6: Fully typed example");
			example6_fullTypedAgentTool();
			System.out.println();

			System.out.println("Example 7: Multiple sub-Agents as tools");
			example7_multipleAgentTools();
			System.out.println();

		}
		catch (Exception e) {
			System.err.println("An error occurred while executing the example:" + e.getMessage());
			e.printStackTrace();
		}
	}
}

