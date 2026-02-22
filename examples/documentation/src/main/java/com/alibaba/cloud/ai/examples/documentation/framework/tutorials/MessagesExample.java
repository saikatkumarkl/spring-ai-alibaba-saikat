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
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;

import org.springframework.core.io.ClassPathResource;
import org.springframework.util.MimeTypeUtils;

import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

/**
 * Messages Tutorial - Complete Code Examples
 * Demonstrates how to use Messages as the basic unit of model interaction
 *
 * Source: messages.md
 */
public class MessagesExample {

	// ==================== Basic Usage ====================

	/**
	 * Example 1: Basic Message Usage
	 */
	public static void basicMessageUsage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Use DashScope ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		SystemMessage systemMsg = new SystemMessage("You are a helpful assistant.");
		UserMessage userMsg = new UserMessage("Hello, how are you?");

		// Use with chat model
		List<Message> messages = List.of(systemMsg, userMsg);
		Prompt prompt = new Prompt(messages);
		ChatResponse response = chatModel.call(prompt);  // Returns ChatResponse containing AssistantMessage
	}

	// ==================== Text Prompt vs Message Prompt ====================

	/**
	 * Example 2: Text Prompt
	 */
	public static void textPromptUsage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Call directly with string
		String response = chatModel.call("Write a haiku about spring");
	}

	/**
	 * Example 3: Message Prompt
	 */
	public static void messagePromptUsage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		List<Message> messages = List.of(
				new SystemMessage("You are a poetry expert"),
				new UserMessage("Write a haiku about spring"),
				new AssistantMessage("When cherry blossoms bloom...")
		);
		Prompt prompt = new Prompt(messages);
		ChatResponse response = chatModel.call(prompt);
	}

	// ==================== System Message ====================

	/**
	 * Example 4: Basic Instruction
	 */
	public static void basicSystemMessage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Basic instruction
		SystemMessage systemMsg = new SystemMessage("You are a helpful programming assistant.");

		List<Message> messages = List.of(
				systemMsg,
				new UserMessage("How to create a REST API?")
		);
		ChatResponse response = chatModel.call(new Prompt(messages));
	}

	/**
	 * Example 5: Detailed Role Setting
	 */
	public static void detailedSystemMessage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Detailed role setting
		SystemMessage systemMsg = new SystemMessage("""
				You are a senior Java developer skilled in web frameworks.
				Always provide code examples and explain your reasoning.
				Be concise but thorough in explanations.
				""");

		List<Message> messages = List.of(
				systemMsg,
				new UserMessage("How to create a REST API?")
		);
		ChatResponse response = chatModel.call(new Prompt(messages));
	}

	// ==================== User Message ====================

	/**
	 * Example 6: Text Content
	 */
	public static void textUserMessage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Use message objects
		ChatResponse response = chatModel.call(
				new Prompt(List.of(new UserMessage("What is machine learning?")))
		);

		// Use string shortcut
		// Using a string is a shortcut for a single UserMessage
		String response2 = chatModel.call("What is machine learning?");
	}

	/**
	 * Example 7: Message Metadata
	 */
	public static void userMessageMetadata() {
		UserMessage userMsg = UserMessage.builder()
				.text("Hello!")
				.metadata(Map.of(
						"user_id", "alice",  // Optional: identify different users
						"session_id", "sess_123"  // Optional: session identifier
				))
				.build();
	}

	/**
	 * Example 8: Multimodal Content - Image
	 */
	public static void multimodalImageMessage() throws Exception {
		// Create image from URL
		UserMessage userMsg = UserMessage.builder()
				.text("Describe the content of this image.")
				.media(Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(new URL("https://example.com/image.jpg"))
						.build()).build();
	}

	// ==================== Assistant Message ====================

	/**
	 * Example 9: Basic Assistant Message Usage
	 */
	public static void basicAssistantMessage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ChatResponse response = chatModel.call(new Prompt("Explain AI"));
		AssistantMessage aiMessage = response.getResult().getOutput();
		System.out.println(aiMessage.getText());
	}

	/**
	 * Example 10: Manually Creating AI Messages
	 */
	public static void manualAssistantMessage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Manually create AI message (e.g., for conversation history)
		AssistantMessage aiMsg = new AssistantMessage("I'd be happy to help you with that question!");

		// Add to conversation history
		List<Message> messages = List.of(
				new SystemMessage("You are a helpful assistant"),
				new UserMessage("Can you help me?"),
				aiMsg,  // Insert as if it came from the model
				new UserMessage("Great! What is 2+2?")
		);

		ChatResponse response = chatModel.call(new Prompt(messages));
	}

	/**
	 * Example 11: Tool Calls
	 */
	public static void toolCallsInAssistantMessage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		Prompt prompt = new Prompt("What's the weather in Beijing?");
		ChatResponse response = chatModel.call(prompt);
		AssistantMessage aiMessage = response.getResult().getOutput();

		if (aiMessage.hasToolCalls()) {
			for (AssistantMessage.ToolCall toolCall : aiMessage.getToolCalls()) {
				System.out.println("Tool: " + toolCall.name());
				System.out.println("Args: " + toolCall.arguments());
				System.out.println("ID: " + toolCall.id());
			}
		}
	}

	/**
	 * Example 12: Token Usage
	 */
	public static void tokenUsage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ChatResponse response = chatModel.call(new Prompt("Hello!"));
		ChatResponseMetadata metadata = response.getMetadata();

		// Access usage information
		if (metadata != null && metadata.getUsage() != null) {
			System.out.println("Input tokens: " + metadata.getUsage().getPromptTokens());
			System.out.println("Output tokens: " + metadata.getUsage().getCompletionTokens());
			System.out.println("Total tokens: " + metadata.getUsage().getTotalTokens());
		}
	}

	/**
	 * Example 13: Streaming and Chunks
	 */
	public static void streamingMessages() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		Flux<ChatResponse> responseStream = chatModel.stream(new Prompt("Hello"));

		StringBuilder fullResponse = new StringBuilder();
		responseStream.subscribe(
				chunk -> {
					String content = chunk.getResult().getOutput().getText();
					fullResponse.append(content);
					System.out.print(content);
				}
		);
	}

	// ==================== Tool Response Message ====================

	/**
	 * Example 14: Tool Response Message
	 */
	public static void toolResponseMessage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// After model makes tool call
		AssistantMessage aiMessage = AssistantMessage.builder()
				.content("")
				.toolCalls(List.of(
						new AssistantMessage.ToolCall(
								"call_123",
								"tool",
								"get_weather",
								"{\"location\": \"San Francisco\"}"
						)
				))
				.build();

		// Execute tool and create result message
		String weatherResult = "Sunny, 22C";
		ToolResponseMessage toolMessage = ToolResponseMessage.builder()
				.responses(List.of(
						new ToolResponseMessage.ToolResponse("call_123", "get_weather", weatherResult)
				))
				.build();

		// Continue conversation
		List<Message> messages = List.of(
				new UserMessage("What's the weather in San Francisco?"),
				aiMessage,      // Model's tool call
				toolMessage     // Tool execution result
		);
		ChatResponse response = chatModel.call(new Prompt(messages));
	}

	// ==================== Multimodal Content ====================

	/**
	 * Example 15: Image Input - From URL
	 */
	public static void imageInputFromURL() throws Exception {
		// From URL
		UserMessage message = UserMessage.builder()
				.text("Describe the content of this image.")
				.media(Media.builder().mimeType(MimeTypeUtils.IMAGE_JPEG).data(new URL("https://example.com/image.jpg"))
						.build())
				.build();
	}

	/**
	 * Example 16: Image Input - From Local File
	 */
	public static void imageInputFromFile() {
		// From local file
		UserMessage message = UserMessage.builder()
				.text("Describe the content of this image.")
				.media(new Media(
						MimeTypeUtils.IMAGE_JPEG,
						new ClassPathResource("images/photo.jpg")
				))
				.build();
	}

	/**
	 * Example 17: Audio Input
	 */
	public static void audioInput() {
		UserMessage message = UserMessage.builder()
				.text("Describe the content of this audio.")
				.media(new Media(
						MimeTypeUtils.parseMimeType("audio/wav"),
						new ClassPathResource("audio/recording.wav")
				))
				.build();
	}

	/**
	 * Example 18: Video Input
	 */
	public static void videoInput() throws Exception {
		UserMessage message = UserMessage.builder()
				.text("Describe the content of this video.")
				.media(Media.builder().mimeType(MimeTypeUtils.parseMimeType("video/mp4"))
						.data(new URL("\"https://example.com/path/to/video.mp4"))
						.build())
				.build();
	}

	// ==================== Using with Chat Models ====================

	/**
	 * Example 19: Basic Conversation Example
	 */
	public static void basicConversationExample() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		List<Message> conversationHistory = new ArrayList<>();

		// First round of conversation
		conversationHistory.add(new UserMessage("Hello!"));
		ChatResponse response1 = chatModel.call(new Prompt(conversationHistory));
		conversationHistory.add(response1.getResult().getOutput());

		// Second round of conversation
		conversationHistory.add(new UserMessage("Can you help me learn Java?"));
		ChatResponse response2 = chatModel.call(new Prompt(conversationHistory));
		conversationHistory.add(response2.getResult().getOutput());

		// Third round of conversation
		conversationHistory.add(new UserMessage("Where should I start?"));
		ChatResponse response3 = chatModel.call(new Prompt(conversationHistory));
	}

	/**
	 * Example 20: Using Builder Pattern
	 */
	public static void builderPattern() {
		// UserMessage with builder
		UserMessage userMsg = UserMessage.builder()
				.text("Hello, I'd like to learn about CordonData")
				.metadata(Map.of("user_id", "user_123"))
				.build();

		// SystemMessage with builder
		SystemMessage systemMsg = SystemMessage.builder()
				.text("You are a Spring framework expert")
				.metadata(Map.of("version", "1.0"))
				.build();

		// AssistantMessage with builder
		AssistantMessage assistantMsg = AssistantMessage.builder()
				.content("I'd be happy to help you learn CordonData!")
				.build();
	}

	/**
	 * Example 21: Message Copy and Modify
	 */
	public static void messageCopyAndModify() {
		// Copy message
		UserMessage original = new UserMessage("Original message");
		UserMessage copy = original.copy();

		// Use mutate to create a modified copy
		UserMessage modified = original.mutate()
				.text("Modified message")
				.metadata(Map.of("modified", true))
				.build();
	}

	// ==================== Using in ReactAgent ====================

	/**
	 * Example 22: Using Messages in ReactAgent
	 */
	public static void messagesInReactAgent() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.systemPrompt("You are a helpful assistant")
				.build();

		// Use string
		AssistantMessage response1 = agent.call("Hello");

		// Usage UserMessage
		UserMessage userMsg = new UserMessage("Help me write a poem");
		AssistantMessage response2 = agent.call(userMsg);

		// Use message list
		List<Message> messages = List.of(
				new UserMessage("I like spring"),
				new UserMessage("Write a poem about spring")
		);
		AssistantMessage response3 = agent.call(messages);
	}

	// ==================== Main Method ====================

	public static void main(String[] args) {
		System.out.println("=== Messages Tutorial Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			System.out.println("\n--- Example 1: Basic Message Usage ---");
			basicMessageUsage();

			System.out.println("\n--- Example 2: Text Prompt Usage ---");
			textPromptUsage();

			System.out.println("\n--- Example 3: Message Prompt Usage ---");
			messagePromptUsage();

			System.out.println("\n--- Example 4: Basic System Message ---");
			basicSystemMessage();

			System.out.println("\n--- Example 5: Detailed System Message ---");
			detailedSystemMessage();

			System.out.println("\n--- Example 6: Text User Message ---");
			textUserMessage();

			System.out.println("\n--- Example 7: User Message Metadata ---");
			userMessageMetadata();

			System.out.println("\n--- Example 8: Multimodal Image Message ---");
			multimodalImageMessage();

			System.out.println("\n--- Example 9: Basic Assistant Message ---");
			basicAssistantMessage();

			System.out.println("\n--- Example 10: Manual Assistant Message ---");
			manualAssistantMessage();

			System.out.println("\n--- Example 11: Tool Calls in Assistant Message ---");
			toolCallsInAssistantMessage();

			System.out.println("\n--- Example 12: Token Usage ---");
			tokenUsage();

			System.out.println("\n--- Example 13: Streaming Messages ---");
			streamingMessages();

			System.out.println("\n--- Example 14: Tool Response Message ---");
			toolResponseMessage();

			System.out.println("\n--- Example 15: Image Input from URL ---");
			imageInputFromURL();

			System.out.println("\n--- Example 16: Image Input from File ---");
			imageInputFromFile();

			System.out.println("\n--- Example 17: Audio Input ---");
			audioInput();

			System.out.println("\n--- Example 18: Video Input ---");
			videoInput();

			System.out.println("\n--- Example 19: Basic Conversation Example ---");
			basicConversationExample();

			System.out.println("\n--- Example 20: Builder Pattern ---");
			builderPattern();

			System.out.println("\n=== All examples executed successfully ===");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

