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
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.node.AgentToolNode;
import com.alibaba.cloud.ai.graph.agent.tool.AsyncToolCallback;
import com.alibaba.cloud.ai.graph.agent.tool.CancellableAsyncToolCallback;
import com.alibaba.cloud.ai.graph.agent.tool.CancellationToken;
import com.alibaba.cloud.ai.graph.agent.tool.DefaultCancellationToken;
import com.alibaba.cloud.ai.graph.agent.tool.StateAwareToolCallback;
import com.alibaba.cloud.ai.graph.agent.tool.ToolCancelledException;
import com.alibaba.cloud.ai.graph.agent.tool.ToolStateCollector;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.DefaultToolExecutionExceptionProcessor;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_CONFIG_CONTEXT_KEY;
import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_STATE_CONTEXT_KEY;
import static com.alibaba.cloud.ai.graph.agent.tools.ToolContextConstants.AGENT_STATE_FOR_UPDATE_CONTEXT_KEY;

/**
 * Async Tool Execution Example
 *
 * <p>This example demonstrates the asynchronous tool support introduced in Issue #3988, including:</p>
 * <ul>
 *   <li>AsyncToolCallback - basic asynchronous tool interface</li>
 *   <li>CancellableAsyncToolCallback - an asynchronous tool interface that supports cancellation</li>
 *   <li>CancellationToken - Collaboration cancellation mechanism</li>
 *   <li>StateAwareToolCallback - State awareness tools</li>
 *   <li>ToolStateCollector - state collection and merging during parallel execution</li>
 *   <li>AgentToolNode - Parallel tool execution configuration</li>
 * </ul>
 *
 * <p>Reference documentation: Issue #3988 - Async Tool Support</p>
 *
 * @author disaster
 * @since 1.0.0
 */
public class AsyncToolExecutionExample {

	private final ChatModel chatModel;

	public AsyncToolExecutionExample(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/**
	 * Main method: run all examples
	 *
	 * <p>Note: The AI_DASHSCOPE_API_KEY environment variable needs to be set</p>
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
		AsyncToolExecutionExample example = new AsyncToolExecutionExample(chatModel);

		//Run all examples
		example.runAllExamples();
	}

	//==================== Example 1: Basic asynchronous tools ====================

	/**
	 * Example 1: Basic asynchronous tool (AsyncToolCallback)
	 *
	 * <p>The AsyncToolCallback interface allows tools to execute asynchronously and returns a CompletableFuture.
	 * This is useful for tasks that require I/O operations or long-running tasks.</p>
	 *
	 * <p>Key features:</p>
	 * <ul>
	 *   <li>callAsync() return CompletableFuture&lt;String&gt;</li>
	 *   <li>Customizable timeout (default 5 minutes)</li>
	 *   <li>Automatically handle the rollback of synchronous calls (the call method will block waiting for the result)</li>
	 * </ul>
	 */
	public void example1_basicAsyncTool() {
		System.out.println("=== Example 1: Basic Async Tool (AsyncToolCallback) ===\n");

		//Create simple asynchronous tools
		AsyncToolCallback asyncWeatherTool = new AsyncToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("async_weather")
					.description("Get weather information asynchronously")
					.inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}")
					.build();
			}

			@Override
			public CompletableFuture<String> callAsync(String arguments, ToolContext context) {
				//Use CompletableFuture.supplyAsync to execute asynchronously
				return CompletableFuture.supplyAsync(() -> {
					System.out.println("[Asynchronous execution] Start getting weather data...");

					//Simulate asynchronous I/O operations (such as HTTP requests)
					try {
						Thread.sleep(1000); //Simulate network latency
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
						throw new RuntimeException("Obtaining weather data was interrupted", e);
					}

					System.out.println("[Asynchronous execution] Weather data acquisition completed");
					return "{\"temperature\": 25, \"condition\": \"sunny\", \"city\": \"Beijing\"}";
				});
			}

			@Override
			public Duration getTimeout() {
				//Custom timeout is 30 seconds
				return Duration.ofSeconds(30);
			}

			@Override
			public String call(String toolInput) {
				//When calling synchronously, block waiting for asynchronous results.
				return callAsync(toolInput, new ToolContext(Map.of())).join();
			}
		};

		//Testing asynchronous tools
		System.out.println("Test asynchronous calls:");
		CompletableFuture<String> future = asyncWeatherTool.callAsync("{\"city\":\"Beijing\"}", new ToolContext(Map.of()));

		//Non-blocking: can do other things
		System.out.println("Asynchronous call submitted, waiting for result...");

		//Get results
		String result = future.join();
		System.out.println("result:" + result);

		System.out.println("\nTest synchronous call (automatic blocking wait):");
		String syncResult = asyncWeatherTool.call("{\"city\":\"Shanghai\"}");
		System.out.println("result:" + syncResult);

		System.out.println();
	}

	//==================== Example 2: Cancelable asynchronous tools ====================

	/**
	 * Example 2: CancellableAsyncToolCallback
	 *
	 * <p>CancellableAsyncToolCallback expanded AsyncToolCallback, supports collaborative cancellation.
	 * When the tool execution times out or needs to be terminated early, the tool can be notified to stop gracefully through CancellationToken.</p>
	 *
	 * <p>Key features:</p>
	 * <ul>
	 *   <li>take over CancellationToken parameter</li>
	 *   <li>support isCancelled() examine</li>
	 *   <li>Support throwIfCancelled() to throw exception</li>
	 *   <li>Support onCancel() to register cleanup callback</li>
	 * </ul>
	 */
	public void example2_cancellableAsyncTool() {
		System.out.println("=== Example 2: CancellableAsyncToolCallback ===\n");

		//Simulated resource manager (used to demonstrate cancellation callbacks)
		AtomicInteger resourcesAllocated = new AtomicInteger(0);

		//Create an asynchronous tool that supports cancellation
		CancellableAsyncToolCallback cancellableSearchTool = new CancellableAsyncToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("cancellable_search")
					.description("Cancelable search tool with graceful stopping")
					.inputSchema("{\"type\":\"object\",\"properties\":{\"query\":{\"type\":\"string\"}}}")
					.build();
			}

			@Override
			public CompletableFuture<String> callAsync(String arguments, ToolContext context,
					CancellationToken cancellationToken) {
				return CompletableFuture.supplyAsync(() -> {
					System.out.println("[Cancellable Tool] Start Search...");

					//Register cancellation callback - for resource cleanup
					cancellationToken.onCancel(() -> {
						System.out.println("[Cancel callback] Clean up allocated resources...");
						resourcesAllocated.set(0);
					});

					StringBuilder results = new StringBuilder();

					//Simulate paging search and check cancellation status on each page
					for (int page = 1; page <= 10; page++) {
						//Method 1: Use isCancelled() to check and return early
						if (cancellationToken.isCancelled()) {
							System.out.println("[Cancellable Tool] Cancel request detected, exit gracefully (section" + page + "Page)");
							return results + "\n[Search in" + page + "page canceled]";
						}

						//Simulate resource allocation and page processing
						resourcesAllocated.incrementAndGet();
						System.out.println("[Cancellable Tool] Processing No." + page + "Page...");

						try {
							Thread.sleep(200); //Simulation processing time
						}
						catch (InterruptedException e) {
							Thread.currentThread().interrupt();
							throw new RuntimeException("Search interrupted", e);
						}

						results.append("Page ").append(page).append(" results; ");

						//Method 2: Use throwIfCancelled() to throw an exception
						// cancellationToken.throwIfCancelled();
					}

					System.out.println("[Cancellable Tool] Search completed");
					return results.toString();
				});
			}

			@Override
			public Duration getTimeout() {
				return Duration.ofSeconds(5); //Short timeout for demonstration purposes
			}

			@Override
			public String call(String toolInput) {
				return callAsync(toolInput, new ToolContext(Map.of()), CancellationToken.NONE).join();
			}
		};

		//Test 1: Completed normally
		System.out.println("Test 1: Normal execution (no cancellation)");
		CompletableFuture<String> future1 = cancellableSearchTool.callAsync("{\"query\":\"AI\"}", new ToolContext(Map.of()),
				CancellationToken.NONE);
		System.out.println("result:" + future1.join().substring(0, Math.min(50, future1.join().length())) + "...");

		//Test 2: Active cancellation
		System.out.println("\nTest 2: Actively cancel execution");
		DefaultCancellationToken cancelToken = new DefaultCancellationToken();

		CompletableFuture<String> future2 = cancellableSearchTool.callAsync("{\"query\":\"Spring\"}", new ToolContext(Map.of()),
				cancelToken);

		//Cancel after 500ms delay
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(500);
			}
			catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			System.out.println("[Main thread] Request to cancel...");
			cancelToken.cancel();
		});

		String cancelledResult = future2.join();
		System.out.println("result:" + cancelledResult);

		System.out.println();
	}

	//==================== Example 3: State-aware asynchronous tools ====================

	/**
	 * Example 3: StateAwareToolCallback
	 *
	 * <p>StateAwareToolCallback is a marker interface, and tools that implement this interface will automatically receive
	 * Agent state injection.Tools can read the current state and write updates.</p>
	 *
	 * <p>Injected context key:</p>
	 * <ul>
	 *   <li>AGENT_STATE_CONTEXT_KEY - current OverAllState(read only)</li>
	 *   <li>AGENT_CONFIG_CONTEXT_KEY - RunnableConfig Configuration</li>
	 *   <li>AGENT_STATE_FOR_UPDATE_CONTEXT_KEY - status update Map(writable)</li>
	 * </ul>
	 */
	public void example3_stateAwareAsyncTool() {
		System.out.println("=== Example 3: StateAwareToolCallback ===\n");

		//Create state-aware asynchronous tools
		// Notice:AsyncToolCallback Inherited from StateAwareToolCallback
		AsyncToolCallback stateAwareCalculator = new AsyncToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("state_aware_calculator")
					.description("State-aware calculator that can read and update Agent state")
					.inputSchema("{\"type\":\"object\",\"properties\":{\"operation\":{\"type\":\"string\"},\"value\":{\"type\":\"number\"}}}")
					.build();
			}

			@Override
			@SuppressWarnings("unchecked")
			public CompletableFuture<String> callAsync(String arguments, ToolContext context) {
				return CompletableFuture.supplyAsync(() -> {
					//Get injected state from context
					Map<String, Object> contextMap = context.getContext();

					//1. Read the current Agent status
					OverAllState state = (OverAllState) contextMap.get(AGENT_STATE_CONTEXT_KEY);
					if (state != null) {
						System.out.println("[Status reading] Current status:" + state.data());
					}

					// 2. read RunnableConfig
					RunnableConfig config = (RunnableConfig) contextMap.get(AGENT_CONFIG_CONTEXT_KEY);
					if (config != null) {
						System.out.println("[Configuration reading] ThreadId:" + config.threadId().orElse("default"));
					}

					//3. Get the status update Map (for writing updates)
					Map<String, Object> updateMap = (Map<String, Object>) contextMap.get(AGENT_STATE_FOR_UPDATE_CONTEXT_KEY);

					//Simulation calculation
					int currentTotal = 0;
					if (state != null) {
						Object totalObj = state.value("runningTotal").orElse(0);
						currentTotal = totalObj instanceof Integer ? (Integer) totalObj : 0;
					}

					// Assume the input is {"operation": "add", "value": 10}
					int newValue = 10; //Simplified example
					int newTotal = currentTotal + newValue;

					//4. Write status updates
					if (updateMap != null) {
						updateMap.put("runningTotal", newTotal);
						updateMap.put("lastOperation", "add");
						updateMap.put("lastValue", newValue);
						System.out.println("[Status Update] Write runningTotal=" + newTotal);
					}

					return "Calculation completed:" + currentTotal + " + " + newValue + " = " + newTotal;
				});
			}

			@Override
			public String call(String toolInput) {
				return callAsync(toolInput, new ToolContext(Map.of())).join();
			}
		};

		//Demonstrate the use of state awareness tools
		System.out.println("Demo state awareness tool (simulated Agent context):");

		//Simulate Agent injected context
		Map<String, Object> simulatedContext = new ConcurrentHashMap<>();
		//Simulated state (actually injected by AgentToolNode)
		// simulatedContext.put(AGENT_STATE_CONTEXT_KEY, state);
		// simulatedContext.put(AGENT_CONFIG_CONTEXT_KEY, config);
		Map<String, Object> updateMap = new ConcurrentHashMap<>();
		simulatedContext.put(AGENT_STATE_FOR_UPDATE_CONTEXT_KEY, updateMap);

		String result = stateAwareCalculator.callAsync("{\"operation\":\"add\",\"value\":10}", new ToolContext(simulatedContext))
			.join();

		System.out.println("Tool returns:" + result);
		System.out.println("Status Update Map:" + updateMap);

		System.out.println();
	}

	//==================== Example 4: Parallel Tool Execution Configuration ====================

	/**
	 * Example 4: Parallel tool execution configuration (AgentToolNode)
	 *
	 * <p>AgentToolNode supports executing multiple tool calls in parallel.Configurable through Builder:</p>
	 * <ul>
	 *   <li>parallelToolExecution(true) - Enable parallel execution</li>
	 *   <li>maxParallelTools(n) - maximum number of parallels</li>
	 *   <li>toolExecutionTimeout(duration) - Execution timeout</li>
	 * </ul>
	 */
	public void example4_parallelExecutionConfiguration() {
		System.out.println("=== Example 4: Parallel Tool Execution Configuration (AgentToolNode) ===\n");

		//Create multiple asynchronous tools
		AsyncToolCallback tool1 = createSimpleAsyncTool("async_tool_1", "Asynchronous Tools 1", 500);
		AsyncToolCallback tool2 = createSimpleAsyncTool("async_tool_2", "Async Tools 2", 800);
		AsyncToolCallback tool3 = createSimpleAsyncTool("async_tool_3", "Asynchronous Tools 3", 300);

		//Configure AgentToolNode to support parallel execution
		AgentToolNode parallelNode = AgentToolNode.builder()
			.agentName("parallel_agent")
			//Enable parallel execution
			.parallelToolExecution(true)
			//Run up to 5 tools simultaneously
			.maxParallelTools(5)
			//Maximum execution time of each tool is 2 minutes
			.toolExecutionTimeout(Duration.ofMinutes(2))
			//Registration tool
			.toolCallbacks(List.of(tool1, tool2, tool3))
			//exception handler
			.toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder()
				.alwaysThrow(false)
				.build())
			.build();

		System.out.println("AgentToolNode Configuration:");
		System.out.println("- Parallel execution: enabled");
		System.out.println("- Maximum number of parallels: 5");
		System.out.println("- Execution timeout: 2 minutes");
		System.out.println("- Number of registered tools: 3");
		System.out.println("- Tool list:" + parallelNode.getToolCallbacks().stream()
			.map(t -> t.getToolDefinition().name())
			.toList());

		//Sequential execution configuration example
		AgentToolNode sequentialNode = AgentToolNode.builder()
			.agentName("sequential_agent")
			//Disable parallel execution (default behavior)
			.parallelToolExecution(false)
			.toolCallbacks(List.of(tool1, tool2, tool3))
			.toolExecutionTimeout(Duration.ofMinutes(5))
			.toolExecutionExceptionProcessor(DefaultToolExecutionExceptionProcessor.builder()
				.alwaysThrow(false)
				.build())
			.build();

		System.out.println("\nExecute AgentToolNode configuration in sequence:");
		System.out.println("- Parallel execution: disabled");
		System.out.println("- Tools will be executed sequentially");

		System.out.println();
	}

	// ==================== Example 5:exist ReactAgent Using asynchronous tools in ====================

	/**
	 * Example 5: Using async tools with ReactAgent
	 *
	 * <p>ReactAgent supports asynchronous tools.When the Agent needs to call multiple tools,
	 * Parallel execution can be configured for increased efficiency.</p>
	 */
	public void example5_asyncToolsInReactAgent() throws GraphRunnerException {
		System.out.println("=== Example 5: Using asynchronous tools with ReactAgent ===\n");

		//Create an asynchronous weather query tool
		AsyncToolCallback asyncWeatherTool = new AsyncToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("async_get_weather")
					.description("Asynchronously obtain weather information for a specified city")
					.inputSchema("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\",\"description\":\"city ​​name\"}},\"required\":[\"city\"]}")
					.build();
			}

			@Override
			public CompletableFuture<String> callAsync(String arguments, ToolContext context) {
				return CompletableFuture.supplyAsync(() -> {
					System.out.println("[Asynchronous tool] Querying weather...");
					try {
						Thread.sleep(500); //Simulate API calls
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					return "{\"city\": \"Beijing\", \"temperature\": 25, \"condition\": \"sunny\"}";
				});
			}

			@Override
			public String call(String toolInput) {
				return callAsync(toolInput, new ToolContext(Map.of())).join();
			}
		};

		//Create an asynchronous stock query tool
		AsyncToolCallback asyncStockTool = new AsyncToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name("async_get_stock")
					.description("Get stock price asynchronously")
					.inputSchema("{\"type\":\"object\",\"properties\":{\"symbol\":{\"type\":\"string\",\"description\":\"Stock code\"}},\"required\":[\"symbol\"]}")
					.build();
			}

			@Override
			public CompletableFuture<String> callAsync(String arguments, ToolContext context) {
				return CompletableFuture.supplyAsync(() -> {
					System.out.println("[Asynchronous tool] Querying stocks...");
					try {
						Thread.sleep(700); //Simulate API calls
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					return "{\"symbol\": \"BABA\", \"price\": 85.50, \"change\": \"+2.3%\"}";
				});
			}

			@Override
			public String call(String toolInput) {
				return callAsync(toolInput, new ToolContext(Map.of())).join();
			}
		};

		//Create ReactAgent and configure asynchronous tools
		ReactAgent agent = ReactAgent.builder()
			.name("async_tools_agent")
			.model(chatModel)
			.description("A smart assistant configured with asynchronous tools")
			.instruction("You are an intelligent assistant that can query weather and stock information asynchronously.When the user asks, use the appropriate tool.")
			//Add async tools
			.tools(asyncWeatherTool, asyncStockTool)
			//Configuration memory
			.saver(new MemorySaver())
			.build();

		System.out.println("ReactAgent configuration completed:");
		System.out.println("- Number of asynchronous tools: 2");
		System.out.println("- Description: ReactAgent automatically handles the execution of asynchronous tools");

		//Call Agent
		RunnableConfig config = RunnableConfig.builder()
			.threadId("async_tools_session")
			.build();

		System.out.println("\nCalling Agent (query weather)...");
		Optional<OverAllState> result = agent.invoke("How is the weather in Beijing today?", config);

		if (result.isPresent()) {
			System.out.println("Agent execution successful");
		}

		System.out.println();
	}

	//==================== Example 6: Advanced usage of cancellation token ====================

	/**
	 * Example 6: Advanced usage of cancellation token (CancellationToken)
	 *
	 * <p>CancellationToken provides a flexible cancellation mechanism:</p>
	 * <ul>
	 *   <li>DefaultCancellationToken.linkedTo() - Link to CompletableFuture</li>
	 *   <li>onCancel() - Register multiple cancellation callbacks</li>
	 *   <li>Impotence of callbacks - each callback is executed only once</li>
	 * </ul>
	 */
	public void example6_cancellationTokenAdvanced() {
		System.out.println("=== Example 6: Advanced usage of cancellation token (CancellationToken) ===\n");

		//6.1 Basic usage
		System.out.println("6.1 Basic cancellation token usage:");
		DefaultCancellationToken basicToken = new DefaultCancellationToken();

		System.out.println("Initial state - isCancelled:" + basicToken.isCancelled());

		//Register callback
		basicToken.onCancel(() -> System.out.println("[Callback 1] Cancel callback is triggered"));
		basicToken.onCancel(() -> System.out.println("[Callback 2] Clean up resources"));

		//trigger cancel
		basicToken.cancel();
		System.out.println("After cancellation - isCancelled:" + basicToken.isCancelled());

		//Repeat cancellation is idempotent
		basicToken.cancel(); //The callback will not be triggered repeatedly

		// 6.2 Link to CompletableFuture
		System.out.println("\n6.2 Link to CompletableFuture:");

		CompletableFuture<String> longRunningTask = new CompletableFuture<>();

		//Create a cancellation token linked to a Future
		DefaultCancellationToken linkedToken = DefaultCancellationToken.linkedTo(longRunningTask);

		linkedToken.onCancel(() -> System.out.println("[Link Token] Future is canceled, triggering cleanup"));

		System.out.println("  Future Before cancellation - token.isCancelled: " + linkedToken.isCancelled());

		//Canceling the Future will automatically trigger the token
		longRunningTask.cancel(true);

		//Need to wait briefly for the asynchronous callback to execute
		try {
			Thread.sleep(100);
		}
		catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		System.out.println("  Future after cancellation - token.isCancelled: " + linkedToken.isCancelled());

		//6.3 Register callback after cancellation
		System.out.println("\n6.3 Register callback after cancellation:");
		DefaultCancellationToken alreadyCancelled = new DefaultCancellationToken();
		alreadyCancelled.cancel();

		//The registered callback will be executed immediately after cancellation
		alreadyCancelled.onCancel(() -> System.out.println("[Delayed Registration] The callback is executed immediately (because it has been cancelled)"));

		//6.4 CancellationToken.NONE - A token that never cancels
		System.out.println("\n6.4 CancellationToken.NONE:");
		CancellationToken noneToken = CancellationToken.NONE;
		System.out.println("  NONE.isCancelled: " + noneToken.isCancelled()); //always false
		noneToken.onCancel(() -> System.out.println("This callback never executes")); //No action

		// 6.5 throwIfCancelled usage
		System.out.println("\n6.5 throwIfCancelled usage:");
		DefaultCancellationToken throwToken = new DefaultCancellationToken();
		throwToken.cancel();

		try {
			throwToken.throwIfCancelled();
		}
		catch (ToolCancelledException e) {
			System.out.println("  captured ToolCancelledException: " + e.getMessage());
		}

		System.out.println();
	}

	// ==================== Example 7：ToolStateCollector Advanced usage ====================

	/**
	 * Example 7: Advanced usage of ToolStateCollector
	 *
	 * <p>ToolStateCollector is used to collect and merge state updates during parallel tool execution.
	 * Each tool gets an isolated update map to avoid concurrency conflicts.</p>
	 *
	 * <p>Key features:</p>
	 * <ul>
	 *   <li>createToolUpdateMap() - Creates an isolated update map for a tool</li>
	 *   <li>discardToolUpdateMap() - discards updates to timeout tools</li>
	 *   <li>mergeAll() - merge all updates in index order</li>
	 * </ul>
	 */
	public void example7_toolStateCollector() {
		System.out.println("=== Example 7: Advanced usage of ToolStateCollector ===\n");

		//Define KeyStrategy (merge strategy)
		Map<String, KeyStrategy> keyStrategies = Map.of(
			"counter", KeyStrategy.APPEND,  //Additional strategy
			"lastUpdated", KeyStrategy.REPLACE  //Replacement strategy (default)
		);

		//Create ToolStateCollector (3 tools)
		ToolStateCollector collector = new ToolStateCollector(3, keyStrategies);

		System.out.println("7.1 Create an isolated update map:");

		//Create an isolated update map for each tool
		Map<String, Object> tool0Updates = collector.createToolUpdateMap(0);
		Map<String, Object> tool1Updates = collector.createToolUpdateMap(1);
		Map<String, Object> tool2Updates = collector.createToolUpdateMap(2);

		//Simulate parallel tool writing updates (each tool writes its own Map)
		System.out.println("  tool 0 write: counter=A, result=tool0_done");
		tool0Updates.put("counter", "A");
		tool0Updates.put("result", "tool0_done");
		tool0Updates.put("lastUpdated", "tool0");

		System.out.println("  tool 1 write: counter=B, data=from_tool1");
		tool1Updates.put("counter", "B");
		tool1Updates.put("data", "from_tool1");
		tool1Updates.put("lastUpdated", "tool1");

		System.out.println("  tool 2 write: counter=C, extra=info");
		tool2Updates.put("counter", "C");
		tool2Updates.put("extra", "info");
		tool2Updates.put("lastUpdated", "tool2");

		System.out.println("\n7.2 Check status:");
		System.out.println("Number of tools completed:" + collector.getCompletedCount());
		System.out.println("Has it been merged:" + collector.isMerged());

		//Merge all updates
		System.out.println("\n7.3 Merge all updates (mergeAll):");
		Map<String, Object> merged = collector.mergeAll();
		System.out.println("Combined results:" + merged);
		System.out.println("  - counter (APPEND): " + merged.get("counter")); // [A, B, C]
		System.out.println("  - lastUpdated (REPLACE): " + merged.get("lastUpdated")); //tool2 (last)

		System.out.println("\n7.4 Updates to the discard timeout tool:");

		//Create a new Collector to demonstrate discarding
		ToolStateCollector collector2 = new ToolStateCollector(2, null);
		Map<String, Object> goodTool = collector2.createToolUpdateMap(0);
		Map<String, Object> timeoutTool = collector2.createToolUpdateMap(1);

		goodTool.put("status", "success");
		timeoutTool.put("status", "partial"); //Suppose the tool times out

		//Updates to the discard timeout tool
		collector2.discardToolUpdateMap(1);
		System.out.println("Updates to Drop Tool 1");

		Map<String, Object> merged2 = collector2.mergeAll();
		System.out.println("Combined results (excluding timeout tools):" + merged2);

		System.out.println("\n7.5 mergeAll can only be called once:");
		try {
			collector.mergeAll(); //Calling again will throw an exception
		}
		catch (IllegalStateException e) {
			System.out.println("Exception caught:" + e.getMessage());
		}

		System.out.println();
	}

	// ==================== Helper methods ====================

	/**
	 * Create simple asynchronous tools (for example)
	 */
	private AsyncToolCallback createSimpleAsyncTool(String name, String description, int delayMs) {
		return new AsyncToolCallback() {
			@Override
			public ToolDefinition getToolDefinition() {
				return DefaultToolDefinition.builder()
					.name(name)
					.description(description)
					.inputSchema("{\"type\":\"object\"}")
					.build();
			}

			@Override
			public CompletableFuture<String> callAsync(String arguments, ToolContext context) {
				return CompletableFuture.supplyAsync(() -> {
					try {
						Thread.sleep(delayMs);
					}
					catch (InterruptedException e) {
						Thread.currentThread().interrupt();
					}
					return name + "Execution completed (time consuming" + delayMs + "ms)";
				});
			}

			@Override
			public String call(String toolInput) {
				return callAsync(toolInput, new ToolContext(Map.of())).join();
			}
		};
	}

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("=== Async Tool Execution Examples ===\n");
		System.out.println("Issue #3988 - Asynchronous tool support feature demonstration\n");
		System.out.println("================================================\n");

		try {
			//Example 1: Basic asynchronous tools
			example1_basicAsyncTool();

			//Example 2: Cancellable asynchronous tools
			example2_cancellableAsyncTool();

			//Example 3: State-aware asynchronous tools
			example3_stateAwareAsyncTool();

			//Example 4: Parallel tool execution configuration
			example4_parallelExecutionConfiguration();

			//Example 5: Using async tools with ReactAgent
			example5_asyncToolsInReactAgent();

			//Example 6: Advanced cancellation token usage
			example6_cancellationTokenAdvanced();

			//Example 7: Advanced usage of ToolStateCollector
			example7_toolStateCollector();

			System.out.println("================================================");
			System.out.println("All examples are executed!");
			System.out.println("================================================");

		}
		catch (Exception e) {
			System.err.println("An error occurred while executing the example:" + e.getMessage());
			e.printStackTrace();
		}
	}

}
