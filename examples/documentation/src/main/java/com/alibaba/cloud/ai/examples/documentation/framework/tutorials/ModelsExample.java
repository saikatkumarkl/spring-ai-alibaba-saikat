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
package com.alibaba.cloud.ai.examples.documentation.framework.tutorials;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatOptions;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;

import reactor.core.publisher.Flux;

/**
 * Models Tutorial - Complete Code Examples
 * Demonstrates how to use Chat Model API to interact with various AI models
 *
 * Source: models.md
 */
public class ModelsExample {

	// ==================== DashScopeChatModel ====================

	/**
	 * Example 1: Creating ChatModel
	 */
	public static void createChatModel() {
		// Create DashScope API instance
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Create ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();
	}

	/**
	 * Example 2: Simple Call
	 */
	public static void simpleCall() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Call directly with string
		String response = chatModel.call("Introduce the Spring framework");
		System.out.println(response);
	}

	/**
	 * Example 3: Using Prompt
	 */
	public static void usePrompt() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create Prompt
		Prompt prompt = new Prompt(new UserMessage("Explain what microservice architecture is"));

		// Call and get response
		ChatResponse response = chatModel.call(prompt);
		String answer = response.getResult().getOutput().getText();
		System.out.println(answer);
	}

	// ==================== Configuration Options ====================

	/**
	 * Example 4: Using ChatOptions
	 */
	public static void useChatOptions() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		DashScopeChatOptions options = DashScopeChatOptions.builder()
				.withModel("qwen-plus")           // Model name
				.withTemperature(0.7)              // Temperature parameter
				.withMaxToken(2000)               // Maximum tokens
				.withTopP(0.9)                     // Top-P sampling
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(options)
				.build();
	}

	/**
	 * Example 5: Runtime Options Override
	 */
	public static void runtimeOptionsOverride() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create Prompt with specific options
		DashScopeChatOptions runtimeOptions = DashScopeChatOptions.builder()
				.withTemperature(0.3)  // Lower temperature, more deterministic output
				.withMaxToken(500)
				.build();

		Prompt prompt = new Prompt(
				new UserMessage("Summarize the characteristics of Java in one sentence"),
				runtimeOptions
		);

		ChatResponse response = chatModel.call(prompt);
	}

	// ==================== Streaming Response ====================

	/**
	 * Example 6: Streaming Response
	 */
	public static void streamingResponse() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Use streaming API
		Flux<ChatResponse> responseStream = chatModel.stream(
				new Prompt("Explain Spring Boot's auto-configuration mechanism in detail")
		);

		// Subscribe and process streaming response
		responseStream.subscribe(
				chatResponse -> {
					String content = chatResponse.getResult()
							.getOutput()
							.getText();
					System.out.print(content);
				},
				error -> System.err.println("Error: " + error.getMessage()),
				() -> System.out.println("\nStreaming response completed")
		);
	}

	// ==================== Multi-Turn Conversation ====================

	/**
	 * Example 7: Multi-Turn Conversation
	 */
	public static void multiTurnConversation() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create conversation history
		List<Message> messages = List.of(
				new SystemMessage("You are a Java expert"),
				new UserMessage("What is Spring Boot?"),
				new AssistantMessage("Spring Boot is..."),
				new UserMessage("What are its advantages?")
		);

		Prompt prompt = new Prompt(messages);
		ChatResponse response = chatModel.call(prompt);
	}

	// ==================== Function Calling ====================

	/**
	 * Example 8: Function Calling
	 */
	public static void functionCalling() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Define function
		ToolCallback weatherFunction = FunctionToolCallback.builder("getWeather", (city) -> {
					// Actual weather query logic
					return "Sunny, 25C";
				})
				.description("Get weather for a specified city")
				.inputType(String.class)
				.build();

		// Use function
		DashScopeChatOptions options = DashScopeChatOptions.builder()
				.withToolCallbacks(List.of(weatherFunction))
				.build();

		Prompt prompt = new Prompt("What's the weather in Beijing?", options);
		ChatResponse response = chatModel.call(prompt);
	}

	// ==================== Integration with ReactAgent ====================

	/**
	 * Example 9: Integration with ReactAgent
	 */
	public static void integrationWithReactAgent() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.systemPrompt("You are a helpful AI assistant")
				.build();

		// Call Agent
		AssistantMessage response = agent.call("Help me analyze this issue");
	}

	// ==================== Advanced Configuration Examples ====================

	/**
	 * Example 10: Complete Configuration Example
	 */
	public static void comprehensiveConfiguration() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Configure various options
		DashScopeChatOptions options = DashScopeChatOptions.builder()
				.withModel("qwen-max")              // Use flagship model
				.withTemperature(0.7)               // Control randomness
				.withMaxToken(4000)                // Maximum output length
				.withTopP(0.9)                      // Nucleus sampling
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(options)
				.build();

		// Create complex conversation
		List<Message> messages = List.of(
				new SystemMessage("You are a senior software architect skilled in microservices and cloud-native technologies."),
				new UserMessage("How to design a highly available microservice system?")
		);

		Prompt prompt = new Prompt(messages);
		ChatResponse response = chatModel.call(prompt);

		System.out.println("Response: " + response.getResult().getOutput().getText());
	}

	/**
	 * Example 11: Using Different Models
	 */
	public static void differentModelsUsage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// qwen-turbo: Tongyi Qianwen large-scale language model
		ChatModel turboModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(DashScopeChatOptions.builder()
						.withModel("qwen-turbo")
						.build())
				.build();

		// qwen-plus: Tongyi Qianwen enhanced version
		ChatModel plusModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(DashScopeChatOptions.builder()
						.withModel("qwen-plus")
						.build())
				.build();

		// qwen-max: Tongyi Qianwen flagship version
		ChatModel maxModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(DashScopeChatOptions.builder()
						.withModel("qwen-max")
						.build())
				.build();

		// Use different models
		String question = "What is artificial intelligence?";
		String turboResponse = turboModel.call(question);
		String plusResponse = plusModel.call(question);
		String maxResponse = maxModel.call(question);
	}

	/**
	 * Example 12: Error Handling
	 */
	public static void errorHandling() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		try {
			ChatResponse response = chatModel.call(new Prompt("Hello"));
			System.out.println("Response: " + response.getResult().getOutput().getText());
		}
		catch (Exception e) {
			System.err.println("Error calling model: " + e.getMessage());
			// Handle errors, e.g., retry, fallback, etc.
		}
	}

	/**
	 * Example 13: Effect of Temperature Parameter
	 */
	public static void temperatureEffect() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		String question = "Tell me an interesting story";

		// Low temperature - more deterministic, more conservative output
		ChatModel conservativeModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(DashScopeChatOptions.builder()
						.withTemperature(0.1)
						.build())
				.build();

		// Medium temperature - balanced output
		ChatModel balancedModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(DashScopeChatOptions.builder()
						.withTemperature(0.7)
						.build())
				.build();

		// High temperature - more creative, more random output
		ChatModel creativeModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(DashScopeChatOptions.builder()
						.withTemperature(1.5)
						.build())
				.build();

		String conservativeResponse = conservativeModel.call(question);
		String balancedResponse = balancedModel.call(question);
		String creativeResponse = creativeModel.call(question);

		System.out.println("Conservative (temp=0.1): " + conservativeResponse);
		System.out.println("Balanced (temp=0.7): " + balancedResponse);
		System.out.println("Creative (temp=1.5): " + creativeResponse);
	}

	// ==================== Main Method ====================

	public static void main(String[] args) {
		System.out.println("=== Models Tutorial Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			System.out.println("\n--- Example 1: Creating ChatModel ---");
			createChatModel();

			System.out.println("\n--- Example 2: Simple Call ---");
			simpleCall();

			System.out.println("\n--- Example 3: Using Prompt ---");
			usePrompt();

			System.out.println("\n--- Example 4: Using ChatOptions ---");
			useChatOptions();

			System.out.println("\n--- Example 5: Runtime Options Override ---");
			runtimeOptionsOverride();

			System.out.println("\n--- Example 6: Streaming Response ---");
			streamingResponse();

			System.out.println("\n--- Example 7: Multi-Turn Conversation ---");
			multiTurnConversation();

			System.out.println("\n--- Example 8: Function Calling ---");
			functionCalling();

			System.out.println("\n--- Example 9: Integration with ReactAgent ---");
			integrationWithReactAgent();

			System.out.println("\n--- Example 10: Comprehensive Configuration ---");
			comprehensiveConfiguration();

			System.out.println("\n--- Example 11: Using Different Models ---");
			differentModelsUsage();

			System.out.println("\n--- Example 12: Error Handling ---");
			errorHandling();

			System.out.println("\n--- Example 13: Temperature Effect ---");
			temperatureEffect();

			System.out.println("\n=== All examples executed successfully ===");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}

}
