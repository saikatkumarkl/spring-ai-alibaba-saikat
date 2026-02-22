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
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.InterruptionMetadata;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.hip.HumanInTheLoopHook;
import com.alibaba.cloud.ai.graph.agent.hook.hip.ToolConfig;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Human-in-the-Loop Example
 *
 * Demonstrates how to use the Human-in-the-Loop Hook to add human oversight to Agent tool calls, including:
 * 1. Configuring interruptions and approvals
 * 2. Approve decision
 * 3. Edit decision
 * 4. Reject decision
 * 5. Handling multiple tool calls
 * 6. Human interruption with nested ReactAgent in Workflow
 * 7. Utility methods
 *
 * Reference documentation: advanced_doc/human-in-the-loop.md
 */
public class HumanInTheLoopExample {

	private final ChatModel chatModel;

	public HumanInTheLoopExample(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/**
	 * Utility method: approve all tool calls
	 */
	public static InterruptionMetadata approveAll(InterruptionMetadata interruptionMetadata) {
		InterruptionMetadata.Builder builder = InterruptionMetadata.builder()
				.nodeId(interruptionMetadata.node())
				.state(interruptionMetadata.state());

		interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
			builder.addToolFeedback(
					InterruptionMetadata.ToolFeedback.builder(toolFeedback)
							.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
							.build()
			);
		});

		return builder.build();
	}

	/**
	 * Utility method: reject all tool calls
	 */
	public static InterruptionMetadata rejectAll(InterruptionMetadata interruptionMetadata, String reason) {
		InterruptionMetadata.Builder builder = InterruptionMetadata.builder()
				.nodeId(interruptionMetadata.node())
				.state(interruptionMetadata.state());

		interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
			builder.addToolFeedback(
					InterruptionMetadata.ToolFeedback.builder(toolFeedback)
							.result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED)
							.description(reason)
							.build()
			);
		});

		return builder.build();
	}

	/**
	 * Utility method: edit parameters of a specific tool
	 */
	public static InterruptionMetadata editTool(
			InterruptionMetadata interruptionMetadata,
			String toolName,
			String newArguments) {
		InterruptionMetadata.Builder builder = InterruptionMetadata.builder()
				.nodeId(interruptionMetadata.node())
				.state(interruptionMetadata.state());

		interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
			if (toolFeedback.getName().equals(toolName)) {
				builder.addToolFeedback(
						InterruptionMetadata.ToolFeedback.builder(toolFeedback)
								.arguments(newArguments)
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.EDITED)
								.build()
				);
			}
			else {
				builder.addToolFeedback(
						InterruptionMetadata.ToolFeedback.builder(toolFeedback)
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
								.build()
				);
			}
		});

		return builder.build();
	}

	/**
	 * Main method: run all examples
	 *
	 * Note: A ChatModel instance must be configured to run
	 */
	public static void main(String[] args) {
		// Create DashScope API instance
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Create ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		if (chatModel == null) {
			System.err.println("Error: Please configure a ChatModel instance first");
			System.err.println("Please set the AI_DASHSCOPE_API_KEY environment variable");
			return;
		}

		// Create example instance
		HumanInTheLoopExample example = new HumanInTheLoopExample(chatModel);

		// Run all examples
		example.runAllExamples();
	}

	/**
	 * Example 1: Configure interruptions and basic usage
	 *
	 * Configure human approval for specific tools
	 */
	public void example1_basicConfiguration() {
		// Configure checkpoint saver (human-in-the-loop requires checkpoints to handle interruptions)
		MemorySaver memorySaver = new MemorySaver();

		// Create tool callbacks (examples)
		ToolCallback writeFileTool = FunctionToolCallback.builder("write_file", (args) -> "File written")
				.description("Write a file")
				.inputType(String.class)
				.build();

		ToolCallback executeSqlTool = FunctionToolCallback.builder("execute_sql", (args) -> "SQL executed")
				.description("Execute SQL statement")
				.inputType(String.class)
				.build();

		ToolCallback readDataTool = FunctionToolCallback.builder("read_data", (args) -> "Data read")
				.description("Read data")
				.inputType(String.class)
				.build();

		// Create Human-in-the-Loop Hook
		HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
				.approvalOn("write_file", ToolConfig.builder()
						.description("File write operation requires approval")
						.build())
				.approvalOn("execute_sql", ToolConfig.builder()
						.description("SQL execution operation requires approval")
						.build())
				.build();

		// Create Agent
		ReactAgent agent = ReactAgent.builder()
				.name("approval_agent")
				.model(chatModel)
				.tools(writeFileTool, executeSqlTool, readDataTool)
				.hooks(List.of(humanInTheLoopHook))
				.saver(memorySaver)
				.build();

		System.out.println("Human-in-the-Loop Hook configuration example completed");
	}

	/**
	 * Example 2: Approve decision
	 *
	 * Human approves tool call and continues execution
	 */
	public void example2_approveDecision() throws Exception {
		MemorySaver memorySaver = new MemorySaver();

		ToolCallback poetTool = FunctionToolCallback.builder("poem", (args) -> "The spring river tide connects with the sea, the bright moon over the sea rises with the tide...")
				.description("Poetry writing tool")
				.inputType(String.class)
				.build();

		HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
				.approvalOn("poem", ToolConfig.builder()
						.description("Please confirm the poetry creation operation")
						.build())
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("poet_agent")
				.model(chatModel)
				.tools(List.of(poetTool))
				.hooks(List.of(humanInTheLoopHook))
				.saver(memorySaver)
				.build();

		String threadId = "user-session-001";
		RunnableConfig config = RunnableConfig.builder()
				.threadId(threadId)
				.build();

		// First call - trigger interruption
		System.out.println("=== First call: expecting interruption ===");
		Optional<NodeOutput> result = agent.invokeAndGetOutput(
				"Help me write a poem of about 100 words",
				config
		);

		// Check for interruption and handle
		if (result.isPresent() && result.get() instanceof InterruptionMetadata) {
			InterruptionMetadata interruptionMetadata = (InterruptionMetadata) result.get();

			System.out.println("Interruption detected, human approval required");
			List<InterruptionMetadata.ToolFeedback> toolFeedbacks =
					interruptionMetadata.toolFeedbacks();

			for (InterruptionMetadata.ToolFeedback feedback : toolFeedbacks) {
				System.out.println("Tool: " + feedback.getName());
				System.out.println("Arguments: " + feedback.getArguments());
				System.out.println("Description: " + feedback.getDescription());
			}

			// Build approval feedback
			InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
					.nodeId(interruptionMetadata.node())
					.state(interruptionMetadata.state());

			// Set approval decision for each tool call
			interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
				InterruptionMetadata.ToolFeedback approvedFeedback =
						InterruptionMetadata.ToolFeedback.builder(toolFeedback)
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
								.build();
				feedbackBuilder.addToolFeedback(approvedFeedback);
			});

			InterruptionMetadata approvalMetadata = feedbackBuilder.build();

			// Resume execution with approval decision
			RunnableConfig resumeConfig = RunnableConfig.builder()
					.threadId(threadId) // Same thread ID to resume paused conversation
					.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, approvalMetadata)
					.build();

			// Second call to resume execution
			System.out.println("\n=== Second call: resume with approval decision ===");
			Optional<NodeOutput> finalResult = agent.invokeAndGetOutput("", resumeConfig);

			if (finalResult.isPresent()) {
				System.out.println("Execution completed");
			}
		}

		System.out.println("Approve decision example completed");
	}

	/**
	 * Example 3: Edit decision
	 *
	 * Human edits tool parameters and continues execution
	 */
	public void example3_editDecision() throws Exception {
		MemorySaver memorySaver = new MemorySaver();

		ToolCallback executeSqlTool = FunctionToolCallback.builder("execute_sql", (args) -> "SQL execution result")
				.description("Execute SQL statement")
				.inputType(String.class)
				.build();

		HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
				.approvalOn("execute_sql", ToolConfig.builder()
						.description("SQL execution operation requires approval")
						.build())
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("sql_agent")
				.model(chatModel)
				.tools(executeSqlTool)
				.hooks(List.of(humanInTheLoopHook))
				.saver(memorySaver)
				.build();

		String threadId = "sql-session-001";
		RunnableConfig config = RunnableConfig.builder()
				.threadId(threadId)
				.build();

		// First call - trigger interruption
		Optional<NodeOutput> result = agent.invokeAndGetOutput(
				"Delete old records from the database",
				config
		);

		if (result.isPresent() && result.get() instanceof InterruptionMetadata) {
			InterruptionMetadata interruptionMetadata = (InterruptionMetadata) result.get();

			// Build edit feedback
			InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
					.nodeId(interruptionMetadata.node())
					.state(interruptionMetadata.state());

			interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
				// Modify tool parameters
				String editedArguments = toolFeedback.getArguments()
						.replace("DELETE FROM records", "DELETE FROM old_records");

				InterruptionMetadata.ToolFeedback editedFeedback =
						InterruptionMetadata.ToolFeedback.builder(toolFeedback)
								.arguments(editedArguments)
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.EDITED)
								.build();
				feedbackBuilder.addToolFeedback(editedFeedback);
			});

			InterruptionMetadata editMetadata = feedbackBuilder.build();

			// Resume execution with edit decision
			RunnableConfig resumeConfig = RunnableConfig.builder()
					.threadId(threadId)
					.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, editMetadata)
					.build();

			Optional<NodeOutput> finalResult = agent.invokeAndGetOutput("", resumeConfig);

			System.out.println("Edit decision example completed");
		}
	}

	/**
	 * Example 4: Reject decision
	 *
	 * Human rejects tool call and terminates current process
	 */
	public void example4_rejectDecision() throws Exception {
		MemorySaver memorySaver = new MemorySaver();

		ToolCallback deleteTool = FunctionToolCallback.builder("delete_data", (args) -> "Data deleted")
				.description("Delete data")
				.inputType(String.class)
				.build();

		HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
				.approvalOn("delete_data", ToolConfig.builder()
						.description("Delete operation requires approval")
						.build())
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("delete_agent")
				.model(chatModel)
				.tools(deleteTool)
				.hooks(List.of(humanInTheLoopHook))
				.saver(memorySaver)
				.build();

		String threadId = "delete-session-001";
		RunnableConfig config = RunnableConfig.builder()
				.threadId(threadId)
				.build();

		// First call - trigger interruption
		Optional<NodeOutput> result = agent.invokeAndGetOutput(
				"Delete all user data",
				config
		);

		if (result.isPresent() && result.get() instanceof InterruptionMetadata) {
			InterruptionMetadata interruptionMetadata = (InterruptionMetadata) result.get();

			// Build reject feedback
			InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
					.nodeId(interruptionMetadata.node())
					.state(interruptionMetadata.state());

			interruptionMetadata.toolFeedbacks().forEach(toolFeedback -> {
				InterruptionMetadata.ToolFeedback rejectedFeedback =
						InterruptionMetadata.ToolFeedback.builder(toolFeedback)
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED)
								.description("Deletion not allowed, please use the archive feature instead.")
								.build();
				feedbackBuilder.addToolFeedback(rejectedFeedback);
			});

			InterruptionMetadata rejectMetadata = feedbackBuilder.build();

			// Resume execution with reject decision
			RunnableConfig resumeConfig = RunnableConfig.builder()
					.threadId(threadId)
					.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, rejectMetadata)
					.build();

			Optional<NodeOutput> finalResult = agent.invokeAndGetOutput("", resumeConfig);

			System.out.println("Reject decision example completed");
		}
	}

	/**
	 * Example 5: Handling multiple tool calls
	 *
	 * Handle multiple tool calls requiring approval at once
	 * Using {@code @Tool} annotation to define tools
	 */
	public void example5_multipleTools() throws Exception {
		MemorySaver memorySaver = new MemorySaver();

		// Define multiple tools using @Tool annotation
		MultiTools multiTools = new MultiTools();

		HumanInTheLoopHook humanInTheLoopHook = HumanInTheLoopHook.builder()
				.approvalOn("tool1", ToolConfig.builder().description("Tool 1 requires approval").build())
				.approvalOn("tool2", ToolConfig.builder().description("Tool 2 requires approval").build())
				.approvalOn("tool3", ToolConfig.builder().description("Tool 3 requires approval").build())
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("multi_tool_agent")
				.model(chatModel)
				.methodTools(multiTools)
				.hooks(List.of(humanInTheLoopHook))
				.saver(memorySaver)
				.build();

		String threadId = "multi-session-001";
		RunnableConfig config = RunnableConfig.builder()
				.threadId(threadId)
				.build();

		Optional<NodeOutput> result = agent.invokeAndGetOutput("Execute all tools", config);

		if (result.isPresent() && result.get() instanceof InterruptionMetadata) {
			InterruptionMetadata interruptionMetadata = (InterruptionMetadata) result.get();

			InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
					.nodeId(interruptionMetadata.node())
					.state(interruptionMetadata.state());

			List<InterruptionMetadata.ToolFeedback> feedbacks = interruptionMetadata.toolFeedbacks();

			// First tool: approve
			if (feedbacks.size() > 0) {
				feedbackBuilder.addToolFeedback(
						InterruptionMetadata.ToolFeedback.builder(feedbacks.get(0))
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
								.build()
				);
			}

			// Second tool: edit
			if (feedbacks.size() > 1) {
				feedbackBuilder.addToolFeedback(
						InterruptionMetadata.ToolFeedback.builder(feedbacks.get(1))
								.arguments("{\"param\": \"new_value\"}")
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.EDITED)
								.build()
				);
			}

			// Third tool: reject
			if (feedbacks.size() > 2) {
				feedbackBuilder.addToolFeedback(
						InterruptionMetadata.ToolFeedback.builder(feedbacks.get(2))
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.REJECTED)
								.description("This operation is not allowed")
								.build()
				);
			}

			InterruptionMetadata decisionsMetadata = feedbackBuilder.build();

			RunnableConfig resumeConfig = RunnableConfig.builder()
					.threadId(threadId)
					.addMetadata(RunnableConfig.HUMAN_FEEDBACK_METADATA_KEY, decisionsMetadata)
					.build();

			Optional<NodeOutput> outputOptional = agent.invokeAndGetOutput("", resumeConfig);

			System.out.println("Multiple decisions example completed, final state:\n\n" + outputOptional.get().state());
		}
	}

	/**
	 * Example 6: Human interruption with nested ReactAgent in Workflow
	 *
	 * Demonstrates how to nest a ReactAgent with HumanInTheLoopHook within a StateGraph workflow,
	 * and handle interruptions and resumptions during workflow execution
	 */
	public void example6_workflowWithHumanInTheLoop() throws Exception {
		// Create tool callback
		ToolCallback searchTool = FunctionToolCallback
				.builder("search", (args) -> "Search result: AI Agent is an intelligent system that can perceive the environment, make autonomous decisions, and take actions.")
				.description("Search tool for finding relevant information")
				.inputType(String.class)
				.build();

		// Configure checkpoint saver (human-in-the-loop requires checkpoints to handle interruptions)
		MemorySaver saver = new MemorySaver();

		// Create ReactAgent with Human-in-the-Loop Hook
		ReactAgent qaAgent = ReactAgent.builder()
				.name("qa_agent")
				.model(chatModel)
				.instruction("You are a Q&A expert, responsible for answering user questions. If you need to search for information, use the search tool.\nUser question: {cleaned_input}")
				.outputKey("qa_result")
				.saver(saver)
				.hooks(HumanInTheLoopHook.builder()
						.approvalOn("search", ToolConfig.builder()
								.description("Search operation requires human approval, please confirm whether to execute the search")
								.build())
						.build())
				.tools(searchTool)
				.enableLogging(true)
				.build();

		// Create preprocessor Node: clean input
		class PreprocessorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				String input = state.value("input", "").toString();
				String cleaned = input.trim();
				System.out.println("Preprocessor node: clean input -> " + cleaned);
				return Map.of("cleaned_input", cleaned);
			}
		}

		// Create validator Node: validate result quality
		class ValidatorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				Optional<Object> qaResultOpt = state.value("qa_result");
				if (qaResultOpt.isPresent() && qaResultOpt.get() instanceof Message message) {
					boolean isValid = message.getText().length() > 30; // Simple validation: answer length must be greater than 30
					System.out.println("Validator node: result validation -> " + (isValid ? "passed" : "failed"));
					return Map.of("is_valid", isValid);
				}
				return Map.of("is_valid", false);
			}
		}

		// Define state management strategies
		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("input", new ReplaceStrategy());
			strategies.put("cleaned_input", new ReplaceStrategy());
			strategies.put("qa_result", new ReplaceStrategy());
			strategies.put("is_valid", new ReplaceStrategy());
			return strategies;
		};

		// Build workflow
		StateGraph workflow = new StateGraph(keyStrategyFactory);

		// Add regular Nodes
		workflow.addNode("preprocess", node_async(new PreprocessorNode()));
		workflow.addNode("validate", node_async(new ValidatorNode()));

		// Add Agent Node (nested ReactAgent)
		workflow.addNode(qaAgent.name(), qaAgent.asNode(
				true,   // includeContents: pass parent graph's message history
				false   // includeReasoning: do not return reasoning process
		));

		// Define flow: preprocess -> Agent processing -> validate
		workflow.addEdge(StateGraph.START, "preprocess");
		workflow.addEdge("preprocess", qaAgent.name());
		workflow.addEdge(qaAgent.name(), "validate");

		// Conditional edge: end if validation passes, otherwise reprocess
		workflow.addConditionalEdges(
				"validate",
				edge_async(state -> {
					Boolean isValid = (Boolean) state.value("is_valid", false);
					return isValid ? "end" : qaAgent.name();
				}),
				Map.of(
						"end", StateGraph.END,
						qaAgent.name(), qaAgent.name()
				)
		);

		// Compile workflow
		CompiledGraph compiledGraph = workflow.compile(
				CompileConfig.builder()
						.saverConfig(SaverConfig.builder().register(saver).build())
						.build()
		);

		String threadId = "workflow-hilt-001";
		Map<String, Object> input = Map.of("input", "Please explain the basic principles of quantum computing");

		// First call - may trigger interruption
		System.out.println("=== First call to workflow: may trigger interruption ===");
		Optional<NodeOutput> nodeOutputOptional = compiledGraph.invokeAndGetOutput(
				input,
				RunnableConfig.builder().threadId(threadId).build()
		);

		// Check if interruption occurred
		if (nodeOutputOptional.isPresent() && nodeOutputOptional.get() instanceof InterruptionMetadata interruptionMetadata) {
			System.out.println("\nWorkflow interrupted, waiting for human review.");
			System.out.println("Interrupted node: " + interruptionMetadata.node());
			System.out.println("Interrupted state: " + interruptionMetadata.state());

			List<InterruptionMetadata.ToolFeedback> feedbacks = interruptionMetadata.toolFeedbacks();
			System.out.println("Number of tool calls requiring approval: " + feedbacks.size());

			// Display all tool calls requiring approval
			for (InterruptionMetadata.ToolFeedback feedback : feedbacks) {
				System.out.println("\nTool name: " + feedback.getName());
				System.out.println("Tool arguments: " + feedback.getArguments());
				System.out.println("Tool description: " + feedback.getDescription());
			}

			// Build human feedback (approve all tool calls)
			InterruptionMetadata.Builder feedbackBuilder = InterruptionMetadata.builder()
					.nodeId(interruptionMetadata.node())
					.state(interruptionMetadata.state());

			// Set approval decision for each tool call
			feedbacks.forEach(toolFeedback -> {
				feedbackBuilder.addToolFeedback(
						InterruptionMetadata.ToolFeedback.builder(toolFeedback)
								.result(InterruptionMetadata.ToolFeedback.FeedbackResult.APPROVED)
								.build()
				);
			});

			InterruptionMetadata approvalMetadata = feedbackBuilder.build();

			// Resume execution with approval decision
			System.out.println("\n=== Second call: resume workflow with approval decision ===");
			RunnableConfig resumableConfig = RunnableConfig.builder()
					.threadId(threadId)
					.addHumanFeedback(approvalMetadata)
					.build();

			nodeOutputOptional = compiledGraph.invokeAndGetOutput(Map.of(), resumableConfig);
			System.out.println("\nHuman interruption with nested ReactAgent in workflow example completed");

		}

	}

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("=== Human-in-the-Loop Examples ===\n");

		try {
			System.out.println("Example 1: Configure interruptions and basic usage");
			example1_basicConfiguration();
			System.out.println();

			System.out.println("Example 2: Approve decision");
			example2_approveDecision();
			System.out.println();

			System.out.println("Example 3: Edit decision");
			example3_editDecision();
			System.out.println();

			System.out.println("Example 4: Reject decision");
			example4_rejectDecision();
			System.out.println();

			System.out.println("Example 5: Handle multiple tool call decisions");
			example5_multipleTools();
			System.out.println();

			System.out.println("Example 6: Human interruption with nested ReactAgent in Workflow");
			example6_workflowWithHumanInTheLoop();
			System.out.println();

		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Multi-tool class defined using @Tool annotations, for Example 5
	 */
	static class MultiTools {

		@Tool(name = "tool1", description = "Tool 1")
		public String tool1(@ToolParam(description = "Input content for Tool 1 simulated approval") String content) {
			return "Tool 1 result";
		}

		@Tool(name = "tool2", description = "Tool 2")
		public String tool2(@ToolParam(description = "Input content for Tool 2 simulated approval") String content) {
			return "Tool 2 result";
		}

		@Tool(name = "tool3", description = "Tool 3")
		public String tool3(@ToolParam(description = "Input content for Tool 3 simulated approval") String content) {
			return "Tool 3 result";
		}
	}
}

