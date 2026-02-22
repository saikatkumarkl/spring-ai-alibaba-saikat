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
package com.alibaba.cloud.ai.examples.documentation.graph.core;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Persistence Example
 * Demonstrates how to use Checkpointer for workflow state persistence
 */
public class PersistenceExample {

	/**
	 * Example 1: Basic Persistence Configuration
	 */
	public static void basicPersistenceExample() throws GraphStateException {
		// Define state strategies
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
			keyStrategyMap.put("foo", new ReplaceStrategy());
			keyStrategyMap.put("bar", new AppendStrategy());
			return keyStrategyMap;
		};

		// Define node actions
		var nodeA = node_async(state -> {
			return Map.of("foo", "a", "bar", List.of("a"));
		});

		var nodeB = node_async(state -> {
			return Map.of("foo", "b", "bar", List.of("b"));
		});

		// Create graph
		StateGraph stateGraph = new StateGraph(keyStrategyFactory)
				.addNode("node_a", nodeA)
				.addNode("node_b", nodeB)
				.addEdge(START, "node_a")
				.addEdge("node_a", "node_b")
				.addEdge("node_b", END);

		// Configure checkpoint
		SaverConfig saverConfig = SaverConfig.builder()
				.register(new MemorySaver())
				.build();

		// Compile graph
		CompiledGraph graph = stateGraph.compile(
				CompileConfig.builder()
						.saverConfig(saverConfig)
						.build()
		);

		// Run graph
		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.build();

		Map<String, Object> input = new HashMap<>();
		input.put("foo", "");

		graph.invoke(input, config);
		System.out.println("Basic persistence example executed");
	}

	/**
	 * Example 2: Getting State
	 */
	public static void getStateExample(CompiledGraph graph) {
		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.build();

		// Get the latest state snapshot
		StateSnapshot stateSnapshot = graph.getState(config);
		System.out.println("Current state: " + stateSnapshot.state());
		System.out.println("Current node: " + stateSnapshot.node());

		// Get state snapshot for a specific checkpoint_id
		RunnableConfig configWithCheckpoint = RunnableConfig.builder()
				.threadId("1")
				.checkPointId("1ef663ba-28fe-6528-8002-5a559208592c")
				.build();
		StateSnapshot specificSnapshot = graph.getState(configWithCheckpoint);
		System.out.println("Specific checkpoint state: " + specificSnapshot.state());
	}

	/**
	 * Example 3: Getting State History
	 */
	public static void getStateHistoryExample(CompiledGraph graph) {
		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.build();

		List<StateSnapshot> history = (List<StateSnapshot>) graph.getStateHistory(config);
		System.out.println("State history:");
		for (int i = 0; i < history.size(); i++) {
			StateSnapshot snapshot = history.get(i);
			System.out.printf("Step %d: %s\n", i, snapshot.state());
			System.out.printf("  Checkpoint ID: %s\n", snapshot.config().checkPointId());
			System.out.printf("  Node: %s\n", snapshot.node());
		}
	}

	/**
	 * Example 4: Updating State
	 */
	public static void updateStateExample(CompiledGraph graph) throws Exception {
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
			keyStrategyMap.put("foo", new ReplaceStrategy());  // Replace strategy
			keyStrategyMap.put("bar", new AppendStrategy());   // Append strategy
			return keyStrategyMap;
		};

		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.build();

		Map<String, Object> updates = new HashMap<>();
		updates.put("foo", 2);
		updates.put("bar", List.of("b"));

		graph.updateState(config, updates, null);
		System.out.println("State updated successfully");
	}

	/**
	 * Example 5: Replay
	 */
	public static void replayExample(CompiledGraph graph) {
		RunnableConfig config = RunnableConfig.builder()
				.threadId("1")
				.checkPointId("0c62ca34-ac19-445d-bbb0-5b4984975b2a")
				.build();

		graph.invoke(Map.of(), config);
		System.out.println("Replay executed");
	}

	public static void main(String[] args) {
		System.out.println("=== Persistence Examples ===\n");

		try {
			// Example 1: Basic Persistence Configuration
			System.out.println("Example 1: Basic Persistence Configuration");
			basicPersistenceExample();
			System.out.println();

			// Create graph for subsequent examples
			KeyStrategyFactory keyStrategyFactory = () -> {
				Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
				keyStrategyMap.put("foo", new ReplaceStrategy());
				keyStrategyMap.put("bar", new AppendStrategy());
				return keyStrategyMap;
			};

			StateGraph stateGraph = new StateGraph(keyStrategyFactory)
					.addNode("node_a", node_async(state -> Map.of("foo", "a", "bar", List.of("a"))))
					.addNode("node_b", node_async(state -> Map.of("foo", "b", "bar", List.of("b"))))
					.addEdge(START, "node_a")
					.addEdge("node_a", "node_b")
					.addEdge("node_b", END);

			SaverConfig saverConfig = SaverConfig.builder()
					.register(new MemorySaver())
					.build();

			CompiledGraph graph = stateGraph.compile(
					CompileConfig.builder()
							.saverConfig(saverConfig)
							.build()
			);

			RunnableConfig config = RunnableConfig.builder()
					.threadId("1")
					.build();

			Map<String, Object> input = new HashMap<>();
			input.put("foo", "");
			graph.invoke(input, config);

			// Example 2: Getting State
			System.out.println("Example 2: Getting State");
			getStateExample(graph);
			System.out.println();

			// Example 3: Getting State History
			System.out.println("Example 3: Getting State History");
			getStateHistoryExample(graph);
			System.out.println();

			// Example 4: Updating State
			System.out.println("Example 4: Updating State");
			updateStateExample(graph);
			System.out.println();

			// Example 5: Replay (requires valid checkpointId)
			System.out.println("Example 5: Replay (requires valid checkpointId)");
			System.out.println("Note: This example requires a valid checkpointId, skipping execution");
			// replayExample(graph);
			System.out.println();

			System.out.println("All examples executed successfully");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

