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
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelInterceptor;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ModelResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallHandler;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallRequest;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolCallResponse;
import com.alibaba.cloud.ai.graph.agent.interceptor.ToolInterceptor;
import com.alibaba.cloud.ai.graph.agent.renderer.SaaStTemplateRenderer;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;

import reactor.core.publisher.Flux;

/**
 * Agents Tutorial - agents.md
 */
public class AgentsExample {

	// ==================== Basic Model Configuration ====================

	/**
	 * Example 1: Basic Model Configuration
	 */
	public static void basicModelConfiguration() {
		// Create DashScope API instance
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Create ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create Agent
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.build();
	}

	/**
	 * Example 2: Advanced Model Configuration
	 */
	public static void advancedModelConfiguration() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.defaultOptions(DashScopeChatOptions.builder()
						.temperature(0.7)      // Controls randomness
						.maxToken(2000)       // Maximum output length
						.topP(0.9)            // Nucleus sampling parameter
						.enableThinking(true)
						.build())
				.build();
	}

	// ==================== Tool Definitions ====================

	public static void toolUsage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Create tool callback
		ToolCallback searchTool = FunctionToolCallback
				.builder("search", new SearchTool())
				.description("Tool for searching information")
				.inputType(String.class)
				.build();

		// Use multiple tools
		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.tools(searchTool)
				.build();
	}

	/**
	 * Example 5: Basic System Prompt
	 */
	public static void basicSystemPrompt() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.systemPrompt("You are a professional technical assistant. Please answer questions accurately and concisely.")
				.build();
	}

	/**
	 * Example 6: Using instruction
	 */
	public static void instructionUsage() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		String instruction = """
				You are an experienced software architect.
				
				When answering questions, please:
				1. First understand the user's core requirements
				2. Analyze possible technical solutions
				3. Provide clear recommendations with reasoning
				4. If more information is needed, proactively ask
				
				Maintain a professional and friendly tone.
				""";

		ReactAgent agent = ReactAgent.builder()
				.name("architect_agent")
				.model(chatModel)
				.instruction(instruction)
				.build();
	}

	// ==================== System Prompt ====================

	/**
	 * Example 6.5: Using custom TemplateRenderer with custom placeholder delimiters
	 *
	 * Demonstrates how to use StringTemplateRenderer.builder() to customize the start and end delimiters for placeholders.
	 * Default uses {variable}, here we demonstrate using {{variable}} as placeholders.
	 */
	public static void customTemplateRendererExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Use StringTemplateRenderer.builder() to create a TemplateRenderer with custom delimiters
		// Use {{ and }} as placeholder delimiters
		TemplateRenderer customRenderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.build();

		// systemPrompt with custom delimiters
		String systemPrompt = """
				You are a professional {{role}} assistant.
				Your area of expertise is {{domain}}.
				Please answer the user's questions in {{language}}.
				""";

		// instruction with custom delimiters
		String instruction = """
				The user's inquiry topic is: {{topic}}
				Please answer according to the following requirements:
				1. Maintain professionalism
				2. Provide specific examples
				3. Language should be {{style}}
				""";

		ReactAgent agent = ReactAgent.builder()
				.name("custom_template_agent")
				.model(chatModel)
				.systemPrompt(systemPrompt)
				.instruction(instruction)
				.templateRenderer(customRenderer)
				.build();

		// When used, variables in the state will automatically replace the {{ }} wrapped placeholders
		Map<String, Object> inputs = Map.of(
				"input", "Please introduce the core features of the Spring framework",
				"role", "Technical Expert",
				"domain", "Java Enterprise Development",
				"language", "English",
				"topic", "Spring Framework",
				"style", "concise and easy to understand"
		);

		Optional<OverAllState> result = agent.invoke(inputs);
		if (result.isPresent()) {
			List<Message> messages = (List<Message>) result.get().value("messages").orElse(List.of());
			for (Message message : messages) {
				if (message instanceof AssistantMessage) {
					System.out.println("Agent reply: " + ((AssistantMessage) message).getText());
				}
			}
		}
	}

	/**
	 * Example 7: Dynamic System Prompt
	 */
	public static void dynamicSystemPrompt() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("adaptive_agent")
				.model(chatModel)
				.interceptors(new DynamicPromptInterceptor())
				.build();
	}

	/**
	 * Example 8: Basic Invocation
	 */
	public static void basicInvocation() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.build();

		// String input
		AssistantMessage response = agent.call("What's the weather like in Hangzhou?");
		System.out.println(response.getText());

		// UserMessage input
		UserMessage userMessage = new UserMessage("Help me analyze this problem");
		AssistantMessage response2 = agent.call(userMessage);

		// Multiple messages
		List<Message> messages = List.of(
				new UserMessage("I want to learn about Java multithreading"),
				new UserMessage("Especially the usage of thread pools")
		);
		AssistantMessage response3 = agent.call(messages);
	}

	/**
	 * Example 9: Get Full State
	 */
	public static void getFullState() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.build();

		Optional<OverAllState> result = agent.invoke("Write me a poem");

		if (result.isPresent()) {
			OverAllState state = result.get();

			// Access message history
			Optional<Object> messages = state.value("messages");
			List<Message> messageList = (List<Message>) messages.get();

			// Access custom state
			Optional<Object> customData = state.value("custom_key");

			System.out.println("Full state: " + state);
		}
	}

	/**
	 * Example 10: Using Configuration
	 */
	public static void useConfiguration() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("my_agent")
				.model(chatModel)
				.build();

		String threadId = "thread_123";
		RunnableConfig runnableConfig = RunnableConfig.builder()
				.threadId(threadId)
				.addMetadata("key", "value")
				.build();

		AssistantMessage response = agent.call("Your question", runnableConfig);
	}

	// ==================== Invoking Agent ====================

	/**
	 * Example 10.1: Stream Invocation - Basic Usage
	 */
	public static void basicStreamInvocation() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("streaming_agent")
				.model(chatModel)
				.build();

		// Stream output
		Flux<NodeOutput> stream = agent.stream("Write me a poem about spring");

		stream.subscribe(
				output -> {
					// Process each node output
					System.out.println("Node: " + output.node());
					System.out.println("Agent: " + output.agent());
					if (output.tokenUsage() != null) {
						System.out.println("Token usage: " + output.tokenUsage());
					}
				},
				error -> System.err.println("Error: " + error.getMessage()),
				() -> System.out.println("Stream output completed")
		);
	}

	/**
	 * Example 10.2: Stream Invocation - Advanced Usage
	 */
	public static void advancedStreamInvocation() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("streaming_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.build();

		RunnableConfig config = RunnableConfig.builder()
				.threadId("stream_thread_1")
				.build();

		// Stream invocation with configuration
		Flux<NodeOutput> stream = agent.stream(new UserMessage("Explain quantum computing"), config);

		// Use doOnNext to handle intermediate outputs
		stream.doOnNext(output -> {
					if (!output.isSTART() && !output.isEND()) {
						System.out.println("Processing...");
						System.out.println("Current node: " + output.node());
					}
				})
				.doOnComplete(() -> System.out.println("All nodes processed"))
				.doOnError(e -> System.err.println("Stream processing error: " + e.getMessage()))
				.blockLast(); // Block until complete
	}

	/**
	 * Example 10.3: Stream Invocation - Collect All Outputs
	 */
	public static void collectStreamOutputs() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("streaming_agent")
				.model(chatModel)
				.build();

		Flux<NodeOutput> stream = agent.stream("Analyze the applications of machine learning");

		// Collect all outputs
		List<NodeOutput> outputs = stream.collectList().block();

		if (outputs != null) {
			System.out.println("Total received " + outputs.size() + " node outputs");

			// Get final output
			NodeOutput lastOutput = outputs.get(outputs.size() - 1);
			System.out.println("Final state: " + lastOutput.state());

			// Get messages
			Optional<Object> messages = lastOutput.state().value("messages");
			if (messages.isPresent()) {
				List<Message> messageList = (List<Message>) messages.get();
				Message lastMessage = messageList.get(messageList.size() - 1);
				if (lastMessage instanceof AssistantMessage assistantMsg) {
					System.out.println("Final reply: " + assistantMsg.getText());
				}
			}
		}
	}

	public static void structuredOutputWithType() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("poem_agent")
				.model(chatModel)
				.outputType(PoemOutput.class)
				.saver(new MemorySaver())
				.build();

		AssistantMessage response = agent.call("Write a poem about spring");
		// Output will follow the PoemOutput structure
		System.out.println(response.getText());
	}

	/**
	 * Example 12: Using outputSchema
	 */
	public static void structuredOutputWithSchema() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Use BeanOutputConverter to generate outputSchema
		BeanOutputConverter<TextAnalysisResult> outputConverter = new BeanOutputConverter<>(TextAnalysisResult.class);
		String format = outputConverter.getFormat();

		ReactAgent agent = ReactAgent.builder()
				.name("analysis_agent")
				.model(chatModel)
				.outputSchema(format)
				.saver(new MemorySaver())
				.build();

		AssistantMessage response = agent.call("Analyze this text: Spring has come, everything is reviving.");
	}

	/**
	 * Example 13: Configure Memory
	 */
	public static void configureMemory() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Configure in-memory storage
		ReactAgent agent = ReactAgent.builder()
				.name("chat_agent")
				.model(chatModel)
				.saver(new MemorySaver())
				.build();

		// Use thread_id to maintain conversation context
		RunnableConfig config = RunnableConfig.builder()
				.threadId("user_123")
				.build();

		agent.call("My name is Zhang San", config);
		agent.call("What is my name?", config);  // Output: "Your name is Zhang San"
	}

	// ==================== Structured Output ====================

	public static void main(String[] args) {
		System.out.println("=== Agents Tutorial Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			System.out.println("\n--- Example 1: Basic Model Configuration ---");
			basicModelConfiguration();

			System.out.println("\n--- Example 2: Advanced Model Configuration ---");
			advancedModelConfiguration();

			System.out.println("\n--- Example 3: Tool Usage ---");
			toolUsage();

			System.out.println("\n--- Example 5: Basic System Prompt ---");
			basicSystemPrompt();

			System.out.println("\n--- Example 6: Using instruction ---");
			instructionUsage();

			System.out.println("\n--- Example 7: Dynamic System Prompt ---");
			dynamicSystemPrompt();

			System.out.println("\n--- Example 8: Basic Invocation ---");
			basicInvocation();

			System.out.println("\n--- Example 9: Get Full State ---");
			getFullState();

			System.out.println("\n--- Example 10: Using Configuration ---");
			useConfiguration();

			System.out.println("\n--- Example 10.1: Stream Invocation - Basic Usage ---");
			basicStreamInvocation();

			System.out.println("\n--- Example 10.2: Stream Invocation - Advanced Usage ---");
			advancedStreamInvocation();

			System.out.println("\n--- Example 10.3: Stream Invocation - Collect All Outputs ---");
			collectStreamOutputs();

			System.out.println("\n--- Example 11: Using outputType ---");
			structuredOutputWithType();

			System.out.println("\n--- Example 12: Using outputSchema ---");
			structuredOutputWithSchema();

			System.out.println("\n--- Example 13: Configure Memory ---");
			configureMemory();

			System.out.println("\n=== All examples completed ===");
		}
		catch (GraphRunnerException e) {
			System.err.println("Error occurred while running examples: " + e.getMessage());
			e.printStackTrace();
		}
		catch (Exception e) {
			System.err.println("Unexpected error occurred: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Example 3: Define and Use Tools
	 */
	public static class SearchTool implements BiFunction<String, ToolContext, String> {
		@Override
		public String apply(
				@ToolParam(description = "Search keyword") String query,
				ToolContext toolContext) {
			return "Search result: " + query;
		}
	}

	/**
	 * Example 4: Tool Error Handling
	 */
	public static class ToolErrorInterceptor extends ToolInterceptor {
		@Override
		public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
			try {
				return handler.call(request);
			}
			catch (Exception e) {
				return ToolCallResponse.of(request.getToolCallId(), request.getToolName(),
						"Tool failed: " + e.getMessage());
			}
		}

		@Override
		public String getName() {
			return "ToolErrorInterceptor";
		}
	}

	// ==================== Memory ====================

	/**
	 * Example 7: Dynamic System Prompt
	 */
	public static class DynamicPromptInterceptor extends ModelInterceptor {
		@Override
		public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
			// Dynamically adjust system prompt based on context
			Map<String, Object> context = request.getContext();

			// Build dynamic prompt based on context
			String dynamicPrompt = buildDynamicPrompt(context);

			// Enhance system message
			SystemMessage enhancedSystemMessage;
			if (request.getSystemMessage() == null) {
				enhancedSystemMessage = new SystemMessage(dynamicPrompt);
			}
			else {
				enhancedSystemMessage = new SystemMessage(
						request.getSystemMessage().getText() + "\n\n" + dynamicPrompt
				);
			}

			// Create enhanced request
			ModelRequest modifiedRequest = ModelRequest.builder(request)
					.systemMessage(enhancedSystemMessage)
					.build();

			return handler.call(modifiedRequest);
		}

		private String buildDynamicPrompt(Map<String, Object> context) {
			// Example: dynamically generate prompts based on user role
			String userRole = (String) context.getOrDefault("user_role", "default");

			return switch (userRole) {
				case "expert" -> """
						You are speaking with a technical expert.
						- Use professional terminology
						- Go into technical details
						- Provide advanced recommendations
						""";
				case "beginner" -> """
						You are speaking with a beginner.
						- Use simple and easy-to-understand language
						- Explain concepts in detail
						- Provide beginner-level recommendations
						""";
				default -> """
						You are a professional assistant.
						- Adjust answers based on question complexity
						- Maintain a friendly and professional tone
						""";
			};
		}

		@Override
		public String getName() {
			return "DynamicPromptInterceptor";
		}
	}

	// ==================== Hooks ====================

	/**
	 * Example 11: Using outputType
	 */
	public static class PoemOutput {
		private String title;
		private String content;
		private String style;

		// Getters and Setters
		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public String getContent() {
			return content;
		}

		public void setContent(String content) {
			this.content = content;
		}

		public String getStyle() {
			return style;
		}

		public void setStyle(String style) {
			this.style = style;
		}
	}

	/**
	 * Example 12: Text Analysis Result Output Class
	 */
	public static class TextAnalysisResult {
		private String summary;
		private List<String> keywords;
		private String sentiment;
		private Double confidence;

		// Getters and Setters
		public String getSummary() {
			return summary;
		}

		public void setSummary(String summary) {
			this.summary = summary;
		}

		public List<String> getKeywords() {
			return keywords;
		}

		public void setKeywords(List<String> keywords) {
			this.keywords = keywords;
		}

		public String getSentiment() {
			return sentiment;
		}

		public void setSentiment(String sentiment) {
			this.sentiment = sentiment;
		}

		public Double getConfidence() {
			return confidence;
		}

		public void setConfidence(Double confidence) {
			this.confidence = confidence;
		}
	}

	/**
	 * Example 14: AgentHook - Execute at Agent start/end
	 */
	public static class LoggingHook extends AgentHook {
		@Override
		public String getName() {
			return "logging";
		}

		@Override
		public HookPosition[] getHookPositions() {
			return new HookPosition[] {
					HookPosition.BEFORE_AGENT,
					HookPosition.AFTER_AGENT
			};
		}

		@Override
		public CompletableFuture<Map<String, Object>> beforeAgent(OverAllState state, RunnableConfig config) {
			System.out.println("Agent execution started");
			return CompletableFuture.completedFuture(Map.of());
		}

		@Override
		public CompletableFuture<Map<String, Object>> afterAgent(OverAllState state, RunnableConfig config) {
			System.out.println("Agent execution completed");
			return CompletableFuture.completedFuture(Map.of());
		}
	}

	// ==================== Interceptors ====================

	/**
	 * Example 15: MessagesModelHook - Trim messages before model call
	 * Uses MessagesModelHook implementation to trim the message list before model calls, keeping only the last MAX_MESSAGES messages
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	public static class MessageTrimmingHook extends MessagesModelHook {
		private static final int MAX_MESSAGES = 10;

		@Override
		public String getName() {
			return "message_trimming";
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			// If message count exceeds the limit, keep only the last MAX_MESSAGES messages
			if (previousMessages.size() > MAX_MESSAGES) {
				List<Message> trimmedMessages = previousMessages.subList(
						previousMessages.size() - MAX_MESSAGES,
						previousMessages.size()
				);
				// Use REPLACE strategy to replace all messages
				return new AgentCommand(trimmedMessages, UpdatePolicy.REPLACE);
			}
			// If message count does not exceed the limit, return original messages (no modification)
			return new AgentCommand(previousMessages);
		}
	}

	/**
	 * Example 16: ModelInterceptor - Content Safety Check
	 */
	public static class GuardrailInterceptor extends ModelInterceptor {
		@Override
		public ModelResponse interceptModel(ModelRequest request, ModelCallHandler handler) {
			// Pre-check: validate input
			if (containsSensitiveContent(request.getMessages())) {
				return ModelResponse.of(new AssistantMessage("Inappropriate content detected"));
			}

			// Execute the call
			ModelResponse response = handler.call(request);

			// Post-check: validate output
			return sanitizeIfNeeded(response);
		}

		private boolean containsSensitiveContent(List<Message> messages) {
			// Implement sensitive content detection logic
			return false;
		}

		private ModelResponse sanitizeIfNeeded(ModelResponse response) {
			// Implement response sanitization logic
			return response;
		}

		@Override
		public String getName() {
			return "GuardrailInterceptor";
		}
	}

	// ==================== Main Method ====================

	/**
	 * Example 17: ToolInterceptor - Monitoring and Error Handling
	 */
	public static class ToolMonitoringInterceptor extends ToolInterceptor {
		@Override
		public ToolCallResponse interceptToolCall(ToolCallRequest request, ToolCallHandler handler) {
			long startTime = System.currentTimeMillis();
			try {
				ToolCallResponse response = handler.call(request);
				logSuccess(request, System.currentTimeMillis() - startTime);
				return response;
			}
			catch (Exception e) {
				logError(request, e, System.currentTimeMillis() - startTime);
				return ToolCallResponse.of(request.getToolCallId(), request.getToolName(),
							"Tool execution encountered a problem, please try again later");
			}
		}

		private void logSuccess(ToolCallRequest request, long duration) {
			System.out.println("Tool " + request.getToolName() + " succeeded in " + duration + "ms");
		}

		private void logError(ToolCallRequest request, Exception e, long duration) {
			System.err.println("Tool " + request.getToolName() + " failed in " + duration + "ms: " + e.getMessage());
		}

		@Override
		public String getName() {
			return "ToolMonitoringInterceptor";
		}
	}
}

