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
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.action.NodeActionWithConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.FlowAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Workflow Example
 *
 * Demonstrates how to build intelligent workflows using StateGraph, including:
 * 1. Defining custom Nodes
 * 2. Agent as Node
 * 3. Mixing Agent Nodes and regular Nodes
 * 4. Executing workflows
 *
 * Reference: advanced_doc/workflow.md
 */
public class WorkflowExample {

	private final ChatModel chatModel;

	public WorkflowExample(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/**
	 * Main method: run all examples
	 *
	 * Note: ChatModel instance must be configured to run
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
		WorkflowExample example = new WorkflowExample(chatModel);

		// Run all examples
		example.runAllExamples();
	}

	/**
	 * Example 1: Basic Node Definition
	 *
	 * Create a simple text processing Node
	 */
	public void example1_basicNode() {
		class TextProcessorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				// 1. Get input from state
				String input = state.value("query", "").toString();

				// 2. Execute business logic
				String processedText = input.toUpperCase().trim();

				// 3. Return updated state
				Map<String, Object> result = new HashMap<>();
				result.put("processed_text", processedText);
				return result;
			}
		}

		TextProcessorNode processor = new TextProcessorNode();
		System.out.println("Basic Node definition example completed");
	}

	/**
	 * Example 2: AI Node with Configuration
	 *
	 * Create a Node that calls an LLM
	 */
	public void example2_aiNode() {
		class QueryExpanderNode implements NodeActionWithConfig {
			private final ChatClient chatClient;
			private final PromptTemplate promptTemplate;

			public QueryExpanderNode(ChatClient.Builder chatClientBuilder) {
				this.chatClient = chatClientBuilder.build();
				this.promptTemplate = new PromptTemplate(
						"You are a search optimization expert. Please generate {number} different variants for the following query.\n" +
								"Original query: {query}\n\n" +
								"Query variants:\n"
				);
			}

			@Override
			public Map<String, Object> apply(OverAllState state, RunnableConfig config) throws Exception {
				// Get input parameters
				String query = state.value("query", "").toString();
				Integer number = (Integer) state.value("expanderNumber", 3);

				// Call LLM
				String result = chatClient.prompt()
						.user(user -> user
								.text(promptTemplate.getTemplate())
								.param("query", query)
								.param("number", number))
						.call()
						.content();

				// Process result
				String[] variants = result.split("\n");

				// Return updated state
				Map<String, Object> output = new HashMap<>();
				output.put("queryVariants", Arrays.asList(variants));
				return output;
			}
		}

		QueryExpanderNode expander = new QueryExpanderNode(ChatClient.builder(chatModel));
		System.out.println("AI Node example completed");
	}

	/**
	 * Example 3: Condition Evaluation Node
	 *
	 * Used for conditional branching in workflows
	 */
	public void example3_conditionNode() {
		class ConditionEvaluatorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				String input = state.value("input", "").toString().toLowerCase();

				// Decide routing based on input content
				String route;
				if (input.contains("error") || input.contains("exception")) {
					route = "error_handling";
				}
				else if (input.contains("data") || input.contains("analysis")) {
					route = "data_processing";
				}
				else if (input.contains("report") || input.contains("summary")) {
					route = "report_generation";
				}
				else {
					route = "default";
				}

				Map<String, Object> result = new HashMap<>();
				result.put("_condition_result", route);
				return result;
			}
		}

		ConditionEvaluatorNode evaluator = new ConditionEvaluatorNode();
		System.out.println("Condition evaluation Node example completed");
	}

	/**
	 * Example 4: Parallel Result Aggregation Node
	 *
	 * Used to collect and aggregate results from multiple parallel Nodes
	 */
	public void example4_aggregatorNode() {
		ParallelResultAggregatorNode aggregator = new ParallelResultAggregatorNode("merged_results");
		System.out.println("Aggregation Node example completed");
	}

	public static class ParallelResultAggregatorNode implements NodeAction {
		private final String outputKey;

		public ParallelResultAggregatorNode(String outputKey) {
			this.outputKey = outputKey;
		}

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			// Collect all parallel task results
			List<String> results = new ArrayList<>();

			// Assume parallel tasks store results in different keys
			state.value("result_1").ifPresent(r -> results.add(r.toString()));
			state.value("result_2").ifPresent(r -> results.add(r.toString()));
			state.value("result_3").ifPresent(r -> results.add(r.toString()));

			// Aggregate results
			String aggregatedResult = String.join("\n---\n", results);

			Map<String, Object> output = new HashMap<>();
			output.put(outputKey, aggregatedResult);
			return output;
		}
	}


	/**
	 * Example 5: Integrating Custom Nodes into StateGraph
	 *
	 * Build a workflow with custom Nodes
	 */
	public void example5_buildWorkflowWithCustomNodes() throws Exception {
		// Define state management strategies
		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("query", new ReplaceStrategy());
			strategies.put("processed_text", new ReplaceStrategy());
			strategies.put("queryVariants", new ReplaceStrategy());
			strategies.put("final_result", new ReplaceStrategy());
			return strategies;
		};

		// Create Node instances
		class TextProcessorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				String input = state.value("query", "").toString();
				String processed = input.toUpperCase().trim();
				return Map.of("processed_text", processed);
			}
		}

		class ConditionNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				String input = state.value("processed_text", "").toString();
				String route = input.length() > 10 ? "long" : "short";
				return Map.of("_condition_result", route);
			}
		}

		// Build StateGraph
		StateGraph graph = new StateGraph(keyStrategyFactory);

		// Add custom Nodes
		graph.addNode("processor", node_async(new TextProcessorNode()));
		graph.addNode("condition", node_async(new ConditionNode()));

		// Define edges (flow connections)
		graph.addEdge(StateGraph.START, "processor");
		graph.addEdge("processor", "condition");

		// Conditional edge: route based on condition node result
		graph.addConditionalEdges(
				"condition",
				edge_async(state -> state.value("_condition_result", "short").toString()),
				Map.of(
						"long", "processor",  // Long text gets reprocessed
						"short", StateGraph.END  // Short text ends
				)
		);

		System.out.println("Custom Node workflow built successfully");
	}

	/**
	 * Example 6: Agent as SubGraph Node
	 *
	 * Embed a ReactAgent into a workflow
	 */
	public void example6_agentAsNode() throws Exception {
		// Create a specialized data analysis Agent
		ReactAgent analysisAgent = ReactAgent.builder()
				.name("data_analyzer")
				.model(chatModel)
				.instruction("You are a data analysis expert responsible for analyzing data and providing insights. Please analyze the following input data:\n {input}")
				.outputKey("analysis_result")
				.build();

		// Create a report generation Agent
		ReactAgent reportAgent = ReactAgent.builder()
				.name("report_generator")
				.model(chatModel)
				.instruction("You are a report generation expert responsible for converting the analysis result \"{analysis_result}\" into a professional report")
				.outputKey("final_report")
				.build();

		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("input", new ReplaceStrategy());
			return strategies;
		};

		// Build a workflow containing Agents
		StateGraph workflow = new StateGraph(keyStrategyFactory);

		// Add Agent as SubGraph Node
		workflow.addNode(analysisAgent.name(), analysisAgent.asNode(
				true,                     // includeContents: whether to pass parent graph's message history
				false));

		workflow.addNode(reportAgent.name(), reportAgent.asNode(
				true,
				false));

		// Define flow
		workflow.addEdge(StateGraph.START, analysisAgent.name());
		workflow.addEdge(analysisAgent.name(), reportAgent.name());
		workflow.addEdge(reportAgent.name(), StateGraph.END);

		CompiledGraph compiledGraph = workflow.compile(CompileConfig.builder().build());
		NodeOutput lastOutput = compiledGraph.stream(Map.of("input", "Full year sales of 10 billion in 2025, gross margin 23%, net margin 13%. Full year sales of 8 billion in 2024, gross margin 20%, net margin 8%.")).doOnNext(output -> {
			if (output instanceof StreamingOutput<?> streamingOutput) {
				System.out.println("Output from node " + streamingOutput.node() + ": " + streamingOutput.message().getText());
			}
		}).blockLast();

		System.out.println("\n\nFinal result, containing all node states:\n" + lastOutput.state().data());
	}

	/**
	 * Example 7: Mixing Agent Nodes and Regular Nodes
	 *
	 * Combine Agents and custom Nodes in a workflow
	 */
	public void example7_hybridWorkflow() throws Exception {
		// Create Agent
		ReactAgent qaAgent = ReactAgent.builder()
				.name("qa_agent")
				.model(chatModel)
				.instruction("You are a Q&A expert responsible for answering the user's questions:\n {cleaned_input}")
				.outputKey("qa_result")
				.enableLogging(true)
				.build();

		// Create custom Nodes
		class PreprocessorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				String input = state.value("input", "").toString();
				String cleaned = input.trim().toLowerCase();
				return Map.of("cleaned_input", cleaned);
			}
		}

		class ValidatorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				Message message = (Message)state.value("qa_result").get();
				boolean isValid = message.getText().length() > 50; // Simple validation
				return Map.of("is_valid", isValid);
			}
		}

		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("input", new ReplaceStrategy());
			strategies.put("cleaned_input", new ReplaceStrategy());
			strategies.put("qa_result", new ReplaceStrategy());
			strategies.put("is_valid", new ReplaceStrategy());
			return strategies;
		};

		// Build hybrid workflow
		StateGraph workflow = new StateGraph(keyStrategyFactory);

		// Add regular Nodes
		workflow.addNode("preprocess", node_async(new PreprocessorNode()));
		workflow.addNode("validate", node_async(new ValidatorNode()));

		// Add Agent Node
		workflow.addNode(qaAgent.name(), qaAgent.asNode(
				true,
				false));

		// Define flow: preprocess -> Agent processing -> validate
		workflow.addEdge(StateGraph.START, "preprocess");
		workflow.addEdge("preprocess", qaAgent.name());
		workflow.addEdge(qaAgent.name(), "validate");

		// Conditional edge: end if validation passes, otherwise reprocess
		workflow.addConditionalEdges(
				"validate",
				edge_async(state -> (Boolean) state.value("is_valid", false) ? "end" : qaAgent.name()),
				Map.of("end", StateGraph.END, qaAgent.name(), qaAgent.name())
		);

		CompiledGraph compiledGraph = workflow.compile(CompileConfig.builder().build());
		NodeOutput lastOutput = compiledGraph.stream(Map.of("input", "Please explain the basic principles of quantum computing")).doOnNext(output -> {
			if (output instanceof StreamingOutput<?> streamingOutput) {
				if (streamingOutput.message() != null) {
					// steaming output from streaming llm node
					System.out.println("Streaming output from node " + streamingOutput.node() + ": " + streamingOutput.message().getText());
				} else {
					// output from normal node, investigate the state to get the node data
					System.out.println("Output from node " + streamingOutput.node() + ": " + streamingOutput.state().data());
				}
			}
		}).blockLast();

		System.out.println("\n\n\nFinal result, containing all node states:\n" + lastOutput.state().data());
	}

	/**
	 * Example 8: Executing a Workflow
	 *
	 * Compile and execute a StateGraph workflow
	 */
	public void example8_executeWorkflow() throws Exception {
		// Create a simple workflow
		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("input", new ReplaceStrategy());
			strategies.put("output", new ReplaceStrategy());
			return strategies;
		};

		StateGraph workflow = new StateGraph(keyStrategyFactory);

		class SimpleNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				String input = state.value("input", "").toString();
				return Map.of("output", "Processed: " + input);
			}
		}

		workflow.addNode("process", node_async(new SimpleNode()));
		workflow.addEdge(StateGraph.START, "process");
		workflow.addEdge("process", StateGraph.END);

		// Compile workflow
		CompileConfig compileConfig = CompileConfig.builder().build();
		CompiledGraph compiledGraph = workflow.compile(compileConfig);

		// Prepare input
		Map<String, Object> input = Map.of(
				"input", "Please analyze the 2024 AI industry development trends"
		);

		// Configure runtime parameters
		RunnableConfig runnableConfig = RunnableConfig.builder()
				.threadId("workflow-001")
				.build();

		// Execute workflow
		Optional<OverAllState> result = compiledGraph.invoke(input, runnableConfig);

		// Process results
		result.ifPresent(state -> {
			System.out.println("Input: " + state.value("input").orElse("none"));
			System.out.println("Output: " + state.value("output").orElse("none"));
		});

		System.out.println("Workflow execution completed");
	}

	/**
	 * Example 9: Multi-Agent Collaborative Workflow
	 *
	 * Build a complete research workflow
	 */
	private static final String RESEARCH_RESULT = """
			#### 1. Introduction
			AI Agent (Artificial Intelligence Agent) is one of the important research directions in the AI field in recent years. It refers to an intelligent system capable of perceiving the environment, making autonomous decisions, and taking actions to achieve specific goals. With the development of deep learning, reinforcement learning, and natural language processing, AI Agents have demonstrated tremendous potential across multiple domains.
			
			This report aims to comprehensively review the technological development, application scenarios, typical cases, and future trends of AI Agents, providing reference for related research and applications.
			
			---
			
			#### 2. Technological Development
			
			##### 2.1 Core Technologies
			- **Perception**: Through computer vision, speech recognition, and sensor data processing, AI Agents can understand the external environment.
			- **Decision-Making**: Based on reinforcement learning, rule engines, or large model reasoning, AI Agents can make optimal decisions in complex environments.
			- **Execution**: By integrating with physical devices (e.g., robots) or software systems (e.g., automation tools), AI Agents carry out task execution.
			- **Learning and Adaptation**: Using online learning and transfer learning techniques, AI Agents can continuously optimize their behavior.
			
			##### 2.2 Key Advances
			- **Large Model-Driven Agents**: LLM (Large Language Model)-based AI Agents have become a research hotspot, with projects like AutoGPT and BabyAGI demonstrating autonomous task decomposition and execution capabilities.
			- **Multi-Modal Fusion**: Combining text, image, audio, and other input modalities to enhance the Agent's environmental understanding.
			- **Human-Agent Collaboration**: Designing more natural human-computer interaction mechanisms to better integrate AI Agents into human workflows.
		
			""";

	public void example9_multiAgentResearchWorkflow() throws Exception {
		// Create tools (example)
		ToolCallback searchTool = FunctionToolCallback
				.builder("search", (args) -> RESEARCH_RESULT)
				.description("Search tool")
				.inputType(String.class)
				.build();

		ToolCallback analysisTool = FunctionToolCallback
				.builder("analysis", (args) -> "Analysis result")
				.description("Analysis tool")
				.inputType(String.class)
				.build();

		ToolCallback summaryTool = FunctionToolCallback
				.builder("summary", (args) -> "Summary result")
				.description("Summary tool.")
				.inputType(String.class)
				.build();

		// 1. Create information gathering Agent
		ReactAgent researchAgent = ReactAgent.builder()
				.name("researcher")
				.model(chatModel)
				.instruction("You are a research expert responsible for collecting and organizing relevant information. Please research the topic: {input}")
				.tools(searchTool)
				.outputKey("research_data")
				.enableLogging(true)
				.build();

		// 2. Create data analysis Agent
		ReactAgent analysisAgent = ReactAgent.builder()
				.name("analyst")
				.model(chatModel)
				.instruction("You are an analysis expert responsible for in-depth analysis of the research data on the topic \"{input}\". Data is as follows: \n\n {research_data}")
				.tools(analysisTool)
				.outputKey("analysis_result")
				.enableLogging(true)
				.build();

		// 3. Create summary Agent
		ReactAgent summaryAgent = ReactAgent.builder()
				.name("summarizer")
				.model(chatModel)
				.instruction("You are a summary expert responsible for distilling the analysis results into concise conclusions. Results:\n\n {analysis_result}")
				.tools(summaryTool)
				.outputKey("final_summary")
				.enableLogging(true)
				.build();

		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("input", new ReplaceStrategy());
			return strategies;
		};

		// 4. Build workflow
		StateGraph workflow = new StateGraph(keyStrategyFactory);

		// Add Agent nodes
		workflow.addNode(researchAgent.name(), researchAgent.asNode(
				true,    // Include message history
				false   // Do not return reasoning process
		));

		workflow.addNode(analysisAgent.name(), analysisAgent.asNode(
				true,
				false));

		workflow.addNode(summaryAgent.name(), summaryAgent.asNode(
				true,
				true    // Return full reasoning process
		));

		// Define sequential execution flow
		workflow.addEdge(StateGraph.START, researchAgent.name());
		workflow.addEdge(researchAgent.name(), analysisAgent.name());
		workflow.addEdge(analysisAgent.name(), summaryAgent.name());
		workflow.addEdge(summaryAgent.name(), StateGraph.END);


		CompiledGraph compiledGraph = workflow.compile(CompileConfig.builder().build());
		NodeOutput finaOutput = compiledGraph.stream(Map.of("input", "Help me create a research report on AI Agents")).doOnNext(output -> {
			if (output instanceof StreamingOutput<?> streamingOutput) {
				System.out.println("Output from node " + streamingOutput.node() + ": " + streamingOutput.message().getText());
			}
		}).blockLast();

		System.out.println("Multi-Agent research workflow built successfully");
		System.out.println("Final output: " + finaOutput.state().value("final_summary").orElse("none"));
	}

	/**
	 * Example 10: Using RoutingAgent as a Workflow Node
	 * START -> preprocess -> content_router (RoutingAgent) -> postprocess -> END
	 * Demonstrates how to integrate LlmRoutingAgent as a node in StateGraph,
	 * using the getStateGraph() method to embed it in a larger workflow
	 */
	public void example10_routingAgentAsNode() throws Exception {
		// Create specialized sub-Agents
		ReactAgent technicalAgent = ReactAgent.builder()
				.name("technical_writer")
				.model(chatModel)
				.description("Specializes in writing technical documentation and API docs")
				.instruction("You are a technical documentation expert who excels at transforming technical content into clear and easy-to-understand documents. Please process the following content:\n{content}")
				.outputKey("technical_output")
				.build();

		ReactAgent marketingAgent = ReactAgent.builder()
				.name("marketing_writer")
				.model(chatModel)
				.description("Specializes in writing marketing copy and product introductions")
				.instruction("You are a marketing copywriting expert who excels at creating compelling promotional content. Please process the following content:\n{content}")
				.outputKey("marketing_output")
				.build();

		ReactAgent customerAgent = ReactAgent.builder()
				.name("customer_support")
				.model(chatModel)
				.description("Specializes in handling customer inquiries and answering questions")
				.instruction("You are a customer service expert who excels at answering customer questions in a friendly and professional manner. Please handle the following inquiry:\n{content}")
				.outputKey("customer_output")
				.build();

		// Create routing Agent - for intelligent task distribution
		LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
				.name("content_router")
				.description("Intelligently routes to the appropriate expert based on content type")
				.model(chatModel)
				.subAgents(List.of(technicalAgent, marketingAgent, customerAgent))
//				.systemPrompt("You are responsible for analyzing the nature of input content and selecting the most appropriate expert to handle it.")
				.build();

		// Create preprocessor Node
		class PreprocessorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				String input = (String) state.value("input", "");
				// Clean and normalize input
				String cleaned = input.trim();
				System.out.println("Preprocessing: " + cleaned);
				return Map.of("content", cleaned);
			}
		}

		// Create postprocessor Node
		class PostprocessorNode implements NodeAction {
			@Override
			public Map<String, Object> apply(OverAllState state) throws Exception {
				// Independently get each Agent's output result
				StringBuilder mergedResult = new StringBuilder();

				// Check technical documentation Agent's output
				Optional<Object> technicalOutput = state.value("technical_output");
				if (technicalOutput.isPresent()) {
					GraphResponse response = (GraphResponse) technicalOutput.get();
					Map<String, Object> resultValue = (Map<String, Object>) response.resultValue().get();
					mergedResult.append("[Technical Doc] ").append(resultValue.get("technical_output")).append("\n");
				}

				// Check marketing copy Agent's output
				Optional<Object> marketingOutput = state.value("marketing_output");
				if (marketingOutput.isPresent()) {
					GraphResponse response = (GraphResponse) marketingOutput.get();
					Map<String, Object> resultValue = (Map<String, Object>) response.resultValue().get();
					mergedResult.append("[Marketing Copy] ").append(resultValue.get("marketing_output")).append("\n");
				}

				// Check customer service Agent's output
				Optional<Object> customerOutput = state.value("customer_output");
				if (customerOutput.isPresent()) {
					GraphResponse response = (GraphResponse) customerOutput.get();
					Map<String, Object> resultValue = (Map<String, Object>) response.resultValue().get();
					mergedResult.append("[Customer Service] ").append(resultValue.get("customer_output")).append("\n");
				}

				// Merge all non-empty results
				String result = mergedResult.length() > 0 ? mergedResult.toString().trim() : "No output";

				// Add formatting
				String formatted = "=== Processing Result ===\n" + result + "\n===============";
				System.out.println("Post-processing completed, merged " +
						(technicalOutput.isPresent() ? 1 : 0) +
						(marketingOutput.isPresent() ? 1 : 0) +
						(customerOutput.isPresent() ? 1 : 0) + " results");
				return Map.of("final_output", formatted);
			}
		}

		// Define state management strategies
		KeyStrategyFactory keyStrategyFactory = () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("input", new ReplaceStrategy());
			strategies.put("content", new ReplaceStrategy());
			strategies.put("final_output", new ReplaceStrategy());
			strategies.put("messages", new AppendStrategy());
			return strategies;
		};

		// Build workflow containing RoutingAgent
		StateGraph workflow = new StateGraph(keyStrategyFactory);

		// Add preprocessor node
		workflow.addNode("preprocess", node_async(new PreprocessorNode()));

		// Key: use asStateGraph() to add RoutingAgent as a sub-graph node into the workflow
		workflow.addNode(routingAgent.name(), routingAgent.asStateGraph());

		// Add postprocessor node
		workflow.addNode("postprocess", node_async(new PostprocessorNode()));

		// Define workflow flow
		workflow.addEdge(StateGraph.START, "preprocess");
		workflow.addEdge("preprocess", routingAgent.name());
		workflow.addEdge(routingAgent.name(), "postprocess");
		workflow.addEdge("postprocess", StateGraph.END);

		// Compile and execute workflow
		CompiledGraph compiledGraph = workflow.compile(CompileConfig.builder().build());

		System.out.println(compiledGraph.getGraph(GraphRepresentation.Type.PLANTUML).content());

		System.out.println("\n=== Test 1: Technical Question ===");
		// Track the currently outputting Agent to avoid mixed output from multiple Agents
		final String[] currentAgent = {null};
		NodeOutput result1 = compiledGraph.stream(
				Map.of("input", "How to implement Agent development using the CordonData framework?")
		).doOnNext(output -> {
			if (output instanceof StreamingOutput<?> streamingOutput) {
				if (streamingOutput.message() != null) {
					// Categorize output by agent attribute
					String agent = streamingOutput.agent();
					if (agent != null && !agent.equals(currentAgent[0])) {
						// When switching to a new Agent, add a newline and print Agent identifier
						if (currentAgent[0] != null) {
							System.out.println(); // End previous Agent's output
						}
						System.out.print("\n[" + agent + "] ");
						currentAgent[0] = agent;
					}
					System.out.print(streamingOutput.message().getText());
				}
			}
		}).blockLast();
		System.out.println("\n\nFinal output: " + result1.state().value("final_output").orElse("none"));

		System.out.println("\n=== Test 2: Marketing Content ===");
		currentAgent[0] = null; // Reset Agent identifier
		NodeOutput result2 = compiledGraph.stream(
				Map.of("input", "Write an engaging promotional copy for a new product")
		).doOnNext(output -> {
			if (output instanceof StreamingOutput<?> streamingOutput) {
				if (streamingOutput.message() != null) {
					// Categorize output by agent attribute
					String agent = streamingOutput.agent();
					if (agent != null && !agent.equals(currentAgent[0])) {
						// When switching to a new Agent, add a newline and print Agent identifier
						if (currentAgent[0] != null) {
							System.out.println(); // End previous Agent's output
						}
						System.out.print("\n[" + agent + "] ");
						currentAgent[0] = agent;
					}
					System.out.print(streamingOutput.message().getText());
				}
			}
		}).blockLast();
		System.out.println("\n\nFinal output: " + result2.state().value("final_output").orElse("none"));

		System.out.println("\n=== Test 3: Customer Inquiry ===");
		currentAgent[0] = null; // Reset Agent identifier
		NodeOutput result3 = compiledGraph.stream(
				Map.of("input", "When will my order arrive?")
		).doOnNext(output -> {
			if (output instanceof StreamingOutput<?> streamingOutput) {
				if (streamingOutput.message() != null) {
					// Categorize output by agent attribute
					String agent = streamingOutput.agent();
					if (agent != null && !agent.equals(currentAgent[0])) {
						// When switching to a new Agent, add a newline and print Agent identifier
						if (currentAgent[0] != null) {
							System.out.println(); // End previous Agent's output
						}
						System.out.print("\n[" + agent + "] ");
						currentAgent[0] = agent;
					}
					System.out.print(streamingOutput.message().getText());
				}
			}
		}).blockLast();
		System.out.println("\n\nFinal output: " + result3.state().value("final_output").orElse("none"));

		System.out.println("\nRoutingAgent as workflow node example completed");
	}

	private void printGraphRepresentation(FlowAgent agent) {
		GraphRepresentation representation = agent.getAndCompileGraph().getGraph(GraphRepresentation.Type.PLANTUML);
		System.out.println(representation.content());
	}

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("=== Workflow Examples ===\n");

		try {
			System.out.println("Example 1: Basic Node Definition");
			example1_basicNode();
			System.out.println();

			System.out.println("Example 2: AI Node with Configuration");
			example2_aiNode();
			System.out.println();

			System.out.println("Example 3: Condition Evaluation Node");
			example3_conditionNode();
			System.out.println();

			System.out.println("Example 4: Parallel Result Aggregation Node");
			example4_aggregatorNode();
			System.out.println();

			System.out.println("Example 5: Integrating Custom Nodes into StateGraph");
			example5_buildWorkflowWithCustomNodes();
			System.out.println();

			System.out.println("Example 6: Agent as SubGraph Node");
			example6_agentAsNode();
			System.out.println();
//
			System.out.println("Example 7: Mixing Agent Nodes and Regular Nodes");
			example7_hybridWorkflow();
			System.out.println();

			System.out.println("Example 8: Executing a Workflow");
			example8_executeWorkflow();
			System.out.println();

			System.out.println("Example 9: Multi-Agent Collaborative Workflow");
			example9_multiAgentResearchWorkflow();
			System.out.println();

			System.out.println("Example 10: Using RoutingAgent as a Workflow Node");
			example10_routingAgentAsNode();
			System.out.println();

		}
		catch (Exception e) {
			System.err.println("Error running example: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

