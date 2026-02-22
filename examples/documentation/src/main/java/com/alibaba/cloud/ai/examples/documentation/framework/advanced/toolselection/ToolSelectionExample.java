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
package com.alibaba.cloud.ai.examples.documentation.framework.advanced.toolselection;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolselection.ToolSelectionInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.util.List;
import java.util.Optional;

/**
 * ToolSelectionInterceptor Example
 *
 * This example demonstrates how to use ToolSelectionInterceptor for intelligent tool selection.
 *
 * Core Features:
 * 1. When an Agent has multiple tools, uses LLM to intelligently select the most relevant tools
 * 2. Tool descriptions are automatically passed to the selection model to improve selection accuracy
 * 3. Configurable maxTools to limit the number of tools selected per invocation
 * 4. Supports alwaysInclude to ensure critical tools are always available
 *
 * Use Cases:
 * - Agent has many tools (>5), needs to reduce token consumption
 * - Need to improve tool selection accuracy
 * - Different queries require different tool subsets
 */
public class ToolSelectionExample {

	// ==================== Example 1: Basic Usage ====================

	/**
	 * Basic usage: Create an Agent with tool selection
	 */
	public static void basicToolSelection() throws Exception {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
			.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
			.build();

		ChatModel chatModel = DashScopeChatModel.builder()
			.dashScopeApi(dashScopeApi)
			.build();

		// Create tool class instance
		TravelTools travelTools = new TravelTools();

		// Create ToolSelectionInterceptor
		// When the number of tools exceeds maxTools, LLM will be used to select the most relevant tools
		ToolSelectionInterceptor interceptor = ToolSelectionInterceptor.builder()
			.selectionModel(chatModel)  // Model used for tool selection
			.maxTools(3)                // Select at most 3 tools
			.build();

		// Create Agent
		ReactAgent agent = ReactAgent.builder()
			.name("travel_assistant")
			.model(chatModel)
			.methodTools(travelTools)   // Automatically scans @Tool annotated methods
			.interceptors(interceptor)
			.saver(new MemorySaver())
			.build();

		// Invoke Agent - automatically selects the most relevant tools
		Optional<OverAllState> result = agent.invoke("What's the weather like in Beijing today?");
		printResult(result, "Basic Usage");
	}

	// ==================== Example 2: Using alwaysInclude ====================

	/**
	 * Advanced usage: Use alwaysInclude to ensure critical tools are always available
	 */
	public static void toolSelectionWithAlwaysInclude() throws Exception {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
			.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
			.build();

		ChatModel chatModel = DashScopeChatModel.builder()
			.dashScopeApi(dashScopeApi)
			.build();

		TravelTools travelTools = new TravelTools();

		// Use alwaysInclude to ensure certain tools are always available
		ToolSelectionInterceptor interceptor = ToolSelectionInterceptor.builder()
			.selectionModel(chatModel)
			.maxTools(2)
			.alwaysInclude("get_weather")  // Weather tool is always included
			.build();

		ReactAgent agent = ReactAgent.builder()
			.name("travel_assistant")
			.model(chatModel)
			.methodTools(travelTools)
			.interceptors(interceptor)
			.saver(new MemorySaver())
			.build();

		// Even if the query is unrelated to weather, the weather tool will be included
		Optional<OverAllState> result = agent.invoke("Help me book a flight to Shanghai");
		printResult(result, "alwaysInclude Example");
	}

	// ==================== Example 3: Custom System Prompt ====================

	/**
	 * Advanced usage: Custom system prompt for tool selection
	 */
	public static void toolSelectionWithCustomPrompt() throws Exception {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
			.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
			.build();

		ChatModel chatModel = DashScopeChatModel.builder()
			.dashScopeApi(dashScopeApi)
			.build();

		TravelTools travelTools = new TravelTools();

		// Custom system prompt for selection logic
		String customPrompt = """
			You are a tool selector for a travel assistant.
			Based on the user's query, select the most relevant tools to help answer the question.

			Selection principles:
			1. Prioritize tools that can directly solve the user's problem
			2. If the user asks about multiple aspects, select tools that cover all aspects
			3. Avoid selecting clearly irrelevant tools
			""";

		ToolSelectionInterceptor interceptor = ToolSelectionInterceptor.builder()
			.selectionModel(chatModel)
			.maxTools(3)
			.systemPrompt(customPrompt)
			.build();

		ReactAgent agent = ReactAgent.builder()
			.name("travel_assistant")
			.model(chatModel)
			.methodTools(travelTools)
			.interceptors(interceptor)
			.saver(new MemorySaver())
			.build();

		Optional<OverAllState> result = agent.invoke("I'm going to Hangzhou next week for tourism, check the weather and attractions for me");
		printResult(result, "Custom Prompt Example");
	}

	// ==================== Example 4: Multi-Tool Scenario ====================

	/**
	 * Complex scenario: Agent with multiple tools
	 */
	public static void multiToolScenario() throws Exception {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
			.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
			.build();

		ChatModel chatModel = DashScopeChatModel.builder()
			.dashScopeApi(dashScopeApi)
			.build();

		// Create multiple tool classes
		TravelTools travelTools = new TravelTools();
		UtilityTools utilityTools = new UtilityTools();

		// Configure tool selection
		ToolSelectionInterceptor interceptor = ToolSelectionInterceptor.builder()
			.selectionModel(chatModel)
			.maxTools(3)  // Select 3 from 8+ tools
			.build();

		ReactAgent agent = ReactAgent.builder()
			.name("smart_assistant")
			.model(chatModel)
			.methodTools(travelTools, utilityTools)  // Register multiple tool classes
			.interceptors(interceptor)
			.saver(new MemorySaver())
			.build();

		// Test different queries
		System.out.println("\n--- Test 1: Weather Query ---");
		Optional<OverAllState> result1 = agent.invoke("How is the weather in Beijing today?");
		printResult(result1, "Weather Query");

		System.out.println("\n--- Test 2: Flight Query ---");
		Optional<OverAllState> result2 = agent.invoke("Check tomorrow's flights from Shanghai to Beijing");
		printResult(result2, "Flight Query");

		System.out.println("\n--- Test 3: Currency Conversion ---");
		Optional<OverAllState> result3 = agent.invoke("How much CNY can 100 USD exchange for?");
		printResult(result3, "Currency Conversion");

		System.out.println("\n--- Test 4: Compound Query ---");
		Optional<OverAllState> result4 = agent.invoke("I'm going to Hangzhou for tourism, check the weather, recommend attractions, and find a hotel");
		printResult(result4, "Compound Query");
	}

	// ==================== Tool Class Definitions ====================

	/**
	 * Travel-related tools
	 *
	 * Note: Tool descriptions should be detailed and accurate so ToolSelectionInterceptor can make correct selections
	 */
	public static class TravelTools {

		@Tool(name = "get_weather",
			  description = "Get real-time weather information for a specified city, including temperature, humidity, weather conditions, and air quality. " +
						   "Use this tool when the user asks about the weather in a city.")
		public String getWeather(
				@ToolParam(description = "City name, e.g.: Beijing, Shanghai, Guangzhou") String city) {
			return String.format("Today's weather in %s: Sunny, temperature 18-25°C, humidity 45%%, air quality good.", city);
		}

		@Tool(name = "search_flights",
			  description = "Search for flight information between two cities, returning flight number, departure time, arrival time, and fare. " +
						   "Use this tool when the user wants to search or book flights.")
		public String searchFlights(
				@ToolParam(description = "Departure city") String from,
				@ToolParam(description = "Arrival city") String to,
				@ToolParam(description = "Departure date, format: YYYY-MM-DD") String date) {
			return String.format("Found flights from %s to %s (%s):\n" +
				"1. CA1234 08:00-10:30 ¥680\n" +
				"2. MU5678 12:00-14:30 ¥720\n" +
				"3. CZ9012 18:00-20:30 ¥650", from, to, date);
		}

		@Tool(name = "search_hotels",
			  description = "Search for hotels in a specified city, filterable by check-in date and price range. " +
						   "Use this tool when the user wants to book accommodation.")
		public String searchHotels(
				@ToolParam(description = "City name") String city,
				@ToolParam(description = "Check-in date, format: YYYY-MM-DD") String arrivalDate) {
			return String.format("%s Hotel Recommendations (check-in %s):\n" +
				"1. Hilton Hotel ★★★★★ ¥800/night\n" +
				"2. Home Inn ★★★ ¥280/night\n" +
				"3. Boutique B&B ★★★★ ¥450/night", city, arrivalDate);
		}

		@Tool(name = "get_attractions",
			  description = "Get a list of popular tourist attractions in a specified city, including introductions, ticket prices, and recommended visit duration. " +
						   "Use this tool when the user wants to learn about attractions at a travel destination.")
		public String getAttractions(
				@ToolParam(description = "City name") String city) {
			return String.format("%s Popular Attractions:\n" +
				"1. West Lake - Free, recommended half-day visit\n" +
				"2. Lingyin Temple - Ticket ¥45, incense extra\n" +
				"3. Songcheng - Ticket ¥300, includes show", city);
		}

		@Tool(name = "search_restaurants",
			  description = "Search for restaurants in a specified city, filterable by cuisine type and price range. " +
						   "Use this tool when the user wants to find a place to eat or learn about local food.")
		public String searchRestaurants(
				@ToolParam(description = "City name") String city,
				@ToolParam(description = "Cuisine type, e.g.: hotpot, Sichuan, Cantonese") String cuisine) {
			return String.format("%s %s Restaurant Recommendations:\n" +
				"1. Heritage Restaurant - Avg ¥80/person Rating 4.8\n" +
				"2. Trending Spot - Avg ¥120/person Rating 4.5\n" +
				"3. Local Specialty House - Avg ¥60/person Rating 4.7", city, cuisine);
		}
	}

	/**
	 * Utility Tools
	 */
	public static class UtilityTools {

		@Tool(name = "convert_currency",
			  description = "Currency exchange rate conversion, supporting multiple currencies (e.g., USD, EUR, CNY, JPY). " +
						   "Use this tool when the user needs to check exchange rates or convert currencies.")
		public String convertCurrency(
				@ToolParam(description = "Amount") double amount,
				@ToolParam(description = "Source currency code, e.g., USD, EUR, CNY") String from,
				@ToolParam(description = "Target currency code") String to) {
			double rate = 7.2; // Simplified exchange rate
			if ("USD".equals(from) && "CNY".equals(to)) {
				return String.format("%.2f USD = %.2f CNY (Rate: 1 USD = %.2f CNY)",
					amount, amount * rate, rate);
			}
			return String.format("%.2f %s = %.2f %s", amount, from, amount, to);
		}

		@Tool(name = "translate_text",
			  description = "Text translation service, supporting translations between Chinese, English, Japanese, Korean, and more. " +
						   "Use this tool when the user needs to translate text or understand foreign language content.")
		public String translateText(
				@ToolParam(description = "Text to translate") String text,
				@ToolParam(description = "Target language: Chinese, English, Japanese, Korean") String targetLang) {
			return String.format("Translation result (%s): [translated content]", targetLang);
		}

		@Tool(name = "calculate",
			  description = "Math calculator, supporting addition, subtraction, multiplication, division, exponentiation, percentages, and more. " +
						   "Use this tool when the user needs to perform mathematical calculations.")
		public String calculate(
				@ToolParam(description = "Mathematical expression, e.g.: 100*1.1, 50+30") String expression) {
			return "Calculation result: " + expression + " = [result]";
		}
	}

	// ==================== Helper Methods ====================

	private static void printResult(Optional<OverAllState> result, String testName) {
		System.out.println("[" + testName + "] Execution result:");
		result.ifPresent(state -> {
			List<Message> messages = state.value("messages", List.of());
			for (Message msg : messages) {
				if (msg instanceof AssistantMessage) {
					System.out.println("Assistant: " + msg.getText());
				}
			}
		});
	}

	// ==================== Main Method ====================

	public static void main(String[] args) {
		System.out.println("=== ToolSelectionInterceptor Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			System.out.println("\n--- Example 1: Basic Usage ---");
			basicToolSelection();

			System.out.println("\n--- Example 2: Using alwaysInclude ---");
			toolSelectionWithAlwaysInclude();

			System.out.println("\n--- Example 3: Custom System Prompt ---");
			toolSelectionWithCustomPrompt();

			System.out.println("\n--- Example 4: Multi-Tool Scenario ---");
			multiToolScenario();

			System.out.println("\n=== All examples completed ===");
		}
		catch (Exception e) {
			System.err.println("Error occurred while running examples: " + e.getMessage());
			e.printStackTrace();
		}
	}

}
