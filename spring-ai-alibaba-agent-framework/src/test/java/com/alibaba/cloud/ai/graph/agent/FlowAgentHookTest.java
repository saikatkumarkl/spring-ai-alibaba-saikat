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
package com.alibaba.cloud.ai.graph.agent;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.flow.agent.*;
import com.alibaba.cloud.ai.graph.agent.flow.agent.loop.CountLoopStrategy;
import com.alibaba.cloud.ai.graph.agent.utils.HookFactory;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test class for FlowAgent subclasses with Hook functionality.
 *
 * @author haojun.phj (Jackie)
 * @since 2025/01/13
 */
@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
public class FlowAgentHookTest {

	private ChatModel chatModel;

	@BeforeEach
	public void setUp() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();
	}

	@Test
	public void testSupervisorAgentWithHook() throws Exception {
		System.out.println("\n========== Testing SupervisorAgent with Hook ==========\n");

		// Create sub-agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing various articles")
				.instruction("You are a well-known writer, good at writing poems, within 20 words.")
				.outputKey("writer_output")
				.enableLogging(false)
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translation")
				.instruction("You are a professional translator, within 20 words.")
				.outputKey("translator_output")
				.enableLogging(false)
				.build();

		// Create SupervisorAgent with hook (mainAgent is required)
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("content management supervisor")
				.model(chatModel)
				.mainAgent(ReactAgent.builder()
						.name("main_agent")
						.model(chatModel)
						.description("Supervisor main agent, responsible for routing decisions")
						.systemPrompt("""
							You are an intelligent content processing supervisor.
							Available sub-agents: writer_agent (writing), translator_agent (translation)
							Only return Agent name or FINISH, do not include any other explanation.
							""")
						.instruction("The user's request is: {input}")
						.outputKey("final_output")
						.build())
				.subAgents(List.of(writerAgent, translatorAgent))
				.hooks(List.of(HookFactory.createLogAgentHook(), HookFactory.createLogModelHook()))
				.systemPrompt("""
					You are an intelligent content processing supervisor.
					Available sub-agents: writer_agent (writing), translator_agent (translation)
					Only return Agent name or FINISH, do not include any other explanation.
					""")
				.build();

		try {
			// Execute the agent
			Optional<OverAllState> result = supervisorAgent.invoke("Help me write a poem about spring");

			assertTrue(result.isPresent(), "Result should be present");

			// Print Mermaid graph representation
			GraphRepresentation mermaidGraph = supervisorAgent.getGraph()
					.getGraph(GraphRepresentation.Type.MERMAID);
			assertNotNull(mermaidGraph, "Mermaid graph should not be null");
			System.out.println("\n=== SupervisorAgent Mermaid Graph ===");
			System.out.println(mermaidGraph.content());
			System.out.println("=====================================\n");

		} catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent with hook execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testSequentialAgentWithHook() throws Exception {
		System.out.println("\n========== Testing SequentialAgent with Hook ==========\n");

		// Create sub-agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Professional Writing Agent")
				.instruction("You are a well-known writer who is good at writing poetry.Please answer according to the user's question: {input}, within 20 words.")
				.outputKey("article")
				.enableLogging(false)
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Professional Review Agent")
				.instruction("You are a well-known critic.Poetry to be commented: {article}, within 20 words.")
				.outputKey("reviewed_article")
				.enableLogging(false)
				.build();

		// Create SequentialAgent with dynamically created hooks
		SequentialAgent sequentialAgent = SequentialAgent.builder()
				.name("writing_workflow")
				.description("Writing workflow: write the poem first, then review it")
				.subAgents(List.of(writerAgent, reviewerAgent))
				.hooks(List.of(HookFactory.createLogAgentHook(), HookFactory.createLogModelHook()))
				.build();

		try {
			// Execute the agent
			Optional<OverAllState> result = sequentialAgent.invoke("Write a poem about spring");

			assertTrue(result.isPresent(), "Result should be present");

			System.out.println("result=" + result.get());

			// Print Mermaid graph representation
			GraphRepresentation mermaidGraph = sequentialAgent.getGraph()
					.getGraph(GraphRepresentation.Type.MERMAID);
			assertNotNull(mermaidGraph, "Mermaid graph should not be null");
			System.out.println("\n=== SequentialAgent Mermaid Graph ===");
			System.out.println(mermaidGraph.content());
			System.out.println("======================================\n");

		} catch (Exception e) {
			e.printStackTrace();
			fail("SequentialAgent with hook execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testLoopAgentWithHook() throws Exception {
		System.out.println("\n========== Testing LoopAgent with Hook ==========\n");

		// Create a simple agent for loop
		ReactAgent processorAgent = ReactAgent.builder()
				.name("processor_agent")
				.model(chatModel)
				.description("Data processing agent")
				.instruction("You are a data processing expert.Please process: {input}, within 20 words.")
				.outputKey("processed_data")
				.enableLogging(false)
				.build();

		// Create LoopAgent with dynamically created hooks
		LoopAgent loopAgent = LoopAgent.builder()
				.name("loop_processor")
				.description("Loop processing workflow")
				.subAgent(processorAgent)
				.hooks(List.of(HookFactory.createLogAgentHook(), HookFactory.createLogModelHook()))
				.loopStrategy(new CountLoopStrategy(2))
				.build();

		try {
			// Execute the agent
			Optional<OverAllState> result = loopAgent.invoke("Process data");

			assertTrue(result.isPresent(), "Result should be present");

			System.out.println("result=" + result.get());
			// Print Mermaid graph representation
			GraphRepresentation mermaidGraph = loopAgent.getGraph()
					.getGraph(GraphRepresentation.Type.MERMAID);
			assertNotNull(mermaidGraph, "Mermaid graph should not be null");
			System.out.println("\n=== LoopAgent Mermaid Graph ===");
			System.out.println(mermaidGraph.content());
			System.out.println("================================\n");

		} catch (Exception e) {
			e.printStackTrace();
			fail("LoopAgent with hook execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testParallelAgentWithHook() throws Exception {
		System.out.println("\n========== Testing ParallelAgent with Hook ==========\n");

		// Create multiple agents for parallel execution
		ReactAgent agent1 = ReactAgent.builder()
				.name("agent_1")
				.model(chatModel)
				.description("Process Agent 1")
				.instruction("You are processor 1.Please process: {input}, within 20 words.")
				.outputKey("output_1")
				.enableLogging(false)
				.build();

		ReactAgent agent2 = ReactAgent.builder()
				.name("agent_2")
				.model(chatModel)
				.description("Process Agent 2")
				.instruction("You are processor 2.Please process: {input}, within 20 words.")
				.outputKey("output_2")
				.enableLogging(false)
				.build();

		ReactAgent agent3 = ReactAgent.builder()
				.name("agent_3")
				.model(chatModel)
				.description("Handling Agent 3")
				.instruction("You are processor 3.Please process: {input}, within 20 words.")
				.outputKey("output_3")
				.enableLogging(false)
				.build();

		// Create ParallelAgent with dynamically created hooks
		ParallelAgent parallelAgent = ParallelAgent.builder()
				.name("parallel_processor")
				.description("Parallel processing workflows")
				.subAgents(List.of(agent1, agent2, agent3))
				.hooks(List.of(HookFactory.createLogAgentHook(), HookFactory.createLogModelHook()))
				.maxConcurrency(3)
				.build();

		try {
			// Execute the agent
			Optional<OverAllState> result = parallelAgent.invoke("Parallel processing tasks");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("result=" + result.get());

			// Print Mermaid graph representation
			GraphRepresentation mermaidGraph = parallelAgent.getGraph()
					.getGraph(GraphRepresentation.Type.MERMAID);
			assertNotNull(mermaidGraph, "Mermaid graph should not be null");
			System.out.println("\n=== ParallelAgent Mermaid Graph ===");
			System.out.println(mermaidGraph.content());
			System.out.println("====================================\n");

		} catch (Exception e) {
			e.printStackTrace();
			fail("ParallelAgent with hook execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testLlmRoutingAgentWithHook() throws Exception {
		System.out.println("\n========== Testing LlmRoutingAgent with Hook ==========\n");

		// Create sub-agents for routing
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("good at writing")
				.instruction("You are a well-known writer, 20 words or less.")
				.outputKey("writer_output")
				.enableLogging(false)
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translation")
				.instruction("You are a professional translator, within 20 words.")
				.outputKey("translator_output")
				.enableLogging(false)
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Good at reviewing")
				.instruction("You are a well-known critic, 20 words or less.")
				.outputKey("reviewer_output")
				.enableLogging(false)
				.build();

		// Create LlmRoutingAgent with dynamically created hooks
		LlmRoutingAgent llmRoutingAgent = LlmRoutingAgent.builder()
				.name("llm_router")
				.description("Intelligent routing agent")
				.model(chatModel)
				.subAgents(List.of(writerAgent, translatorAgent, reviewerAgent))
				.hooks(List.of(HookFactory.createLogAgentHook(), HookFactory.createLogModelHook()))
				.fallbackAgent("writer_agent")
				.systemPrompt("""
					You are an intelligent router.
					Available agents: writer_agent (writing), translator_agent (translation), reviewer_agent (review)
					Only return Agent name or FINISH, do not include any other explanation.
					""")
				.build();

		try {
			// Execute the agent
			Optional<OverAllState> result = llmRoutingAgent.invoke("write me a poem");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("result=" + result.get());

			// Print Mermaid graph representation
			GraphRepresentation mermaidGraph = llmRoutingAgent.getGraph()
					.getGraph(GraphRepresentation.Type.MERMAID);
			assertNotNull(mermaidGraph, "Mermaid graph should not be null");
			System.out.println("\n=== LlmRoutingAgent Mermaid Graph ===");
			System.out.println(mermaidGraph.content());
			System.out.println("======================================\n");

		} catch (Exception e) {
			e.printStackTrace();
			fail("LlmRoutingAgent with hook execution failed: " + e.getMessage());
		}
	}

	/**
	 * Test case for ParallelAgent with both beforeAgent and beforeModel hooks.
	 */
	@Test
	public void testParallelAgentWithBeforeAgentAndBeforeModelHooks() throws Exception {
		System.out.println("\n========== Testing ParallelAgent with BeforeAgent AND BeforeModel Hooks ==========\n");

		// Create multiple agents for parallel execution
		ReactAgent agent1 = ReactAgent.builder()
				.name("agent_1")
				.model(chatModel)
				.description("Process Agent 1")
				.instruction("You are processor 1.Please process: {input}, within 20 words.")
				.outputKey("output_1")
				.enableLogging(false)
				.build();

		ReactAgent agent2 = ReactAgent.builder()
				.name("agent_2")
				.model(chatModel)
				.description("Process Agent 2")
				.instruction("You are processor 2.Please process: {input}, within 20 words.")
				.outputKey("output_2")
				.enableLogging(false)
				.build();

		// Create ParallelAgent with BOTH beforeAgent and beforeModel hooks (dynamically created)
		ParallelAgent parallelAgent = ParallelAgent.builder()
				.name("parallel_processor")
				.description("Parallel processing workflows")
				.subAgents(List.of(agent1, agent2))
				.hooks(List.of(HookFactory.createLogAgentHook(), HookFactory.createLogModelHook()))  // Both hooks present!
				.maxConcurrency(2)
				.build();

		try {
			// Execute the agent
			Optional<OverAllState> result = parallelAgent.invoke("Parallel processing tasks");

			assertTrue(result.isPresent(), "Result should be present");

			// Print Mermaid graph representation
			GraphRepresentation mermaidGraph = parallelAgent.getGraph()
					.getGraph(GraphRepresentation.Type.MERMAID);
			assertNotNull(mermaidGraph, "Mermaid graph should not be null");
			System.out.println("\n=== ParallelAgent with Both Hooks Mermaid Graph ===");
			System.out.println(mermaidGraph.content());
			System.out.println("====================================================\n");

			System.out.println("✓ Bug fix verified: beforeAgent and beforeModel hooks work correctly together in ParallelAgent!");

		} catch (Exception e) {
			e.printStackTrace();
			fail("ParallelAgent with both beforeAgent and beforeModel hooks execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testConditionalFlowWithHook() throws Exception {
		System.out.println("\n========== Testing Conditional Flow with Hook ==========\n");

		// Create sub-agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("good at writing")
				.instruction("You are a well-known writer, 20 words or less.")
				.outputKey("writer_output")
				.enableLogging(false)
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translation")
				.instruction("You are a professional translator, within 20 words.")
				.outputKey("translator_output")
				.enableLogging(false)
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Good at reviewing")
				.instruction("You are a well-known critic, 20 words or less.")
				.outputKey("reviewer_output")
				.enableLogging(false)
				.build();

		// Create conditional flow agent with dynamically created hooks
		FlowAgent conditionalAgent = new FlowAgent("CONDITIONAL", "Conditional routing workflow",
				null, List.of(writerAgent, translatorAgent, reviewerAgent), null, null,
				List.of(HookFactory.createLogAgentHook(), HookFactory.createLogModelHook())) {
			@Override
			protected StateGraph buildSpecificGraph(com.alibaba.cloud.ai.graph.agent.flow.builder.FlowGraphBuilder.FlowGraphConfig config)
					throws GraphStateException {
				Map<String, Agent> conditionalAgents = new HashMap<>();
				conditionalAgents.put("write", writerAgent);
				conditionalAgents.put("translate", translatorAgent);
				conditionalAgents.put("review", reviewerAgent);
				config.conditionalAgents(conditionalAgents);
				return com.alibaba.cloud.ai.graph.agent.flow.builder.FlowGraphBuilder.buildGraph(config.getName(), config);
			}
		};

		try {
			// Execute the agent
			Optional<OverAllState> result = conditionalAgent.invoke("write a poem");

			assertTrue(result.isPresent(), "Result should be present");
			System.out.println("result=" + result.get());

			// Print Mermaid graph representation
			GraphRepresentation mermaidGraph = conditionalAgent.getGraph()
					.getGraph(GraphRepresentation.Type.MERMAID);
			assertNotNull(mermaidGraph, "Mermaid graph should not be null");
			System.out.println("\n=== Conditional Flow Mermaid Graph ===");
			System.out.println(mermaidGraph.content());
			System.out.println("=======================================\n");

		} catch (Exception e) {
			e.printStackTrace();
			fail("Conditional flow with hook execution failed: " + e.getMessage());
		}
	}
}
