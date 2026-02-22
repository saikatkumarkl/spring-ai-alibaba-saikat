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
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;
import com.alibaba.cloud.ai.graph.store.Store;
import com.alibaba.cloud.ai.graph.store.StoreItem;
import com.alibaba.cloud.ai.graph.store.stores.MemoryStore;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;

/**
 * Memory management (Memory) example
 *
 * Demonstrate how to use memory management functions in Agent, including:
 * 1. Read long-term memory in the tool
 * 2. Write long-term memory in the tool
 * 3. Use ModelHook to manage long-term memory
 * 4. Combine short-term and long-term memory
 * 5. Cross-session memory
 * 6. User preference learning
 *
 * Reference documentation: advanced_doc/memory.md
 */
public class MemoryExample {

	private final ChatModel chatModel;

	public MemoryExample(ChatModel chatModel) {
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
		MemoryExample example = new MemoryExample(chatModel);

		//Run all examples
		example.runAllExamples();
	}

	private static void mockInsertToStore(MemoryStore store) {
		//Writing sample data to storage
		Map<String, Object> userData = new HashMap<>();
		userData.put("name", "Zhang San");
		userData.put("language", "Chinese");

		StoreItem userItem = StoreItem.of(List.of("users"), "user_123", userData);
		store.putItem(userItem);
	}

	/**
	 * Example 1: Reading long-term memory in a tool
	 *
	 * Create a tool that allows Agent to query user information
	 */
	public void example1_readMemoryInTool() throws GraphRunnerException {
		//Define request and response records
		record GetMemoryRequest(List<String> namespace, String key) { }
		record MemoryResponse(String message, Map<String, Object> value) { }

		//Create tools to obtain user information
		BiFunction<GetMemoryRequest, ToolContext, MemoryResponse> getUserInfoFunction =
				(request, context) -> {
					RunnableConfig runnableConfig = (RunnableConfig) context.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
					Store store = runnableConfig.store();
					Optional<StoreItem> itemOpt = store.getItem(request.namespace(), request.key());
					if (itemOpt.isPresent()) {
						Map<String, Object> value = itemOpt.get().getValue();
						return new MemoryResponse("Find user information", value);
					}
					return new MemoryResponse("User not found", Map.of());
				};

		ToolCallback getUserInfoTool = FunctionToolCallback.builder("getUserInfo", getUserInfoFunction)
				.description("Query user information")
				.inputType(GetMemoryRequest.class)
				.build();

		//CreateAgent
		ReactAgent agent = ReactAgent.builder()
				.name("memory_agent")
				.model(chatModel)
				.tools(getUserInfoTool)
				.saver(new MemorySaver())
				.build();


		//Create memory storage
		MemoryStore store = new MemoryStore();
		//Put simulated data in the Store. In actual applications, the storage may be generated in other processes.
		mockInsertToStore(store);
		//RunAgent
		RunnableConfig config = RunnableConfig.builder()
				.threadId("session_001")
				.addMetadata("user_id", "user_123")
				.store(store)
				.build();

		agent.invoke("Query user information,namespace=['users'], key='user_123'", config);

		System.out.println("Tool reading long-term memory example execution completed");
	}

	/**
	 * Example 2: Writing long-term memory in tools
	 *
	 * Create a tool to update user information
	 */
	public void example2_writeMemoryInTool() throws GraphRunnerException {
		//Define request records
		record SaveMemoryRequest(List<String> namespace, String key, Map<String, Object> value) { }
		record MemoryResponse(String message, Map<String, Object> value) { }

		//Create a tool to save user information
		BiFunction<SaveMemoryRequest, ToolContext, MemoryResponse> saveUserInfoFunction =
				(request, context) -> {
					RunnableConfig runnableConfig = (RunnableConfig) context.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
					Store store = runnableConfig.store();
					StoreItem item = StoreItem.of(request.namespace(), request.key(), request.value());
					store.putItem(item);
					return new MemoryResponse("User information saved successfully", request.value());
				};

		ToolCallback saveUserInfoTool = FunctionToolCallback.builder("saveUserInfo", saveUserInfoFunction)
				.description("Save user information")
				.inputType(SaveMemoryRequest.class)
				.build();

		//CreateAgent
		ReactAgent agent = ReactAgent.builder()
				.name("save_memory_agent")
				.model(chatModel)
				.tools(saveUserInfoTool)
				.saver(new MemorySaver())
				.build();

		//Create memory storage
		MemoryStore store = new MemoryStore();
		RunnableConfig config = RunnableConfig.builder()
				.threadId("session_001")
				.addMetadata("user_id", "user_123")
				.store(store)
				.build();
		//RunAgent
		agent.invoke(
				"My name is Zhang San, please save my information.Use the saveUserInfo tool, namespace=['users'], key='user_123', value={'name': 'Zhang San'}",
				config
		);

		//You can directly access the storage to get the value
		Optional<StoreItem> savedItem = store.getItem(List.of("users"), "user_123");
		if (savedItem.isPresent()) {
			Map<String, Object> savedValue = savedItem.get().getValue();
			System.out.println("Saved data:" + savedValue);
		}

		System.out.println("Tools write long-term memory example execution completed");
	}

	/**
	 * Example 3: Using MessagesModelHook to manage long-term memory
	 *
	 * Automatically load and save long-term memory before and after model calls
	 */
	public void example3_memoryWithModelHook() throws GraphRunnerException {
		//Create a memory interceptor
		@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
		class MemoryInterceptor extends MessagesModelHook {
			@Override
			public String getName() {
				return "memory_interceptor";
			}

			@Override
			public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
				//Get user ID from configuration
				String userId = (String) config.metadata("user_id").orElse(null);
				if (userId == null) {
					return new AgentCommand(previousMessages);
				}

				Store store = config.store();
				//Load user portrait from memory storage
				Optional<StoreItem> itemOpt = store.getItem(List.of("user_profiles"), userId);
				if (itemOpt.isPresent()) {
					Map<String, Object> profile = itemOpt.get().getValue();

					//Inject user context into system messages
					String userContext = String.format(
							"User information: name=%s, age=%s, email=%s, preference=%s",
							profile.get("name"),
							profile.get("age"),
							profile.get("email"),
							profile.get("preferences")
					);

					//Find if SystemMessage already exists
					SystemMessage existingSystemMessage = null;
					int systemMessageIndex = -1;
					for (int i = 0; i < previousMessages.size(); i++) {
						Message msg = previousMessages.get(i);
						if (msg instanceof SystemMessage) {
							existingSystemMessage = (SystemMessage) msg;
							systemMessageIndex = i;
							break;
						}
					}

					//If SystemMessage is found, update it; otherwise create a new one
					SystemMessage enhancedSystemMessage;
					if (existingSystemMessage != null) {
						//Update existing SystemMessage
						enhancedSystemMessage = new SystemMessage(
								existingSystemMessage.getText() + "\n\n" + userContext
						);
					}
					else {
						//Create new SystemMessage
						enhancedSystemMessage = new SystemMessage(userContext);
					}

					//Build a new message list
					List<Message> newMessages = new ArrayList<>();
					if (systemMessageIndex >= 0) {
						//If SystemMessage is found, replace it
						for (int i = 0; i < previousMessages.size(); i++) {
							if (i == systemMessageIndex) {
								newMessages.add(enhancedSystemMessage);
							}
							else {
								newMessages.add(previousMessages.get(i));
							}
						}
					}
					else {
						//If SystemMessage is not found, add a new one at the beginning
						newMessages.add(enhancedSystemMessage);
						newMessages.addAll(previousMessages);
					}

					//Replace all messages using REPLACE policy
					return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
				}

				return new AgentCommand(previousMessages);
			}

			@Override
			public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
				//The memory saving logic after the dialogue can be implemented here
				//Do not modify the message, return the original message
				return new AgentCommand(previousMessages);
			}
		}

		MessagesModelHook memoryInterceptor = new MemoryInterceptor();

		//Create an Agent with a memory interceptor
		ReactAgent agent = ReactAgent.builder()
				.name("memory_agent")
				.model(chatModel)
				.hooks(memoryInterceptor)
				.saver(new MemorySaver())
				.build();


		//Create memory storage
		MemoryStore memoryStore = new MemoryStore();

		//Simulate data and pre-populate user portraits
		Map<String, Object> profileData = new HashMap<>();
		profileData.put("name", "Wang Xiaoming");
		profileData.put("age", 28);
		profileData.put("email", "wang@example.com");
		profileData.put("preferences", List.of("like coffee", "like reading"));

		StoreItem profileItem = StoreItem.of(List.of("user_profiles"), "user_001", profileData);
		memoryStore.putItem(profileItem);
		RunnableConfig config = RunnableConfig.builder()
				.threadId("session_001")
				.addMetadata("user_id", "user_001")
				.store(memoryStore)
				.build();

		//Agent will automatically load user portrait information
		agent.invoke("Please give me some information.", config);

		System.out.println("ModelHook management long-term memory example execution completed");
	}

	/**
	 * Example 4: Combining short-term and long-term memory
	 *
	 * Short-term memory is used to store conversation context, and long-term memory is used to store persistent data.
	 * Implemented using MessagesModelHook
	 */
	public void example4_combinedMemory() throws GraphRunnerException {
		//Create a combined memory Hook
		@HookPositions({HookPosition.BEFORE_MODEL})
		class CombinedMemoryHook extends MessagesModelHook {
			@Override
			public String getName() {
				return "combined_memory";
			}

			@Override
			public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
				Optional<Object> userIdOpt = config.metadata("user_id");
				if (userIdOpt.isEmpty()) {
					return new AgentCommand(previousMessages);
				}
				String userId = (String) userIdOpt.get();

				Store memoryStore = config.store();
				//Loading from long-term memory
				Optional<StoreItem> profileOpt = memoryStore.getItem(List.of("profiles"), userId);
				if (profileOpt.isEmpty()) {
					return new AgentCommand(previousMessages);
				}

				Map<String, Object> profile = profileOpt.get().getValue();
				String contextInfo = String.format("Long-term memory: User %s, Occupation: %s",
						profile.get("name"), profile.get("occupation"));

				//Find if SystemMessage already exists
				SystemMessage existingSystemMessage = null;
				int systemMessageIndex = -1;
				for (int i = 0; i < previousMessages.size(); i++) {
					Message msg = previousMessages.get(i);
					if (msg instanceof SystemMessage) {
						existingSystemMessage = (SystemMessage) msg;
						systemMessageIndex = i;
						break;
					}
				}

				//If SystemMessage is found, update it; otherwise create a new one
				SystemMessage enhancedSystemMessage;
				if (existingSystemMessage != null) {
					//Update existing SystemMessage
					enhancedSystemMessage = new SystemMessage(
							existingSystemMessage.getText() + "\n\n" + contextInfo
					);
				}
				else {
					//Create new SystemMessage
					enhancedSystemMessage = new SystemMessage(contextInfo);
				}

				//Build a new message list
				List<Message> newMessages = new ArrayList<>();
				if (systemMessageIndex >= 0) {
					//If SystemMessage is found, replace it
					for (int i = 0; i < previousMessages.size(); i++) {
						if (i == systemMessageIndex) {
							newMessages.add(enhancedSystemMessage);
						}
						else {
							newMessages.add(previousMessages.get(i));
						}
					}
				}
				else {
					//If SystemMessage is not found, add a new one at the beginning
					newMessages.add(enhancedSystemMessage);
					newMessages.addAll(previousMessages);
				}

				//Replace all messages using REPLACE policy
				return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
			}
		}

		MessagesModelHook combinedMemoryHook = new CombinedMemoryHook();

		//CreateAgent
		ReactAgent agent = ReactAgent.builder()
				.name("combined_memory_agent")
				.model(chatModel)
				.hooks(combinedMemoryHook)
				.saver(new MemorySaver()) //short term memory
				.build();

		//Create memory storage
		MemoryStore memoryStore = new MemoryStore();
		//Set up long-term memory
		Map<String, Object> userProfile = new HashMap<>();
		userProfile.put("name", "Engineer Li");
		userProfile.put("occupation", "software engineer");
		StoreItem profileItem = StoreItem.of(List.of("profiles"), "user_002", userProfile);
		memoryStore.putItem(profileItem);

		RunnableConfig config = RunnableConfig.builder()
				.threadId("combined_thread")
				.addMetadata("user_id", "user_002")
				.store(memoryStore)
				.build();

		//Short-term memory: remembering during conversations
		agent.invoke("I'm working on a Spring project today.", config);

		//Asking questions that require the use of both memories
		agent.invoke("Give me some advice based on my career and what I do today.", config);
		//Response uses both long-term memory (career) and short-term memory (Spring project)

		System.out.println("Completed by combining short-term and long-term memory examples");
	}

	/**
	 * Example 5: Memory across sessions
	 *
	 * The same user should be able to access the same long-term memory in different sessions
	 */
	public void example5_crossSessionMemory() throws GraphRunnerException {
		record SaveMemoryRequest(List<String> namespace, String key, Map<String, Object> value) { }
		record GetMemoryRequest(List<String> namespace, String key) { }
		record MemoryResponse(String message, Map<String, Object> value) { }


		ToolCallback saveMemoryTool = FunctionToolCallback.builder("saveMemory",
						(BiFunction<SaveMemoryRequest, ToolContext, MemoryResponse>) (request, context) -> {
							StoreItem item = StoreItem.of(request.namespace(), request.key(), request.value());
							RunnableConfig runnableConfig = (RunnableConfig) context.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
							Store memoryStore = runnableConfig.store();
							memoryStore.putItem(item);
							return new MemoryResponse("saved", request.value());
						})
				.description("Save to long-term memory")
				.inputType(SaveMemoryRequest.class)
				.build();

		ToolCallback getMemoryTool = FunctionToolCallback.builder("getMemory",
						(BiFunction<GetMemoryRequest, ToolContext, MemoryResponse>) (request, context) -> {
							RunnableConfig runnableConfig = (RunnableConfig) context.getContext().get(AGENT_CONFIG_CONTEXT_KEY);
							Store memoryStore = runnableConfig.store();
							Optional<StoreItem> itemOpt = memoryStore.getItem(request.namespace(), request.key());
							return new MemoryResponse(
									itemOpt.isPresent() ? "turn up" : "not found",
									itemOpt.map(StoreItem::getValue).orElse(Map.of())
							);
						})
				.description("Retrieve from long-term memory")
				.inputType(GetMemoryRequest.class)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("session_agent")
				.model(chatModel)
				.tools(saveMemoryTool, getMemoryTool)
				.saver(new MemorySaver())
				.build();

		//Create memory stores and tools
		MemoryStore memoryStore = new MemoryStore();
		//Session 1: Save information
		RunnableConfig session1 = RunnableConfig.builder()
				.threadId("session_morning")
				.addMetadata("user_id", "user_003")
				.store(memoryStore)
				.build();

		agent.invoke(
				"Remember my password is secret123.use saveMemory save,namespace=['credentials'], key='user_003_password', value={'password': 'secret123'}。",
				session1
		);

		//Session 2: Retrieving information (different thread, same user)
		RunnableConfig session2 = RunnableConfig.builder()
				.threadId("session_afternoon")
				.addMetadata("user_id", "user_003")
				.store(memoryStore)
				.build();

		agent.invoke(
				"What is my password?use getMemory get,namespace=['credentials'], key='user_003_password'。",
				session2
		);
		//Long-term memory persists across sessions

		System.out.println("Cross-session memory example execution completed");
	}

	/**
	 * Example 6: User preference learning
	 *
	 * Agents can learn and store user preferences over time
	 * Implemented using MessagesModelHook
	 */
	public void example6_preferLearning() throws GraphRunnerException {
		MemoryStore memoryStore = new MemoryStore();

		@HookPositions({HookPosition.AFTER_MODEL})
		class PreferenceLearningHook extends MessagesModelHook {
			private final MemoryStore store;

			public PreferenceLearningHook(MemoryStore store) {
				this.store = store;
			}

			@Override
			public String getName() {
				return "preference_learning";
			}

			@Override
			public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
				String userId = (String) config.metadata("user_id").orElse(null);
				if (userId == null) {
					return new AgentCommand(previousMessages);
				}

				//Extract user input
				if (previousMessages.isEmpty()) {
					return new AgentCommand(previousMessages);
				}

				//Load existing preferences
				Optional<StoreItem> prefsOpt = store.getItem(List.of("user_data"), userId + "_preferences");
				List<String> prefs = new ArrayList<>();
				if (prefsOpt.isPresent()) {
					Map<String, Object> prefsData = prefsOpt.get().getValue();
					prefs = (List<String>) prefsData.getOrDefault("items", new ArrayList<>());
				}

				//Simple preference extraction (using NLP in practical applications)
				for (Message msg : previousMessages) {
					String content = msg.getText().toLowerCase();
					if (content.contains("like") || content.contains("Preference")) {
						prefs.add(msg.getText());

						Map<String, Object> prefsData = new HashMap<>();
						prefsData.put("items", prefs);
						StoreItem item = StoreItem.of(List.of("user_data"), userId + "_preferences", prefsData);
						store.putItem(item);

						System.out.println("Learn user preferences" + userId + ": " + msg.getText());
					}
				}

				//Do not modify the message, return the original message
				return new AgentCommand(previousMessages);
			}
		}

		MessagesModelHook preferenceLearningHook = new PreferenceLearningHook(memoryStore);

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

		//User expressed preference
		agent.invoke("I like drinking green tea.", config);
		agent.invoke("I prefer to exercise in the morning.", config);

		//Verification preferences have been stored
		Optional<StoreItem> savedPrefs = memoryStore.getItem(List.of("user_data"), "user_004_preferences");
		if (savedPrefs.isPresent()) {
			System.out.println("Saved preferences:" + savedPrefs.get().getValue());
		}

		System.out.println("User preference learning example execution completed");
	}

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("=== Memory management example ===\n");

		try {
			System.out.println("Example 1: Reading long-term memory in a tool");
			example1_readMemoryInTool();
			System.out.println();

			System.out.println("Example 2: Writing long-term memory in a tool");
			example2_writeMemoryInTool();
			System.out.println();

			System.out.println("Example 3: Using ModelHook to manage long-term memory");
			example3_memoryWithModelHook();
			System.out.println();

			System.out.println("Example 4: Combining short-term and long-term memory");
			example4_combinedMemory();
			System.out.println();

			System.out.println("Example 5: Cross-session memory");
			example5_crossSessionMemory();
			System.out.println();

			System.out.println("Example 6: User preference learning");
			example6_preferLearning();
			System.out.println();

		}
		catch (Exception e) {
			System.err.println("An error occurred while executing the example:" + e.getMessage());
			e.printStackTrace();
		}
	}
}

