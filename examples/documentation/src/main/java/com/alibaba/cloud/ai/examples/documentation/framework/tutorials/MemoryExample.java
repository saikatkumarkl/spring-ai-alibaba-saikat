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
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.redisson.api.RedissonClient;

/**
 * Memory Tutorial - Complete Code Examples
 * Demonstrates how to use short-term memory to let Agent remember previous interactions
 *
 * Source: memory.md
 */
public class MemoryExample {

	// ==================== Basic Usage ====================

	/**
	 * Example 1: Basic Memory Configuration
	 */
	public static void basicMemoryConfiguration() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create sample tools
		ToolCallback getUserInfoTool = createGetUserInfoTool();

		// Configure checkpointer
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(getUserInfoTool)
				.saver(new MemorySaver())
				.build();

		// Use thread_id to maintain conversation context
		RunnableConfig config = RunnableConfig.builder()
				.threadId("1") // threadId specifies session ID
				.build();

		agent.call("Hi! My name is Bob.", config);
	}

	/**
	 * Example 2: Using Redis Checkpointer in Production
	 */
	public static void productionMemoryConfiguration(RedissonClient redissonClient) {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ToolCallback getUserInfoTool = createGetUserInfoTool();

		// Configure Redis checkpointer
		RedisSaver redisSaver = RedisSaver.builder().redisson(redissonClient).build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(getUserInfoTool)
				.saver(redisSaver)
				.build();
	}

	// ==================== Custom Agent Memory ====================

	/**
	 * Example 5: Using Message Trimming
	 */
	public static void useMessageTrimming() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ToolCallback[] tools = new ToolCallback[0];

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(tools)
				.hooks(new MessageTrimmingHook())
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.build();

		agent.call("Hi, my name is bob", config);
		agent.call("Write a short poem about cats", config);
		agent.call("Now do the same for dogs", config);
		AssistantMessage finalResponse = agent.call("What's my name?", config);

		System.out.println(finalResponse.getText());
		// Output: Your name is Bob. You told me earlier.
	}

	// ==================== Trimming Messages ====================

	/**
	 * Example 8: Using Message Deletion
	 */
	public static void useMessageDeletion() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.systemPrompt("Please be concise and clear.")
				.hooks(new MessageDeletionHook())
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.build();

		// First call
		agent.call("Hi! I'm bob", config);
		// Output: [('human', "Hi! I'm bob"), ('assistant', 'Hello Bob! Nice to meet you...')]

		// Second call
		agent.call("What's my name?", config);
		// Output: [('human', "What's my name?"), ('assistant', 'Your name is Bob...')]
	}

	/**
	 * Example 10: Using Message Summarization
	 */
	public static void useMessageSummarization() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Model for summarization (can be a cheaper model)
		ChatModel summaryModel = chatModel;

		MessageSummarizationHook summarizationHook = new MessageSummarizationHook(
				summaryModel,
				4000,  // Trigger summarization at 4000 tokens
				20     // Keep last 20 messages after summarization
		);

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.hooks(summarizationHook)
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.build();

		agent.call("Hi, my name is bob", config);
		agent.call("Write a short poem about cats", config);
		agent.call("Now do the same for dogs", config);
		AssistantMessage finalResponse = agent.call("What's my name?", config);

		System.out.println(finalResponse.getText());
		// Output: Your name is Bob!
	}

	// ==================== Deleting Messages ====================

	/**
	 * Example 12: Accessing Memory via Tools
	 */
	public static void accessMemoryInTool() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools
		ToolCallback getUserInfoTool = FunctionToolCallback
				.builder("get_user_info", new UserInfoTool())
				.description("Look up user information")
				.inputType(String.class)
				.build();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(getUserInfoTool)
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.addMetadata("user_id", "user_123")
				.build();

		AssistantMessage response = agent.call("Get user information", config);
		System.out.println(response.getText());
	}

	/**
	 * Create sample tools
	 */
	private static ToolCallback createGetUserInfoTool() {
		return FunctionToolCallback.builder("get_user_info", (String query) -> {
					return "User info: " + query;
				})
				.description("Get user information")
				.inputType(String.class)
				.build();
	}

	public static void main(String[] args) {
		System.out.println("=== Memory Tutorial Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			// Example 1: Basic Memory Configuration
			System.out.println("\n--- Example 1: Basic Memory Configuration ---");
			basicMemoryConfiguration();

			// Example 2: Using Redis Checkpointer in Production (requires RedissonClient instance, skipped here)
			System.out.println("\n--- Example 2: Production Redis Checkpointer (skipped, requires RedissonClient) ---");
			// productionMemoryConfiguration(redissonClient);

			// Example 5: Using Message Trimming
			System.out.println("\n--- Example 5: Using Message Trimming ---");
			useMessageTrimming();

			// Example 8: Using Message Deletion
			System.out.println("\n--- Example 8: Using Message Deletion ---");
			useMessageDeletion();

			// Example 10: Using Message Summarization
			System.out.println("\n--- Example 10: Using Message Summarization ---");
			useMessageSummarization();

			// Example 12: Accessing Memory via Tools
			System.out.println("\n--- Example 12: Accessing Memory via Tools ---");
			accessMemoryInTool();

			System.out.println("\n=== All examples executed successfully ===");
		}
		catch (GraphRunnerException e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
		catch (Exception e) {
			System.err.println("Unexpected error: " + e.getMessage());
			e.printStackTrace();
		}
	}

	// ==================== Summarize Messages ====================

	/**
	 * Example 3: Accessing and Modifying State in Hook
	 * Note: This Hook is mainly for accessing message history without modifying messages, so ModelHook can still be used
	 * But if messages need to be modified, use MessagesModelHook instead
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	public static class CustomMemoryHook extends MessagesModelHook {

		@Override
		public String getName() {
			return "custom_memory";
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			// Access message history (previousMessages already provides the message list)
			// Process messages...
			// If messages need to be modified, return a new AgentCommand
			// Here we only access without modifying, so return original messages
			return new AgentCommand(previousMessages);
		}
	}

	/**
	 * Example 4: Message Trimming Hook
	 * Implemented using MessagesModelHook, trims the message list before model call
	 * Keeps the first message and last keepCount messages, removes messages in between
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	public static class MessageTrimmingHook extends MessagesModelHook {

		private static final int MAX_MESSAGES = 3;

		@Override
		public String getName() {
			return "message_trimming";
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			if (previousMessages.size() <= MAX_MESSAGES) {
				// If message count does not exceed limit, no changes needed
				return new AgentCommand(previousMessages);
			}

			int keepCount = previousMessages.size() % 2 == 0 ? 3 : 4;

			// Build message list to keep: first message + last keepCount messages
			List<Message> trimmedMessages = new ArrayList<>();
			// Keep the first message
			if (!previousMessages.isEmpty()) {
				trimmedMessages.add(previousMessages.get(0));
			}
			// Keep the last keepCount messages
			if (previousMessages.size() - keepCount > 0) {
				trimmedMessages.addAll(previousMessages.subList(
						previousMessages.size() - keepCount,
						previousMessages.size()
				));
			}

			// Use REPLACE strategy to replace all messages
			return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
		}
	}

	// ==================== Accessing Memory ====================

	/**
	 * Example 6: Message Deletion Hook
	 * Implemented using MessagesModelHook, deletes the two earliest messages after model call
	 */
	@HookPositions({HookPosition.AFTER_MODEL})
	public static class MessageDeletionHook extends MessagesModelHook {

		@Override
		public String getName() {
			return "message_deletion";
		}

		@Override
		public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
			if (previousMessages.size() <= 2) {
				// If message count does not exceed 2, no deletion needed
				return new AgentCommand(previousMessages);
			}

			// Delete the two earliest messages, keep the rest
			List<Message> remainingMessages = previousMessages.subList(2, previousMessages.size());

			// Use REPLACE strategy to replace all messages
			return new AgentCommand(remainingMessages, UpdatePolicy.REPLACE);
		}
	}

	/**
	 * Example 7: Delete All Messages
	 * Implemented using MessagesModelHook, deletes all messages after model call
	 */
	@HookPositions({HookPosition.AFTER_MODEL})
	public static class ClearAllMessagesHook extends MessagesModelHook {

		@Override
		public String getName() {
			return "clear_all_messages";
		}

		@Override
		public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
			// Delete all messages, return empty list
			List<Message> emptyMessages = new ArrayList<>();
			// Use REPLACE strategy to replace all messages with empty list
			return new AgentCommand(emptyMessages, UpdatePolicy.REPLACE);
		}
	}

	// ==================== Helper Methods ====================

	/**
	 * Example 9: Message Summarization Hook
	 * Implemented using MessagesModelHook, checks message count before model call and generates summary if exceeding threshold
	 * Removes old messages, keeps summary message and recent messages
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	public static class MessageSummarizationHook extends MessagesModelHook {

		private final ChatModel summaryModel;
		private final int maxTokensBeforeSummary;
		private final int messagesToKeep;

		public MessageSummarizationHook(
				ChatModel summaryModel,
				int maxTokensBeforeSummary,
				int messagesToKeep
		) {
			this.summaryModel = summaryModel;
			this.maxTokensBeforeSummary = maxTokensBeforeSummary;
			this.messagesToKeep = messagesToKeep;
		}

		@Override
		public String getName() {
			return "message_summarization";
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			// Estimate token count (simplified)
			int estimatedTokens = previousMessages.stream()
					.mapToInt(m -> m.getText().length() / 4)
					.sum();

			if (estimatedTokens < maxTokensBeforeSummary) {
				// If token count does not exceed threshold, no summarization needed
				return new AgentCommand(previousMessages);
			}

			// Need to summarize
			int messagesToSummarize = previousMessages.size() - messagesToKeep;
			if (messagesToSummarize <= 0) {
				// If message count is insufficient for summarization, no changes needed
				return new AgentCommand(previousMessages);
			}

			List<Message> oldMessages = previousMessages.subList(0, messagesToSummarize);
			List<Message> recentMessages = previousMessages.subList(
					messagesToSummarize,
					previousMessages.size()
			);

			// Generate summary
			String summary = generateSummary(oldMessages);

			// Create summary message
			SystemMessage summaryMessage = new SystemMessage(
					"## Previous Conversation Summary:\n" + summary
			);

			// Build new message list: summary message + recent messages
			List<Message> newMessages = new ArrayList<>();
			newMessages.add(summaryMessage);
			newMessages.addAll(recentMessages);

			// Use REPLACE strategy to replace all messages
			return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
		}

		private String generateSummary(List<Message> messages) {
			StringBuilder conversation = new StringBuilder();
			for (Message msg : messages) {
				conversation.append(msg.getMessageType())
						.append(": ")
						.append(msg.getText())
						.append("\n");
			}

			String summaryPrompt = "Please briefly summarize the following conversation:\n\n" + conversation;

			ChatResponse response = summaryModel.call(
					new Prompt(new UserMessage(summaryPrompt))
			);

			return response.getResult().getOutput().getText();
		}
	}

	// ==================== Main Method ====================

	/**
	 * Example 11: Reading Short-term Memory in Tools
	 */
	public static class UserInfoTool implements BiFunction<String, ToolContext, String> {

		@Override
		public String apply(String query, ToolContext toolContext) {
			// Get user information from context
			RunnableConfig config = (RunnableConfig) toolContext.getContext().get("config");
			String userId = (String) config.metadata("user_id").orElse("");

			if ("user_123".equals(userId)) {
				return "User is John Smith";
			}
			else {
				return "Unknown user";
			}
		}
	}
}

