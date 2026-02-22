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
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.tool.resolution.StaticToolCallbackResolver;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;

/**
 * Tools Tutorial - Complete Code Examples
 * Demonstrates how to create and use Tools to let Agent interact with external systems
 *
 * Source: tools.md
 */
public class ToolsExample {

	// ==================== Basic Tool Definitions ====================

	/**
	 * Example 1: Programmatic Specification - FunctionToolCallback
	 */
	public static void programmaticToolSpecification() {
		ToolCallback toolCallback = FunctionToolCallback
				.builder("currentWeather", new WeatherService())
				.description("Get the weather in location")
				.inputType(WeatherRequest.class)
				.build();
	}

	/**
	 * Example 2: Adding Tool to ChatClient (Using Programmatic Specification)
	 */
	public static void addToolToChatClient() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ToolCallback toolCallback = FunctionToolCallback
				.builder("currentWeather", new WeatherService())
				.description("Get the weather in location")
				.inputType(WeatherRequest.class)
				.build();

		// Note: ChatClient usage would be shown here in actual implementation
		// This is a simplified example
	}

	/**
	 * Example 3: Custom Tool Name
	 */
	public static void customToolName() {
		ToolCallback searchTool = FunctionToolCallback
				.builder("web_search", new SearchFunction())  // Custom name
				.description("Search the web for information")
				.inputType(String.class)
				.build();

		System.out.println(searchTool.getToolDefinition().name());  // web_search
	}

	/**
	 * Example 4: Custom Tool Description
	 */
	public static void customToolDescription() {
		ToolCallback calculatorTool = FunctionToolCallback
				.builder("calculator", new CalculatorFunction())
				.description("Performs arithmetic calculations. Use this for any math problems.")
				.inputType(String.class)
				.build();
	}

	/**
	 * Example 5: Advanced Schema Definition
	 */
	public static void advancedSchemaDefinition() {
		ToolCallback weatherTool = FunctionToolCallback
				.builder("get_weather", new WeatherFunction())
				.description("Get current weather and optional forecast")
				.inputType(WeatherInput.class)
				.build();
	}

	/**
	 * Example 6: Accessing State
	 */
	public static void accessingState() {
		// Create tools
		ToolCallback summaryTool = FunctionToolCallback
				.builder("summarize_conversation", new ConversationSummaryTool())
				.description("Summarize the conversation so far")
				.inputType(String.class)
				.build();
	}

	// ==================== Custom Tool Properties ====================

	/**
	 * Example 7: Accessing Context
	 */
	public static void accessingContext() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ToolCallback accountTool = FunctionToolCallback
				.builder("get_account_info", new AccountInfoTool())
				.description("Get the current user's account information")
				.inputType(String.class)
				.build();

		// Use in ReactAgent
		ReactAgent agent = ReactAgent.builder()
				.name("financial_assistant")
				.model(chatModel)
				.tools(accountTool)
				.systemPrompt("You are a financial assistant.")
				.build();

		// Pass context when calling
		RunnableConfig config = RunnableConfig.builder()
				.addMetadata("user_id", "user123")
				.build();

		agent.call("question", config);
	}

	/**
	 * Example 8: Accessing Persistent Data Across Conversations via Store
	 */
	public static void accessingMemoryStore() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Configure persistent storage
		MemorySaver memorySaver = new MemorySaver();

		// Create tools
		ToolCallback saveUserInfoTool = createSaveUserInfoTool();
		ToolCallback getUserInfoTool = createGetUserInfoTool();

		// Create Agent with persistent memory
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(saveUserInfoTool, getUserInfoTool)
				.saver(memorySaver)
				.build();

		// First session: save user info
		RunnableConfig config1 = RunnableConfig.builder()
				.threadId("session_1")
				.build();

		agent.call("Save user: userid: abc123, name: Foo, age: 25, email: foo@example.com", config1);

		// Second session: get user info, note using a different threadId
		RunnableConfig config2 = RunnableConfig.builder()
				.threadId("session_2")
				.build();

		agent.call("Get user info for user with id 'abc123'", config2);
	}

	/**
	 * Example 9: Using Tools in ReactAgent
	 */
	public static void toolsInReactAgent() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools
		ToolCallback weatherTool = FunctionToolCallback
				.builder("get_weather", new WeatherFunction())
				.description("Get weather for a given city")
				.inputType(WeatherInput.class)
				.build();

		ToolCallback searchTool = FunctionToolCallback
				.builder("search", new SearchFunction())
				.description("Search for information")
				.inputType(String.class)
				.build();

		// Create Agent with tools
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(weatherTool, searchTool)
				.systemPrompt("You are a helpful assistant with access to weather and search tools.")
				.saver(new MemorySaver())
				.build();

		// Use Agent
		AssistantMessage response = agent.call("What's the weather like in San Francisco?");
		System.out.println(response.getText());
	}

	/**
	 * Example 10: Complete Tool Usage Example (Using tools Method)
	 */
	public static void comprehensiveToolExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Define multiple tools
		ToolCallback weatherTool = FunctionToolCallback
				.builder("get_weather", new WeatherFunction())
				.description("Get current weather and optional forecast for a city")
				.inputType(WeatherInput.class)
				.build();

		ToolCallback calculatorTool = FunctionToolCallback
				.builder("calculator", new CalculatorFunction())
				.description("Perform arithmetic calculations")
				.inputType(String.class)
				.build();

		ToolCallback searchTool = FunctionToolCallback
				.builder("web_search", new SearchFunction())
				.description("Search the web for information")
				.inputType(String.class)
				.build();

		// Create Agent
		ReactAgent agent = ReactAgent.builder()
				.name("multi_tool_agent")
				.model(chatModel)
				.tools(weatherTool, calculatorTool, searchTool)
				.systemPrompt("""
						You are a helpful AI assistant with access to multiple tools:
						- Weather information
						- Calculator for math operations
						- Web search for general information
						
						Use the appropriate tool based on the user's question.
						""")
				.saver(new MemorySaver())
				.build();

		// Use different tools
		RunnableConfig config = RunnableConfig.builder()
				.threadId("session_1")
				.build();

		agent.call("What's the weather in New York?", config);
		agent.call("Calculate 25 * 4 + 10", config);
		agent.call("Search for latest AI news", config);
	}

	/**
	 * Example 11: Using methodTools - @Tool Annotation-based Method Tools
	 */
	public static void methodToolsExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tool object with @Tool annotated methods
		CalculatorTools calculatorTools = new CalculatorTools();

		// Use methodTools method, pass in object with @Tool annotated methods
		ReactAgent agent = ReactAgent.builder()
				.name("calculator_agent")
				.model(chatModel)
				.description("An agent that can perform calculations")
				.instruction("You are a helpful calculator assistant. Use the available tools to perform calculations.")
				.methodTools(calculatorTools)  // Pass in object with @Tool annotated methods
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("method_tools_session")
				.build();

		agent.call("What is 15 + 27?", config);
		agent.call("What is 8 * 9?", config);
	}

	/**
	 * Example 12: Using Multiple methodTools Objects
	 */
	public static void multipleMethodToolsExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create multiple tool objects
		CalculatorTools calculatorTools = new CalculatorTools();
		WeatherTools weatherTools = new WeatherTools();

		// Can pass in multiple methodTools objects
		ReactAgent agent = ReactAgent.builder()
				.name("multi_method_tool_agent")
				.model(chatModel)
				.description("An agent with multiple method-based tools")
				.instruction("You are a helpful assistant with calculator and weather tools.")
				.methodTools(calculatorTools, weatherTools)  // Pass in multiple tool objects
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("multi_method_tools_session")
				.build();

		agent.call("What is 10 * 8 and what's the weather in Beijing?", config);
	}

	/**
	 * Example 13: Using ToolCallbackProvider
	 */
	public static void toolCallbackProviderExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools
		ToolCallback searchTool = FunctionToolCallback.builder("search", new SearchToolWithContext())
				.description("Search for information")
				.inputType(String.class)
				.build();

		// Create ToolCallbackProvider
		ToolCallbackProvider toolProvider = new CustomToolCallbackProvider(List.of(searchTool));

		// Use toolCallbackProviders method
		ReactAgent agent = ReactAgent.builder()
				.name("search_agent")
				.model(chatModel)
				.description("An agent that can search for information")
				.instruction("You are a helpful assistant with search capabilities.")
				.toolCallbackProviders(toolProvider)  // Use ToolCallbackProvider
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("tool_provider_session")
				.build();

		agent.call("Search for information about Spring AI", config);
	}

	/**
	 * Example 14: Using toolNames and resolver (Must Be Used Together)
	 */
	public static void toolNamesWithResolverExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools (using composite types)
		ToolCallback searchTool = FunctionToolCallback.builder("search", new SearchFunctionWithRequest())
				.description("Search for information")
				.inputType(SearchRequest.class)
				.build();

		ToolCallback calculatorTool = FunctionToolCallback.builder("calculator", new CalculatorFunctionWithRequest())
				.description("Perform arithmetic calculations")
				.inputType(CalculatorRequest.class)
				.build();

		// Create StaticToolCallbackResolver containing all tools
		StaticToolCallbackResolver resolver = new StaticToolCallbackResolver(
				List.of(calculatorTool, searchTool));

		// Use toolNames to specify tool names, must be used with resolver
		ReactAgent agent = ReactAgent.builder()
				.name("multi_tool_agent")
				.model(chatModel)
				.description("An agent with multiple tools")
				.instruction("You are a helpful assistant with access to calculator and search tools.")
				.toolNames("calculator", "search")  // Use tool names instead of ToolCallback instances
				.resolver(resolver)  // Must provide resolver to resolve tool names
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("tool_names_session")
				.build();

		agent.call("Calculate 25 + 4 and then search for information about the result", config);
	}

	/**
	 * Example 15: Using resolver to Directly Resolve Tools
	 */
	public static void resolverExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools
		ToolCallback calculatorTool = FunctionToolCallback.builder("calculator", new CalculatorFunctionWithContext())
				.description("Perform arithmetic calculations")
				.inputType(String.class)
				.build();

		// Create resolver
		StaticToolCallbackResolver resolver = new StaticToolCallbackResolver(
				List.of(calculatorTool));

		// Use resolver, can be used directly in tools or provided only through resolver
		ReactAgent agent = ReactAgent.builder()
				.name("resolver_agent")
				.model(chatModel)
				.description("An agent using ToolCallbackResolver")
				.instruction("You are a helpful calculator assistant.")
				.tools(calculatorTool)  // Directly specify tools
				.resolver(resolver)  // Also set resolver for tool node usage
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("resolver_session")
				.build();

		agent.call("What is 100 divided by 4?", config);
	}

	/**
	 * Example 16: Combining Multiple Tool Provision Methods
	 */
	public static void combinedToolProvisionExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Method tools
		CalculatorTools calculatorTools = new CalculatorTools();

		// Direct tool
		ToolCallback searchTool = FunctionToolCallback.builder("search", new SearchToolWithContext())
				.description("Search for information")
				.inputType(String.class)
				.build();

		// ToolCallbackProvider
		ToolCallbackProvider toolProvider = new CustomToolCallbackProvider(List.of(searchTool));

		// Combine multiple methods
		ReactAgent agent = ReactAgent.builder()
				.name("combined_tool_agent")
				.model(chatModel)
				.description("An agent with multiple tool provision methods")
				.instruction("You are a helpful assistant with calculator and search capabilities.")
				.methodTools(calculatorTools)  // Method-based tools
				.toolCallbackProviders(toolProvider)  // Provider-based tools
				.tools(searchTool)  // Direct tools
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("combined_session")
				.build();

		agent.call("Calculate 50 + 75 and search for information about mathematics", config);
	}

	// ==================== Advanced Schema Definitions ====================

	/**
	 * Create Save User Info Tool
	 */
	private static ToolCallback createSaveUserInfoTool() {
		return FunctionToolCallback.builder("save_user_info", (String input) -> {
					// Simplified implementation
					return "User info saved: " + input;
				})
				.description("Save user information")
				.inputType(String.class)
				.build();
	}

	/**
	 * Create Get User Info Tool
	 */
	private static ToolCallback createGetUserInfoTool() {
		return FunctionToolCallback.builder("get_user_info", (String userId) -> {
					// Simplified implementation
					return "User info for: " + userId;
				})
				.description("Get user information by ID")
				.inputType(String.class)
				.build();
	}

	public static void main(String[] args) {
		System.out.println("=== Tools Tutorial Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			System.out.println("\n--- Example 1: Programmatic Tool Specification ---");
			programmaticToolSpecification();

			System.out.println("\n--- Example 2: Adding Tool to ChatClient ---");
			addToolToChatClient();

			System.out.println("\n--- Example 3: Custom Tool Name ---");
			customToolName();

			System.out.println("\n--- Example 4: Custom Tool Description ---");
			customToolDescription();

			System.out.println("\n--- Example 5: Advanced Schema Definition ---");
			advancedSchemaDefinition();

			System.out.println("\n--- Example 6: Accessing State ---");
			accessingState();

			System.out.println("\n--- Example 7: Accessing Context ---");
			accessingContext();

			System.out.println("\n--- Example 8: Accessing Memory Store ---");
			accessingMemoryStore();

			System.out.println("\n--- Example 9: Tools in ReactAgent ---");
			toolsInReactAgent();

			System.out.println("\n--- Example 10: Comprehensive Tool Example (tools Method) ---");
			comprehensiveToolExample();

			System.out.println("\n--- Example 11: Using methodTools (@Tool Annotation) ---");
			methodToolsExample();

			System.out.println("\n--- Example 12: Multiple methodTools Objects ---");
			multipleMethodToolsExample();

			System.out.println("\n--- Example 13: Using ToolCallbackProvider ---");
			toolCallbackProviderExample();

			System.out.println("\n--- Example 14: Using toolNames and resolver ---");
			toolNamesWithResolverExample();

			System.out.println("\n--- Example 15: Using resolver ---");
			resolverExample();

			System.out.println("\n--- Example 16: Combining Multiple Tool Provision Methods ---");
			combinedToolProvisionExample();

			System.out.println("\n=== All examples executed successfully ===");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public enum Unit {C, F}

	// ==================== Accessing Context ====================

	public enum UnitType {CELSIUS, FAHRENHEIT}

	/**
	 * Weather Service
	 */
	public static class WeatherService implements Function<WeatherRequest, WeatherResponse> {
		@Override
		public WeatherResponse apply(WeatherRequest request) {
			return new WeatherResponse(30.0, Unit.C);
		}
	}

	// ==================== Context ====================

	public record WeatherRequest(
			@ToolParam(description = "City or coordinates") String location,
			Unit unit
	) { }

	public record WeatherResponse(double temp, Unit unit) { }

	// ==================== Memory (Store) ====================

	/**
	 * Search Function
	 */
	public static class SearchFunction implements Function<String, String> {
		@Override
		public String apply(String query) {
			return "Search results for: " + query;
		}
	}

	// ==================== Using Tools in ReactAgent ====================

	/**
	 * Calculator Function
	 */
	public static class CalculatorFunction implements Function<String, String> {
		@Override
		public String apply(String expression) {
			// Simplified calculation logic
			return "Result: " + expression;
		}
	}

	// ==================== Complete Example ====================

	/**
	 * Weather Input (using Record class)
	 */
	public record WeatherInput(
			@ToolParam(description = "City name or coordinates") String location,
			@ToolParam(description = "Temperature unit preference") Unit units,
			@ToolParam(description = "Include 5-day forecast") boolean includeForecast
	) { }

	// ==================== Helper Methods ====================

	/**
	 * Weather Function (Advanced)
	 */
	public static class WeatherFunction implements Function<WeatherInput, String> {
		@Override
		public String apply(WeatherInput input) {
			double temp = input.units() == Unit.F ? 22 : 72;
			String result = String.format(
					"Current weather in %s: %.0f degrees %s",
					input.location(),
					temp,
					input.units().toString().substring(0, 1).toUpperCase()
			);

			if (input.includeForecast()) {
				result += "\nNext 5 days: Sunny";
			}

			return result;
		}
	}

	/**
	 * Conversation Summary Tool
	 */
	public static class ConversationSummaryTool implements BiFunction<String, ToolContext, String> {

		@Override
		public String apply(String input, ToolContext toolContext) {
			OverAllState state = (OverAllState) toolContext.getContext().get("state");
			RunnableConfig config = (RunnableConfig) toolContext.getContext().get("config");

			// Get messages from state
			Optional<Object> messagesOpt = state.value("messages");
			List<Message> messages = messagesOpt.isPresent()
					? (List<Message>) messagesOpt.get()
					: new ArrayList<>();

			if (messages.isEmpty()) {
				return "No conversation history available";
			}

			long userMsgs = messages.stream()
					.filter(m -> m.getMessageType().getValue().equals("user"))
					.count();
			long aiMsgs = messages.stream()
					.filter(m -> m.getMessageType().getValue().equals("assistant"))
					.count();
			long toolMsgs = messages.stream()
					.filter(m -> m.getMessageType().getValue().equals("tool"))
					.count();

			return String.format(
					"Conversation has %d user messages, %d AI responses, and %d tool results",
					userMsgs, aiMsgs, toolMsgs
			);
		}
	}

	// ==================== Main Method ====================

	/**
	 * Account Info Tool
	 */
	public static class AccountInfoTool implements BiFunction<String, ToolContext, String> {

		private static final Map<String, Map<String, Object>> USER_DATABASE = Map.of(
				"user123", Map.of(
						"name", "Alice Johnson",
						"account_type", "Premium",
						"balance", 5000,
						"email", "alice@example.com"
				),
				"user456", Map.of(
						"name", "Bob Smith",
						"account_type", "Standard",
						"balance", 1200,
						"email", "bob@example.com"
				)
		);

		@Override
		public String apply(String query, ToolContext toolContext) {
			RunnableConfig config = (RunnableConfig) toolContext.getContext().get("config");
			String userId = (String) config.metadata("user_id").orElse(null);

			if (userId == null) {
				return "User ID not provided";
			}

			Map<String, Object> user = USER_DATABASE.get(userId);
			if (user != null) {
				return String.format(
						"Account holder: %s\nType: %s\nBalance: $%d",
						user.get("name"),
						user.get("account_type"),
						user.get("balance")
				);
			}

			return "User not found";
		}
	}

	// ==================== MethodTools Related Classes ====================

	/**
	 * Calculator Tool Class - Using @Tool Annotation
	 */
	public static class CalculatorTools {
		public static int callCount = 0;

		@Tool(description = "Add two numbers together")
		public String add(
				@ToolParam(description = "First number") int a,
				@ToolParam(description = "Second number") int b) {
			callCount++;
			return String.valueOf(a + b);
		}

		@Tool(description = "Multiply two numbers together")
		public String multiply(
				@ToolParam(description = "First number") int a,
				@ToolParam(description = "Second number") int b) {
			callCount++;
			return String.valueOf(a * b);
		}

		@Tool(description = "Subtract second number from first number")
		public String subtract(
				@ToolParam(description = "First number") int a,
				@ToolParam(description = "Second number") int b) {
			callCount++;
			return String.valueOf(a - b);
		}
	}

	/**
	 * Weather Tool Class - Using @Tool Annotation
	 */
	public static class WeatherTools {
		@Tool(description = "Get current weather for a location")
		public String getWeather(@ToolParam(description = "City name") String city) {
			return "Sunny, 25°C in " + city;
		}

		@Tool(description = "Get weather forecast for a location")
		public String getForecast(
				@ToolParam(description = "City name") String city,
				@ToolParam(description = "Number of days") int days) {
			return String.format("Weather forecast for %s for next %d days: Mostly sunny", city, days);
		}
	}

	// ==================== ToolCallbackProvider Related Classes ====================

	/**
	 * Custom ToolCallbackProvider Implementation
	 */
	public static class CustomToolCallbackProvider implements ToolCallbackProvider {
		private final List<ToolCallback> toolCallbacks;

		public CustomToolCallbackProvider(List<ToolCallback> toolCallbacks) {
			this.toolCallbacks = toolCallbacks;
		}

		@Override
		public ToolCallback[] getToolCallbacks() {
			return toolCallbacks.toArray(new ToolCallback[0]);
		}
	}

	/**
	 * Search Tool with Context
	 */
	public static class SearchToolWithContext implements BiFunction<String, ToolContext, String> {
		@Override
		public String apply(String query, ToolContext toolContext) {
			return "Search results for: " + query;
		}
	}

	// ==================== Resolver Related Classes ====================

	/**
	 * Search Request Class (for Composite Types)
	 */
	public static class SearchRequest {
		@JsonProperty(required = true)
		@JsonPropertyDescription("The search query string")
		public String query;

		public SearchRequest() {
		}

		public SearchRequest(String query) {
			this.query = query;
		}
	}

	/**
	 * Search Function with Composite Type
	 */
	public static class SearchFunctionWithRequest implements BiFunction<SearchRequest, ToolContext, String> {
		@Override
		public String apply(SearchRequest request, ToolContext toolContext) {
			return "Search results for: " + request.query;
		}
	}

	/**
	 * Calculator Request Class (for Composite Types)
	 */
	public static class CalculatorRequest {
		@JsonProperty(required = true)
		@JsonPropertyDescription("First number for the calculation")
		public int a;

		@JsonProperty(required = true)
		@JsonPropertyDescription("Second number for the calculation")
		public int b;

		public CalculatorRequest() {
		}

		public CalculatorRequest(int a, int b) {
			this.a = a;
			this.b = b;
		}
	}

	/**
	 * Calculator Function with Composite Type
	 */
	public static class CalculatorFunctionWithRequest implements BiFunction<CalculatorRequest, ToolContext, String> {
		@Override
		public String apply(CalculatorRequest request, ToolContext toolContext) {
			return String.valueOf(request.a + request.b);
		}
	}

	/**
	 * Calculator Function with Context
	 */
	public static class CalculatorFunctionWithContext implements BiFunction<String, ToolContext, String> {
		@Override
		public String apply(String expression, ToolContext toolContext) {
			// Simple calculation parsing (for demonstration)
			if (expression.contains("/")) {
				String[] parts = expression.split("/");
				double result = Double.parseDouble(parts[0].trim()) / Double.parseDouble(parts[1].trim());
				return String.valueOf(result);
			}
			if (expression.contains("*")) {
				String[] parts = expression.split("\\*");
				double result = Double.parseDouble(parts[0].trim()) * Double.parseDouble(parts[1].trim());
				return String.valueOf(result);
			}
			return "Calculation result for: " + expression;
		}
	}
}

