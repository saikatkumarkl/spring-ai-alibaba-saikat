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
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Context Engineering Example
 *
 * Demonstrates how to improve Agent reliability through context engineering, including:
 * 1. Model context: system prompts, message history, tools, model selection, response format
 * 2. Tool context: tool access and modification status
 * 3. Life cycle context: Hook mechanism
 *
 * Reference documentation: advanced_doc/context-engineering.md
 */
public class ContextEngineeringExample {

	private final ChatModel chatModel;

	public ContextEngineeringExample(ChatModel chatModel) {
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
		ContextEngineeringExample example = new ContextEngineeringExample(chatModel);

		//Run all examples
		example.runAllExamples();
	}

	/**
	 * Example 1: Dynamic prompts based on status
	 *
	 * Adjust system prompts based on conversation length
	 */
	public void example1_stateAwarePrompt() throws GraphRunnerException {
		//Create a model interceptor that adjusts system prompts based on conversation length
		class StateAwarePromptInterceptor extends ModelInterceptor {
			@Override
			public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
				List<Message> messages = request.getMessages();
				int messageCount = messages.size();

				//Basic tips
				String basePrompt = "You are a useful assistant.";

				//Adjust prompts based on number of messages
				if (messageCount > 10) {
					basePrompt += "\nThis is a long conversation - please try to keep it precise and concise.";
				}

				//Update system messages (refer to the implementation of TodoListInterceptor)
				SystemMessage enhancedSystemMessage;
				if (request.getSystemMessage() == null) {
					enhancedSystemMessage = new SystemMessage(basePrompt);
				}
				else {
					enhancedSystemMessage = new SystemMessage(
							request.getSystemMessage().getText() + "\n\n" + basePrompt
					);
				}

				//Create enhanced request
				ModelRequest enhancedRequest = ModelRequest.builder(request)
						.systemMessage(enhancedSystemMessage)
						.build();

				//call handler
				return handler.call(enhancedRequest);
			}

			@Override
			public String getName() {
				return "StateAwarePromptInterceptor";
			}
		}

		//Create Agent using interceptor
		ReactAgent agent = ReactAgent.builder()
				.name("context_aware_agent")
				.model(chatModel)
				.interceptors(new StateAwarePromptInterceptor())
				.build();

		//test
		agent.invoke("Hello");
		System.out.println("Status-based dynamic prompt example execution completed");
	}

	/**
	 * Example 2: Storage-based personalized prompts
	 *
	 * Load user preferences from long-term memory and generate personalized prompts
	 */
	public void example2_personalizedPrompt() throws GraphRunnerException {
		//User preference class
		class UserPreferences {
			private String communicationStyle;
			private String language;
			private List<String> interests;

			public UserPreferences(String style, String lang, List<String> interests) {
				this.communicationStyle = style;
				this.language = lang;
				this.interests = interests;
			}

			public String getCommunicationStyle() {
				return communicationStyle;
			}

			public String getLanguage() {
				return language;
			}

			public List<String> getInterests() {
				return interests;
			}
		}

		//Simple user preference storage
		class UserPreferenceStore {
			private Map<String, UserPreferences> store = new HashMap<>();

			public UserPreferences getPreferences(String userId) {
				return store.getOrDefault(userId,
						new UserPreferences("major", "Chinese", List.of()));
			}

			public void savePreferences(String userId, UserPreferences prefs) {
				store.put(userId, prefs);
			}
		}

		UserPreferenceStore store = new UserPreferenceStore();
		store.savePreferences("user_001",
				new UserPreferences("Friendly and relaxed", "Chinese", List.of("technology", "read")));

		//Load user preferences from long-term memory
		class PersonalizedPromptInterceptor extends ModelInterceptor {
			private final UserPreferenceStore store;

			public PersonalizedPromptInterceptor(UserPreferenceStore store) {
				this.store = store;
			}

			@Override
			public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
				//Get user ID from runtime context
				String userId = getUserIdFromContext(request);

				//Load user preferences from storage
				UserPreferences prefs = store.getPreferences(userId);

				//Build personalized prompts
				String personalizedPrompt = buildPersonalizedPrompt(prefs);

				//Update system messages (refer to the implementation of TodoListInterceptor)
				SystemMessage enhancedSystemMessage;
				if (request.getSystemMessage() == null) {
					enhancedSystemMessage = new SystemMessage(personalizedPrompt);
				}
				else {
					enhancedSystemMessage = new SystemMessage(
							request.getSystemMessage().getText() + "\n\n" + personalizedPrompt
					);
				}

				//Create enhanced request
				ModelRequest enhancedRequest = ModelRequest.builder(request)
						.systemMessage(enhancedSystemMessage)
						.build();

				//call handler
				return handler.call(enhancedRequest);
			}

			private String getUserIdFromContext(ModelRequest request) {
				//Extract user ID from request context
				return "user_001"; //Simplified example
			}

			private String buildPersonalizedPrompt(UserPreferences prefs) {
				StringBuilder prompt = new StringBuilder("You are a useful assistant.");

				if (prefs.getCommunicationStyle() != null) {
					prompt.append("\nCommunication style:").append(prefs.getCommunicationStyle());
				}

				if (prefs.getLanguage() != null) {
					prompt.append("\nUse language:").append(prefs.getLanguage());
				}

				if (!prefs.getInterests().isEmpty()) {
					prompt.append("\nUser interests:").append(String.join(", ", prefs.getInterests()));
				}

				return prompt.toString();
			}

			@Override
			public String getName() {
				return "PersonalizedPromptInterceptor";
			}
		}

		ReactAgent agent = ReactAgent.builder()
				.name("personalized_agent")
				.model(chatModel)
				.interceptors(new PersonalizedPromptInterceptor(store))
				.build();

		agent.invoke("Introduce the latest AI technology");
		System.out.println("Personalized prompt example execution completed");
	}

	/**
	 * Example 3: Message filtering
	 *
	 * Only keep the most recent N messages to avoid too long context
	 */
	public void example3_messageFilter() {
		class MessageFilterInterceptor extends ModelInterceptor {
			private final int maxMessages;

			public MessageFilterInterceptor(int maxMessages) {
				this.maxMessages = maxMessages;
			}

			@Override
			public ModelResponse interceptModel(ModelRequest request, ModelCallHandler next) {
				List<Message> messages = request.getMessages();

				//Only keep the latest N messages
				if (messages.size() > maxMessages) {
					List<Message> filtered = new ArrayList<>();

					//Add system message
					messages.stream()
							.filter(m -> m instanceof SystemMessage)
							.findFirst()
							.ifPresent(filtered::add);

					//Add recent message
					int startIndex = Math.max(0, messages.size() - maxMessages + 1);
					filtered.addAll(messages.subList(startIndex, messages.size()));

					messages = filtered;
				}

				ModelRequest updatedRequest = ModelRequest.builder(request)
						.messages(messages)
						.build();

				return next.call(updatedRequest);
			}

			@Override
			public String getName() {
				return "MessageFilterInterceptor";
			}
		}

		ReactAgent agent = ReactAgent.builder()
				.name("message_filter_agent")
				.model(chatModel)
				.interceptors(new MessageFilterInterceptor(10))
				.build();

		System.out.println("Message filtering example execution completed");
	}

	/**
	 * Example 4: Context-based tool selection
	 *
	 * Dynamically select available tools based on user role
	 */
	public void example4_contextualToolSelection() {
		class ContextualToolInterceptor extends ModelInterceptor {
			private final Map<String, List<ToolCallback>> roleBasedTools;

			public ContextualToolInterceptor(Map<String, List<ToolCallback>> roleBasedTools) {
				this.roleBasedTools = roleBasedTools;
			}

			@Override
			public ModelResponse interceptModel(ModelRequest request, ModelCallHandler next) {
				//Get user role from context
				String userRole = getUserRole(request);

				//Choose tools based on role
				List<ToolCallback> allowedTools = roleBasedTools.getOrDefault(
						userRole,
						Collections.emptyList()
				);

				//Update tool options (note: the actual implementation needs to be adjusted according to the framework API)
				//Conceptual code shown here
				System.out.println("for role" + userRole + "selected" + allowedTools.size() + "tools");

				return next.call(request);
			}

			private String getUserRole(ModelRequest request) {
				//Extract user role from request context
				return "user"; //Simplified example
			}

			@Override
			public String getName() {
				return "ContextualToolInterceptor";
			}
		}

		//Configuring role-based tools (example)
		Map<String, List<ToolCallback>> roleTools = Map.of(
				"admin", List.of(/* readTool, writeTool, deleteTool */),
				"user", List.of(/* readTool */),
				"guest", List.of()
		);

		ReactAgent agent = ReactAgent.builder()
				.name("role_based_agent")
				.model(chatModel)
				.interceptors(new ContextualToolInterceptor(roleTools))
				.build();

		System.out.println("Context-based tool selection example execution completed");
	}

	/**
	 * Example 5: Logging Hook
	 *
	 * Use MessagesModelHook to log before and after model calls
	 */
	public void example5_loggingHook() throws GraphRunnerException {
		@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
		class LoggingHook extends MessagesModelHook {
			@Override
			public String getName() {
				return "logging_hook";
			}

			@Override
			public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
				//Record before model call
				System.out.println("Before model call - number of messages:" + previousMessages.size());
				//Do not modify the message, return the original message
				return new AgentCommand(previousMessages);
			}

			@Override
			public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
				//Logging after model call
				System.out.println("After model call - response generated");
				//Do not modify the message, return the original message
				return new AgentCommand(previousMessages);
			}
		}

		//Use Hook
		ReactAgent agent = ReactAgent.builder()
				.name("logged_agent")
				.model(chatModel)
				.hooks(new LoggingHook())
				.build();

		agent.invoke("Test logging");
		System.out.println("Logging Hook example execution completed");
	}

	/**
	 * Example 6: Message Summary Hook
	 *
	 * Automatically generate summaries when conversations are too long
	 * Implemented using MessagesModelHook
	 */
	public void example6_summarizationHook() {
		@HookPositions({HookPosition.BEFORE_MODEL})
		class SummarizationHook extends MessagesModelHook {
			private final ChatModel summarizationModel;
			private final int triggerLength;

			public SummarizationHook(ChatModel model, int triggerLength) {
				this.summarizationModel = model;
				this.triggerLength = triggerLength;
			}

			@Override
			public String getName() {
				return "summarization_hook";
			}

			@Override
			public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
				if (previousMessages.size() <= triggerLength) {
					//If the number of messages does not exceed the threshold, no summary is needed
					return new AgentCommand(previousMessages);
				}

				//Generate conversation summary
				String summary = generateSummary(previousMessages);

				//Find if SystemMessage already exists
				SystemMessage existingSystemMessage = null;
				for (Message msg : previousMessages) {
					if (msg instanceof SystemMessage) {
						existingSystemMessage = (SystemMessage) msg;
						break;
					}
				}

				//Create summary SystemMessage
				String summaryText = "Summary of previous conversations:" + summary;
				SystemMessage summarySystemMessage;
				if (existingSystemMessage != null) {
					//If SystemMessage exists, append summary information
					summarySystemMessage = new SystemMessage(
							existingSystemMessage.getText() + "\n\n" + summaryText
					);
				}
				else {
					//If it does not exist, create a new one
					summarySystemMessage = new SystemMessage(summaryText);
				}

				//Keep the most recent messages
				int recentCount = Math.min(5, previousMessages.size());
				List<Message> recentMessages = previousMessages.subList(
						previousMessages.size() - recentCount,
						previousMessages.size()
				);

				//Build a new message list
				List<Message> newMessages = new ArrayList<>();
				newMessages.add(summarySystemMessage);
				//Add recent messages, excluding old SystemMessage if present
				for (Message msg : recentMessages) {
					if (msg != existingSystemMessage) {
						newMessages.add(msg);
					}
				}

				//Replace all messages using REPLACE policy
				return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
			}

			private String generateSummary(List<Message> messages) {
				//Use another model to generate a summary
				String conversation = messages.stream()
						.map(Message::getText)
						.collect(Collectors.joining("\n"));

				//Simplified example: return fixed summary
				return "Several topics have been discussed before...";
			}
		}

		ReactAgent agent = ReactAgent.builder()
				.name("summarizing_agent")
				.model(chatModel)
				.hooks(new SummarizationHook(chatModel, 20))
				.build();

		System.out.println("Message summary Hook example execution completed");
	}

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("=== Context Engineering Example ===\n");

		try {
			System.out.println("Example 1: Dynamic prompts based on status");
			example1_stateAwarePrompt();
			System.out.println();

			System.out.println("Example 2: Storage-based personalized prompts");
			example2_personalizedPrompt();
			System.out.println();

			System.out.println("Example 3: Message filtering");
			example3_messageFilter();
			System.out.println();

			System.out.println("Example 4: Context-based tool selection");
			example4_contextualToolSelection();
			System.out.println();

			System.out.println("Example 5: Logging Hook");
			example5_loggingHook();
			System.out.println();

			System.out.println("Example 6: Message Summary Hook");
			example6_summarizationHook();
			System.out.println();

		}
		catch (Exception e) {
			System.err.println("An error occurred while executing the example:" + e.getMessage());
			e.printStackTrace();
		}
	}
}

