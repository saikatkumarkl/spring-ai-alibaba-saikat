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
package com.alibaba.cloud.ai.graph.agent.memory;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test for ReactAgent long-term memory functionality using MemoryStore
 *
 * Long-term memory allows the agent to store and retrieve information across sessions.
 * This test demonstrates:
 * 1. Using MemoryStore with Interceptor to manage long-term memory
 * 2. Using MemoryStore with tools to save and retrieve user information
 * 3. Combining short-term and long-term memory
 */
@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class LongTermMemoryTest {

	private ChatModel chatModel;
	private MemoryStore memoryStore;

	// Memory store request/response records
	public record SaveMemoryRequest(List<String> namespace, String key, Map<String, Object> value) {}
	public record GetMemoryRequest(List<String> namespace, String key) {}
	public record MemoryResponse(String message, Map<String, Object> value) {}

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create in-memory MemoryStore
		this.memoryStore = new MemoryStore();
	}

	/**
	 * Test 1: Long-term memory using Interceptor
	 * Interceptor automatically saves and retrieves user context
	 */
	@Test
	void testLongTermMemoryWithInterceptor() throws Exception {
		// Create a memory interceptor that loads user profile before model call
		ModelHook memoryInterceptor = new ModelHook() {
			@Override
			public String getName() {
				return "memory_interceptor";
			}

			@Override
			public List<JumpTo> canJumpTo() {
				return List.of();
			}

			@Override
			public HookPosition[] getHookPositions() {
				return new HookPosition[]{HookPosition.BEFORE_MODEL};
			}

			@Override
			public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
				// Get userId from config
				String userId = (String) config.metadata("user_id").get();

				// Load user profile from memory store
				Optional<StoreItem> itemOpt = memoryStore.getItem(List.of("user_profiles"), userId);
				if (itemOpt.isPresent()) {
					Map<String, Object> profileData = itemOpt.get().getValue();
					System.out.println("Loaded user profile from long-term memory: " + profileData);

					// Inject user context into system message
					String userContext = String.format(
							"User information: name=%s, age=%s, email=%s, preference=%s",
							profileData.get("name"),
							profileData.get("age"),
							profileData.get("email"),
							profileData.get("preferences")
					);

					// Add system message with user context
					List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
					List<Message> newMessages = new ArrayList<>(messages);

					// Find existing SystemMessage and append userContext to it
					boolean foundSystemMessage = false;
					for (int i = 0; i < newMessages.size(); i++) {
						if (newMessages.get(i) instanceof SystemMessage) {
							SystemMessage existingSystemMessage = (SystemMessage) newMessages.get(i);
							String updatedText = existingSystemMessage.getText() + "\n\n" + userContext;
							newMessages.set(i, new SystemMessage(updatedText));
							foundSystemMessage = true;
							break;
						}
					}

					// If no SystemMessage found, add one at the beginning
					if (!foundSystemMessage) {
						newMessages.add(0, new SystemMessage(userContext));
					}

					return CompletableFuture.completedFuture(Map.of("messages", newMessages));
				}

				return CompletableFuture.completedFuture(Map.of());
			}

		};

		// Pre-populate memory store with user profile
		Map<String, Object> profileData = new HashMap<>();
		profileData.put("name", "Wang Xiaoming");
		profileData.put("age", 28);
		profileData.put("email", "wang@example.com");
		profileData.put("preferences", List.of("like coffee", "like reading"));

		StoreItem profileItem = StoreItem.of(List.of("user_profiles"), "user_001", profileData);
		memoryStore.putItem(profileItem);

		// Create agent with memory interceptor
		ReactAgent agent = ReactAgent.builder()
				.name("memory_agent")
				.model(chatModel)
				.hooks(memoryInterceptor)
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("session_001")
				.addMetadata("user_id", "user_001")
				.build();

		// Ask agent about user information
		Optional<OverAllState> result = agent.invoke("Please give me some information.", config);
		AssistantMessage response = (AssistantMessage) result.get().value("messages")
				.map(m -> ((List<Message>) m).get(((List<Message>) m).size() - 1))
				.orElseThrow();

		System.out.println("Response with long-term memory: " + response.getText());
		assertTrue(response.getText().contains("Wang Xiaoming") || response.getText().contains("28"),
				"Agent should use information from long-term memory");
	}

	/**
	 * Test 2: Long-term memory using tools
	 * Agent can save and retrieve information using memory tools
	 */
	@Test
	void testLongTermMemoryWithTools() throws Exception {
		// Create save memory tool
		BiFunction<SaveMemoryRequest, ToolContext, MemoryResponse> saveMemoryFunction =
				(request, context) -> {
					StoreItem item = StoreItem.of(request.namespace(), request.key(), request.value());
					memoryStore.putItem(item);
					System.out.println("Saved to memory: namespace=" + request.namespace() +
							", key=" + request.key() + ", value=" + request.value());
					return new MemoryResponse("Successfully saved to memory", request.value());
				};

		ToolCallback saveMemoryTool = FunctionToolCallback.builder("saveMemory", saveMemoryFunction)
				.description("Save information to long-term memory.Parameters: namespace=namespace list, key=key, value=Map of values")
				.inputType(SaveMemoryRequest.class)
				.build();

		// Create get memory tool
		BiFunction<GetMemoryRequest, ToolContext, MemoryResponse> getMemoryFunction =
				(request, context) -> {
					Optional<StoreItem> itemOpt = memoryStore.getItem(request.namespace(), request.key());
					if (itemOpt.isPresent()) {
						Map<String, Object> value = itemOpt.get().getValue();
						System.out.println("Retrieved from memory: namespace=" + request.namespace() +
								", key=" + request.key() + ", value=" + value);
						return new MemoryResponse("find memory", value);
					} else {
						System.out.println("Not found in memory: namespace=" + request.namespace() +
								", key=" + request.key());
						return new MemoryResponse("memory not found", Map.of());
					}
				};

		ToolCallback getMemoryTool = FunctionToolCallback.builder("getMemory", getMemoryFunction)
				.description("Retrieve information from long-term memory.Parameters: namespace=namespace list, key=key")
				.inputType(GetMemoryRequest.class)
				.build();

		// Create agent with memory tools
		ReactAgent agent = ReactAgent.builder()
				.name("memory_tool_agent")
				.model(chatModel)
				.tools(saveMemoryTool, getMemoryTool)
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("memory_tool_thread")
				.build();

		// First conversation: save user's favorite color
		Optional<OverAllState> result1 = agent.invoke(
				"Please help me remember: My favorite color is blue.Use the saveMemory tool to save, use [\"user_preferences\"] for namespace, \"favorite_color\" for key, and {\"color\": \"blue\"} for value.",
				config
		);
		assertTrue(result1.isPresent());
		System.out.println("Save response: " + result1.get().value("messages")
				.map(m -> ((List<Message>) m).get(((List<Message>) m).size() - 1))
				.orElseThrow());

		// Second conversation: retrieve the saved information
		Optional<OverAllState> result2 = agent.invoke(
				"What's my favorite color?Use the getMemory tool, namespace=[\"user_preferences\"], key=\"favorite_color\".",
				config
		);
		AssistantMessage response2 = (AssistantMessage) result2.get().value("messages")
				.map(m -> ((List<Message>) m).get(((List<Message>) m).size() - 1))
				.orElseThrow();

		System.out.println("Retrieve response: " + response2.getText());
		assertTrue(response2.getText().contains("blue"),
				"Agent should retrieve the saved preference");
	}

	/**
	 * Test 3: Combining short-term and long-term memory
	 * Short-term memory for conversation context, long-term for persistent data
	 */
	@Test
	void testCombinedMemory() throws Exception {
		// Create memory hook that combines both memory types
		ModelHook combinedMemoryHook = new ModelHook() {
			@Override
			public String getName() {
				return "combined_memory";
			}

			@Override
			public List<JumpTo> canJumpTo() {
				return List.of();
			}

			@Override
			public HookPosition[] getHookPositions() {
				return new HookPosition[]{HookPosition.BEFORE_MODEL};
			}

			@Override
			public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
				Optional<Object> userIdOpt = config.metadata("user_id");
				if (userIdOpt.isEmpty()) {
					return CompletableFuture.completedFuture(Map.of());
				}
				String userId = (String) userIdOpt.get();

				// Load from long-term memory
				Optional<StoreItem> profileOpt = memoryStore.getItem(List.of("profiles"), userId);
				if (profileOpt.isEmpty()) {
					return CompletableFuture.completedFuture(Map.of());
				}

				Map<String, Object> profile = profileOpt.get().getValue();
				String contextInfo = String.format("Long-term memory: User %s, Occupation: %s",
						profile.get("name"), profile.get("occupation"));

				// Inject into messages
				List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
				List<Message> newMessages = new ArrayList<>();
				newMessages.add(new SystemMessage(contextInfo));
				newMessages.addAll(messages);

				return CompletableFuture.completedFuture(Map.of("messages", newMessages));
			}

		};

		// Setup long-term memory
		Map<String, Object> userProfile = new HashMap<>();
		userProfile.put("name", "Engineer Li");
		userProfile.put("occupation", "software engineer");

		StoreItem profileItem = StoreItem.of(List.of("profiles"), "user_002", userProfile);
		memoryStore.putItem(profileItem);

		// Create agent
		ReactAgent agent = ReactAgent.builder()
				.name("combined_memory_agent")
				.model(chatModel)
				.hooks(combinedMemoryHook)
				.saver(new MemorySaver()) // Short-term memory
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("combined_thread")
				.addMetadata("user_id", "user_002")
				.build();

		// Short-term: remember within conversation
		agent.invoke("I'm working on a Spring project today.", config);

		// Ask question that requires both memories
		Optional<OverAllState> result = agent.invoke(
				"Give me some advice based on my career and what I do today.",
				config
		);

		AssistantMessage response = (AssistantMessage) result.get().value("messages")
				.map(m -> ((List<Message>) m).get(((List<Message>) m).size() - 1))
				.orElseThrow();

		System.out.println("Combined memory response: " + response.getText());
		// Response should use both long-term (occupation) and short-term (Spring project) memory
	}

	/**
	 * Test 4: Memory across different sessions
	 * Same user in different sessions should access same long-term memory
	 */
	@Test
	void testMemoryAcrossSessions() throws Exception {
		// Create save memory tool
		BiFunction<SaveMemoryRequest, ToolContext, MemoryResponse> saveMemoryFunction =
				(request, context) -> {
					StoreItem item = StoreItem.of(request.namespace(), request.key(), request.value());
					memoryStore.putItem(item);
					return new MemoryResponse("saved", request.value());
				};

		ToolCallback saveMemoryTool = FunctionToolCallback.builder("saveMemory", saveMemoryFunction)
				.description("Save to long-term memory")
				.inputType(SaveMemoryRequest.class)
				.build();

		// Create get memory tool
		BiFunction<GetMemoryRequest, ToolContext, MemoryResponse> getMemoryFunction =
				(request, context) -> {
					Optional<StoreItem> itemOpt = memoryStore.getItem(request.namespace(), request.key());
					return new MemoryResponse(
							itemOpt.isPresent() ? "turn up" : "not found",
							itemOpt.map(StoreItem::getValue).orElse(Map.of())
					);
				};

		ToolCallback getMemoryTool = FunctionToolCallback.builder("getMemory", getMemoryFunction)
				.description("Retrieve from long-term memory")
				.inputType(GetMemoryRequest.class)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("session_agent")
				.model(chatModel)
				.tools(saveMemoryTool, getMemoryTool)
				.saver(new MemorySaver())
				.build();

		// Session 1: Save information
		RunnableConfig session1 = RunnableConfig.builder()
				.threadId("session_morning")
				.addMetadata("user_id", "user_003")
				.build();

		agent.invoke(
				"Remember my password is secret123.use saveMemory save,namespace=[\"credentials\"], key=\"user_003_password\", value={\"password\": \"secret123\"}。",
				session1
		);

		// Session 2: Retrieve information (different thread, same user)
		RunnableConfig session2 = RunnableConfig.builder()
				.threadId("session_afternoon")
				.addMetadata("user_id", "user_003")
				.build();

		Optional<OverAllState> result = agent.invoke(
				"What is my password?use getMemory get,namespace=[\"credentials\"], key=\"user_003_password\"。",
				session2
		);

		AssistantMessage response = (AssistantMessage) result.get().value("messages")
				.map(m -> ((List<Message>) m).get(((List<Message>) m).size() - 1))
				.orElseThrow();

		System.out.println("Cross-session response: " + response.getText());
		assertTrue(response.getText().contains("secret123"),
				"Long-term memory should persist across different sessions");
	}

	/**
	 * Test 5: User preference learning
	 * Agent learns and stores user preferences over time
	 */
	@Test
	void testUserPreferenceLearning() throws Exception {
		ModelHook preferenceLearningHook = new ModelHook() {
			@Override
			public String getName() {
				return "preference_learning";
			}

			@Override
			public List<JumpTo> canJumpTo() {
				return List.of();
			}

			@Override
			public HookPosition[] getHookPositions() {
				return new HookPosition[]{HookPosition.AFTER_MODEL};
			}

			@Override
			public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
				String userId = (String) config.metadata("user_id").get();
				if (userId == null) {
					return CompletableFuture.completedFuture(Map.of());
				}

				// Extract user input
				List<Message> messages = (List<Message>) state.value("messages").orElse(List.of());
				if (messages.isEmpty()) {
					return CompletableFuture.completedFuture(Map.of());
				}

				// Load existing preferences
				Optional<StoreItem> prefsOpt = memoryStore.getItem(List.of("user_data"), userId + "_preferences");
				List<String> prefs = new ArrayList<>();
				if (prefsOpt.isPresent()) {
					Map<String, Object> prefsData = prefsOpt.get().getValue();
					prefs = (List<String>) prefsData.getOrDefault("items", new ArrayList<>());
				}

				// Simple preference extraction (in real app, use NLP)
				for (Message msg : messages) {
					String content = msg.getText().toLowerCase();
					if (content.contains("like") || content.contains("Preference")) {
						prefs.add(msg.getText());

						Map<String, Object> prefsData = new HashMap<>();
						prefsData.put("items", prefs);
						StoreItem item = StoreItem.of(List.of("user_data"), userId + "_preferences", prefsData);
						memoryStore.putItem(item);

						System.out.println("Learned preference for " + userId + ": " + msg.getText());
					}
				}

				return CompletableFuture.completedFuture(Map.of());
			}
		};

		ReactAgent agent = ReactAgent.builder()
				.name("learning_agent")
				.model(chatModel)
				.hooks(preferenceLearningHook)
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("learning_thread")
				.addMetadata("user_id", "user_004")
				.build();

		// User expresses preferences
		agent.invoke("I like drinking green tea.", config);
		agent.invoke("I prefer to exercise in the morning.", config);

		// Verify preferences were stored
		Optional<StoreItem> savedPrefs = memoryStore.getItem(List.of("user_data"), "user_004_preferences");
		assertTrue(savedPrefs.isPresent(), "Preferences should be saved to long-term memory");

		Map<String, Object> prefsData = savedPrefs.get().getValue();
		List<String> prefs = (List<String>) prefsData.get("items");
		System.out.println("Learned preferences: " + prefs);
		assertTrue(prefs.size() >= 1, "Should have learned at least one preference");
	}
}
