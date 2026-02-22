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
package com.alibaba.cloud.ai.examples.documentation.graph.core;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;

import org.springframework.ai.chat.client.ChatClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Memory Management Example
 * Demonstrates short-term and long-term memory management
 */
public class MemoryExample {

	/**
	 * Example 1: Adding Short-term Memory
	 */
	public static void addShortTermMemory(ChatClient.Builder chatClientBuilder) throws GraphStateException {
		// Create memory checkpointer
		MemorySaver checkpointer = new MemorySaver();

		SaverConfig saverConfig = SaverConfig.builder()
				.register(checkpointer)
				.build();

		// Define state strategies
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
			keyStrategyMap.put("messages", new AppendStrategy());
			return keyStrategyMap;
		};

		// Create chat node
		var chatNode = node_async(state -> {
			List<Map<String, String>> messages =
					(List<Map<String, String>>) state.value("messages").orElse(List.of());

			// Use ChatClient to call AI model
			ChatClient chatClient = chatClientBuilder.build();
			String response = chatClient.prompt()
					.user(messages.get(messages.size() - 1).get("content"))
					.call()
					.content();

			return Map.of("messages", List.of(
					Map.of("role", "assistant", "content", response)
			));
		});

		// Build graph
		StateGraph stateGraph = new StateGraph(keyStrategyFactory)
				.addNode("chat", chatNode)
				.addEdge(START, "chat")
				.addEdge("chat", END);

		// Compile graph
		CompiledGraph graph = stateGraph.compile(
				CompileConfig.builder()
						.saverConfig(saverConfig)
						.build()
		);

		// First round of conversation
		RunnableConfig config = RunnableConfig.builder()
				.threadId("conversation-1")
				.build();

		graph.invoke(Map.of("messages", List.of(
				Map.of("role", "user", "content", "Hello! I am Bob")
		)), config);

		// Second round of conversation (using same threadId)
		graph.invoke(Map.of("messages", List.of(
				Map.of("role", "user", "content", "What is my name?")
		)), config);
		// AI will be able to remember the previous conversation, answering "Bob"
		System.out.println("Short-term memory example executed");
	}

	/**
	 * Example 2: Using Store for Long-term Memory
	 */
	public static void longTermMemoryWithDatabase() throws GraphStateException {
		// Use Store in nodes to store user information
		var userProfileNode = com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async((state, config) -> {
			String userId = (String) state.value("userId").orElse("");

			if (userId.isEmpty()) {
				return Map.of("userProfile", Map.of("name", "Unknown", "preferences", "default"));
			}

			// Get user profile from Store
			Store store = config.store();
			if (store != null) {
				Optional<StoreItem> itemOpt = store.getItem(List.of("user_profiles"), userId);
				if (itemOpt.isPresent()) {
					Map<String, Object> userProfile = itemOpt.get().getValue();
					return Map.of("userProfile", userProfile);
				}
			}

			// If not found, return default values
			Map<String, Object> userProfile = Map.of("name", "User", "preferences", "default");
			return Map.of("userProfile", userProfile);
		});

		// Create graph
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
			keyStrategyMap.put("userId", new ReplaceStrategy());
			keyStrategyMap.put("userProfile", new ReplaceStrategy());
			return keyStrategyMap;
		};

		StateGraph stateGraph = new StateGraph(keyStrategyFactory)
				.addNode("load_profile", userProfileNode)
				.addEdge(START, "load_profile")
				.addEdge("load_profile", END);

		CompiledGraph graph = stateGraph.compile(CompileConfig.builder().build());

		// Create long-term memory store and pre-populate data
		MemoryStore memoryStore = new MemoryStore();
		Map<String, Object> profileData = new HashMap<>();
		profileData.put("name", "Zhang San");
		profileData.put("preferences", "Likes programming");
		StoreItem profileItem = StoreItem.of(List.of("user_profiles"), "user_001", profileData);
		memoryStore.putItem(profileItem);

		// Run graph
		RunnableConfig config = RunnableConfig.builder()
				.threadId("profile_thread")
				.store(memoryStore)
				.build();

		Optional<OverAllState> stateOptiona = graph.invoke(Map.of("userId", "user_001"), config);
		Map<String, Object> result = stateOptiona.get().data();
		System.out.println("Loaded user profile: " + result.get("userProfile"));

		System.out.println("Long-term memory with Store example executed");
	}

	/**
	 * Example 3: Using Store Cache for Long-term Memory
	 */
	public static void longTermMemoryWithRedis() throws GraphStateException {
		var cacheNode = com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async((state, config) -> {
			String key = (String) state.value("cacheKey").orElse("");

			if (key.isEmpty()) {
				return Map.of("result", "no_key");
			}

			// Get cached data from Store
			Store store = config.store();
			if (store != null) {
				Optional<StoreItem> itemOpt = store.getItem(List.of("cache"), key);
				if (itemOpt.isPresent()) {
					// Cache hit
					Map<String, Object> cachedData = itemOpt.get().getValue();
					return Map.of("result", cachedData.get("value"));
				}
			}

			// Cache miss, executing computation or query
			Object computedData = performExpensiveOperation(key);

			// Store to Store
			if (store != null) {
				Map<String, Object> cacheValue = new HashMap<>();
				cacheValue.put("value", computedData);
				StoreItem cacheItem = StoreItem.of(List.of("cache"), key, cacheValue);
				store.putItem(cacheItem);
			}

			return Map.of("result", computedData);
		});

		// Create graph
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
			keyStrategyMap.put("cacheKey", new ReplaceStrategy());
			keyStrategyMap.put("result", new ReplaceStrategy());
			return keyStrategyMap;
		};

		StateGraph stateGraph = new StateGraph(keyStrategyFactory)
				.addNode("cache", cacheNode)
				.addEdge(START, "cache")
				.addEdge("cache", END);

		CompiledGraph graph = stateGraph.compile(CompileConfig.builder().build());

		// Create long-term memory store
		MemoryStore memoryStore = new MemoryStore();

		// First call (cache miss)
		RunnableConfig config = RunnableConfig.builder()
				.threadId("cache_thread")
				.store(memoryStore)
				.build();

		Optional<OverAllState> stateOptional = graph.invoke(Map.of("cacheKey", "expensive_key"), config);
		Map<String, Object> result1 = stateOptional.get().data();
		System.out.println("First call result: " + result1.get("result"));

		// Second call (cache hit)
		Optional<OverAllState> stateOptiona = graph.invoke(Map.of("cacheKey", "expensive_key"), config);
		Map<String, Object> result2 = stateOptional.get().data();
		System.out.println("Second call result (from cache): " + result2.get("result"));

		System.out.println("Long-term memory with Store cache example executed");
	}

	// Simulate expensive operation
	private static Object performExpensiveOperation(String key) {
		// Simulate expensive computation
		return "computed_result_for_" + key;
	}

	/**
	 * Example 4: Combining Short-term and Long-term Memory
	 */
	public static void combinedMemoryExample(ChatClient.Builder chatClientBuilder) throws GraphStateException {
		// Define state
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
			keyStrategyMap.put("userId", new ReplaceStrategy());
			keyStrategyMap.put("messages", new AppendStrategy());
			keyStrategyMap.put("userPreferences", new ReplaceStrategy());
			return keyStrategyMap;
		};

		// Load user preferences (long-term memory)
		var loadUserPreferences = com.alibaba.cloud.ai.graph.action.AsyncNodeActionWithConfig.node_async((state, config) -> {
			String userId = (String) state.value("userId").orElse("");

			if (userId.isEmpty()) {
				return Map.of("userPreferences", Map.of("theme", "default", "language", "zh"));
			}

			// Load user preferences from Store
			Store store = config.store();
			if (store != null) {
				Optional<StoreItem> itemOpt = store.getItem(List.of("user_preferences"), userId);
				if (itemOpt.isPresent()) {
					Map<String, Object> preferences = itemOpt.get().getValue();
					return Map.of("userPreferences", preferences);
				}
			}

			// If not found, return default preferences
			Map<String, Object> preferences = Map.of("theme", "dark", "language", "zh");
			return Map.of("userPreferences", preferences);
		});

		// Chat node (using short-term and long-term memory)
		var chatNode = node_async(state -> {
			List<Map<String, String>> messages =
					(List<Map<String, String>>) state.value("messages").orElse(List.of());
			Map<String, Object> preferences =
					(Map<String, Object>) state.value("userPreferences").orElse(Map.of());

			// Build prompt including user preferences
			String userPrompt = messages.get(messages.size() - 1).get("content");
			String enhancedPrompt = "User preferences: " + preferences + "\nUser question: " + userPrompt;

			// Call AI
			ChatClient chatClient = chatClientBuilder.build();
			String response = chatClient.prompt()
					.user(enhancedPrompt)
					.call()
					.content();

			return Map.of("messages", List.of(
					Map.of("role", "assistant", "content", response)
			));
		});

		// Build graph
		StateGraph stateGraph = new StateGraph(keyStrategyFactory)
				.addNode("load_preferences", loadUserPreferences)
				.addNode("chat", chatNode)
				.addEdge(START, "load_preferences")
				.addEdge("load_preferences", "chat")
				.addEdge("chat", END);

		// Configure checkpoint (short-term memory)
		SaverConfig saverConfig = SaverConfig.builder()
				.register(new MemorySaver())
				.build();

		// Compile graph
		CompiledGraph graph = stateGraph.compile(
				CompileConfig.builder()
						.saverConfig(saverConfig)
						.build()
		);

		// Create long-term memory store and pre-populate user preferences
		MemoryStore memoryStore = new MemoryStore();
		Map<String, Object> preferencesData = new HashMap<>();
		preferencesData.put("theme", "dark");
		preferencesData.put("language", "zh");
		preferencesData.put("timezone", "Asia/Shanghai");
		StoreItem preferencesItem = StoreItem.of(List.of("user_preferences"), "user_002", preferencesData);
		memoryStore.putItem(preferencesItem);

		// Run graph
		RunnableConfig config = RunnableConfig.builder()
				.threadId("combined_thread")
				.store(memoryStore)
				.build();

		// First round (load preferences and start conversation)
		graph.invoke(Map.of(
				"userId", "user_002",
				"messages", List.of(Map.of("role", "user", "content", "Hello"))
		), config);

		// Second round (using short-term and long-term memory)
		graph.invoke(Map.of(
				"userId", "user_002",
				"messages", List.of(Map.of("role", "user", "content", "Give me some suggestions based on my preferences"))
		), config);

		System.out.println("Combined memory example created");
	}

	public static void main(String[] args) {
		System.out.println("=== Memory Management Examples ===\n");

		try {
			// Example 1: Adding Short-term Memory (requires ChatClient)
			System.out.println("Example 1: Adding Short-term Memory");
			System.out.println("Note: This example requires ChatClient, skipping execution");
			// addShortTermMemory(ChatClient.builder(...));
			System.out.println();

			// Example 2: Using Store for Long-term Memory
			System.out.println("Example 2: Using Store for Long-term Memory");
			longTermMemoryWithDatabase();
			System.out.println();

			// Example 3: Using Store Cache for Long-term Memory
			System.out.println("Example 3: Using Store Cache for Long-term Memory");
			longTermMemoryWithRedis();
			System.out.println();

			// Example 4: Combining Short-term and Long-term Memory (requires ChatClient)
			System.out.println("Example 4: Combining Short-term and Long-term Memory");
			System.out.println("Note: This example requires ChatClient, skipping execution");
			// combinedMemoryExample(ChatClient.builder(...));
			System.out.println();

			System.out.println("All examples executed successfully");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

