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
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.agent.hook.modelcalllimit.ModelCallLimitHook;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIDetectionHook;
import com.alibaba.cloud.ai.graph.agent.hook.pii.PIIType;
import com.alibaba.cloud.ai.graph.agent.hook.pii.RedactionStrategy;
import com.alibaba.cloud.ai.graph.agent.hook.summarization.SummarizationHook;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.contextediting.ContextEditingInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.todolist.TodoListInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolemulator.ToolEmulatorInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolretry.ToolRetryInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.toolselection.ToolSelectionInterceptor;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Hooks & Interceptors Tutorial - hooks.md
 */
public class HooksExample {

	// ==================== Basic Hook and Interceptor Configuration ====================

	/**
	 * Example 1: Adding Hooks and Interceptors
	 */
	public static void basicHooksAndInterceptors() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools (example)
		ToolCallback[] tools = new ToolCallback[0];

		// Create Hooks and Interceptors
		ModelHook loggingHook = new LoggingModelHook();
		MessagesModelHook messageTrimmingHook = new MessageTrimmingHook();
		ModelInterceptor guardrailInterceptor = new GuardrailInterceptor();
		ToolInterceptor retryInterceptor = new RetryToolInterceptor();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(tools)
				.hooks(loggingHook, messageTrimmingHook)
				.interceptors(guardrailInterceptor)
				.interceptors(retryInterceptor)
				.build();
	}

	// ==================== Message Compression (Summarization) ====================

	/**
	 * Example 2: Message Compression Hook
	 */
	public static void messageSummarization() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create message compression Hook
		SummarizationHook summarizationHook = SummarizationHook.builder()
				.model(chatModel)
				.maxTokensBeforeSummary(4000)
				.messagesToKeep(20)
				.build();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.hooks(summarizationHook)
				.build();

	}

	// ==================== Human-in-the-Loop ====================

	/**
	 * Example 3: Human-in-the-Loop Hook
	 */
	public static void humanInTheLoop() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools (example)
		ToolCallback sendEmailTool = createSendEmailTool();
		ToolCallback deleteDataTool = createDeleteDataTool();

		// Create Human-in-the-Loop Hook
		HumanInTheLoopHook humanReviewHook = HumanInTheLoopHook.builder()
				.approvalOn("sendEmailTool", ToolConfig.builder()
						.description("Please confirm sending the email.")
						.build())
				.approvalOn("deleteDataTool", ToolConfig.builder()
						.description("Please confirm deleting the data.")
						.build())
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("supervised_agent")
				.model(chatModel)
				.tools(sendEmailTool, deleteDataTool)
				.hooks(humanReviewHook)
				.saver(new MemorySaver())
				.build();
	}

	// ==================== Model Call Limit ====================

	/**
	 * Example 4: Model Call Limit
	 */
	public static void modelCallLimit() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.hooks(ModelCallLimitHook.builder().runLimit(5).build())  // Limit model calls to 5
				.saver(new MemorySaver())
				.build();
	}


	// ==================== PII Detection ====================

	/**
	 * Example 6: PII Detection
	 */
	public static void piiDetection() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		PIIDetectionHook pii = PIIDetectionHook.builder()
				.piiType(PIIType.EMAIL)
				.strategy(RedactionStrategy.REDACT)
				.applyToInput(true)
				.build();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("secure_agent")
				.model(chatModel)
				.hooks(pii)
				.build();
	}

	// ==================== Tool Retry ====================

	/**
	 * Example 7: Tool Retry
	 */
	public static void toolRetry() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tools (example)
		ToolCallback searchTool = createSearchTool();
		ToolCallback databaseTool = createDatabaseTool();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("resilient_agent")
				.model(chatModel)
				.tools(searchTool, databaseTool)
				.interceptors(ToolRetryInterceptor.builder().maxRetries(2)
						.onFailure(ToolRetryInterceptor.OnFailureBehavior.RETURN_MESSAGE).build())
				.build();
	}

	// ==================== Planning ====================

	/**
	 * Example 8: Planning Hook
	 */
	public static void planning() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ToolCallback myTool = createSampleTool();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("planning_agent")
				.model(chatModel)
				.tools(myTool)
				.interceptors(TodoListInterceptor.builder().build())
				.build();
	}

	// ==================== LLM Tool Selector ====================

	/**
	 * Example 9: LLM Tool Selector
	 */
	public static void llmToolSelector() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ChatModel selectorModel = chatModel; // Another ChatModel for selection

		ToolCallback tool1 = createSampleTool();
		ToolCallback tool2 = createSampleTool();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("smart_selector_agent")
				.model(chatModel)
				.tools(tool1, tool2)
				.interceptors(ToolSelectionInterceptor.builder()
						.selectionModel(selectorModel)
						.build())
				.build();
	}

	// ==================== LLM Tool Emulator ====================

	/**
	 * Example 10: LLM Tool Emulator
	 */
	public static void llmToolEmulator() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ToolCallback simulatedTool = createSampleTool();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("emulator_agent")
				.model(chatModel)
				.tools(simulatedTool)
				.interceptors(ToolEmulatorInterceptor.builder().model(chatModel).build())
				.build();
	}

	// ==================== Context Editing ====================

	/**
	 * Example 11: Context Editing
	 */
	public static void contextEditing() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Usage
		ReactAgent agent = ReactAgent.builder()
				.name("context_aware_agent")
				.model(chatModel)
				.interceptors(ContextEditingInterceptor.builder().trigger(120000).clearAtLeast(60000).build())
				.build();
	}

	// ==================== Custom Hooks ====================

	// Helper methods for creating sample tools
	private static ToolCallback createSendEmailTool() {
		return FunctionToolCallback.builder("sendEmailTool", (String input) -> "Email sent")
				.description("Send an email")
				.inputType(String.class)
				.build();
	}

	private static ToolCallback createDeleteDataTool() {
		return FunctionToolCallback.builder("deleteDataTool", (String input) -> "Data deleted")
				.description("Delete data")
				.inputType(String.class)
				.build();
	}

	// ==================== Custom Interceptors ====================

	private static ToolCallback createSearchTool() {
		return FunctionToolCallback.builder("searchTool", (String input) -> "Search results")
				.description("Search the web")
				.inputType(String.class)
				.build();
	}

	private static ToolCallback createDatabaseTool() {
		return FunctionToolCallback.builder("databaseTool", (String input) -> "Database query results")
				.description("Query database")
				.inputType(String.class)
				.build();
	}

	// ==================== Helper Classes and Methods ====================

	private static ToolCallback createSampleTool() {
		return FunctionToolCallback.builder("sampleTool", (String input) -> "Sample result")
				.description("A sample tool")
				.inputType(String.class)
				.build();
	}

	public static void main(String[] args) {
		System.out.println("=== Hooks and Interceptors Tutorial Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			System.out.println("\n--- Example 1: Basic Hooks and Interceptors ---");
			basicHooksAndInterceptors();

			System.out.println("\n--- Example 2: Message Compression Hook ---");
			messageSummarization();

			System.out.println("\n--- Example 3: Human-in-the-Loop ---");
			humanInTheLoop();

			System.out.println("\n--- Example 4: Model Call Limit ---");
			modelCallLimit();

			System.out.println("\n--- Example 5: PII Detection ---");
			piiDetection();

			System.out.println("\n--- Example 6: Tool Retry ---");
			toolRetry();

			System.out.println("\n--- Example 7: Planning ---");
			planning();

			System.out.println("\n--- Example 8: LLM Tool Selector ---");
			llmToolSelector();

			System.out.println("\n--- Example 9: LLM Tool Emulator ---");
			llmToolEmulator();

			System.out.println("\n--- Example 10: Context Editing ---");
			contextEditing();

			System.out.println("\n=== All examples executed successfully ===");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Example 12: Custom ModelHook
	 */
	@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
	public static class CustomModelHook extends ModelHook {

		@Override
		public String getName() {
			return "custom_model_hook";
		}

		@Override
		public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
			// Execute before model call
			System.out.println("Preparing to call model...");

			// Can modify state
			// e.g.: Add extra context
			return CompletableFuture.completedFuture(Map.of("extra_context", "Some extra information"));
		}

		@Override
		public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
			// Execute after model call
			System.out.println("Model call completed");

			// Can log response information
			return CompletableFuture.completedFuture(Map.of());
		}
	}

	/**
	 * Example 13: Custom AgentHook
	 */
	@HookPositions({HookPosition.BEFORE_AGENT, HookPosition.AFTER_AGENT})
	public static class CustomAgentHook extends AgentHook {

		@Override
		public String getName() {
			return "custom_agent_hook";
		}

		@Override
		public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
			System.out.println("Agent started execution");
			// Can initialize resources, record start time, etc.
			return CompletableFuture.completedFuture(Map.of("start_time", System.currentTimeMillis()));
		}

		@Override
		public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
			System.out.println("Agent execution completed");
			// Can clean up resources, calculate execution time, etc.
			Optional<Object> startTime = state.value("start_time");
			if (startTime.isPresent()) {
				long duration = System.currentTimeMillis() - (Long) startTime.get();
				System.out.println("Execution time: " + duration + "ms");
			}
			return CompletableFuture.completedFuture(Map.of());
		}
	}

	/**
	 * Example 14: Custom ModelInterceptor
	 */
	public static class LoggingInterceptor extends ModelInterceptor {

		@Override
		public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
			// Log before request
			System.out.println("Sending request to model: " + request.getMessages().size() + " messages");

			long startTime = System.currentTimeMillis();

			// Execute actual call
			ModelResponse response = handler.call(request);

			// Log after response
			long duration = System.currentTimeMillis() - startTime;
			System.out.println("Model response time: " + duration + "ms");

			return response;
		}

		@Override
		public String getName() {
			return "LoggingInterceptor";
		}
	}

	/**
	 * Example 15: Custom ToolInterceptor
	 */
	public static class ToolMonitoringInterceptor extends ToolInterceptor {

		@Override
		public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
			String toolName = request.getToolName();
			long startTime = System.currentTimeMillis();

			System.out.println("Executing tool: " + toolName);

			try {
				ToolCallResponse response = handler.call(request);

				long duration = System.currentTimeMillis() - startTime;
				System.out.println("Tool " + toolName + " executed successfully (time: " + duration + "ms)");

				return response;
			}
			catch (Exception e) {
				long duration = System.currentTimeMillis() - startTime;
				System.err.println("Tool " + toolName + " execution failed (time: " + duration + "ms): " + e.getMessage());

				return ToolCallResponse.of(
						request.getToolCallId(),
						request.getToolName(),
						"Tool execution failed: " + e.getMessage()
				);
			}
		}

		@Override
		public String getName() {
			return "ToolMonitoringInterceptor";
		}
	}

	/**
	 * Logging ModelHook
	 */
	@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
	private static class LoggingModelHook extends ModelHook {
		@Override
		public String getName() {
			return "logging_model_hook";
		}

		@Override
		public HookPosition[] getHookPositions() {
			return new HookPosition[] {HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL};
		}

		@Override
		public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
			System.out.println("Before model call");
			return CompletableFuture.completedFuture(Map.of());
		}

		@Override
		public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
			System.out.println("After model call");
			return CompletableFuture.completedFuture(Map.of());
		}
	}

	/**
	 * Message Trimming Hook
	 * Implemented using MessagesModelHook, trims the message list before model call, keeping only the last 10 messages
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	private static class MessageTrimmingHook extends MessagesModelHook {
		private static final int MAX_MESSAGES = 10;

		@Override
		public String getName() {
			return "message_trimming";
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			// If message count exceeds limit, keep only the last MAX_MESSAGES messages
			if (previousMessages.size() > MAX_MESSAGES) {
				List<Message> trimmedMessages = previousMessages.subList(
						previousMessages.size() - MAX_MESSAGES,
						previousMessages.size()
				);
				// Use REPLACE strategy to replace all messages
				return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
			}
			// If message count does not exceed limit, return original messages (no modification)
			return new AgentCommand(previousMessages);
		}
	}

	/**
	 * Guardrail Interceptor
	 */
	private static class GuardrailInterceptor extends ModelInterceptor {
		@Override
		public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
			// Simplified implementation
			return handler.call(request);
		}

		@Override
		public String getName() {
			return "GuardrailInterceptor";
		}
	}

	// ==================== Main Method ====================

	/**
	 * Retry Tool Interceptor
	 */
	private static class RetryToolInterceptor extends ToolInterceptor {
		@Override
		public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
			// Simplified implementation
			return handler.call(request);
		}

		@Override
		public String getName() {
			return "RetryToolInterceptor";
		}
	}
}

