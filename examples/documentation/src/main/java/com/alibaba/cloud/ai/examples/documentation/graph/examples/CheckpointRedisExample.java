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
package com.alibaba.cloud.ai.examples.documentation.graph.examples;

import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.redis.RedisSaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.StateSnapshot;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Redis Checkpoint Persistence Example
 * Demonstrates how to persist workflow state using Redis
 */
public class CheckpointRedisExample {

	/**
	 * Initialize RedisSaver
	 */
	public static RedisSaver createRedisSaver() {
		// Configure Redisson client
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://localhost:6379");  // Redis address

		RedissonClient redisson = Redisson.create(config);
		return RedisSaver.builder().redisson(redisson).build();
	}

	/**
	 * Create RedisSaver with custom Redis address
	 */
	public static RedisSaver createRedisSaver(String host, int port) {
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://" + host + ":" + port);

		RedissonClient redisson = Redisson.create(config);
		return RedisSaver.builder().redisson(redisson).build();
	}

	/**
	 * Complete example: Checkpoint persistence with Redis
	 *
	 * @return
	 */
	public static void testCheckpointWithRedis(StateGraph stateGraph) throws Exception {
		// Initialize Redis Saver
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://localhost:6379");

		RedissonClient redisson = Redisson.create(config);
		try {
			RedisSaver saver = RedisSaver.builder().redisson(redisson).build();

			SaverConfig saverConfig = SaverConfig.builder()
					.register(saver)
					.build();

			// Compile graph with checkpoint
			CompiledGraph workflow = stateGraph.compile(
					CompileConfig.builder()
							.saverConfig(saverConfig)
							.build()
			);

			// Execute workflow
			RunnableConfig runnableConfig = RunnableConfig.builder()
					.threadId("test-thread-1")
					.build();

			Map<String, Object> inputs = Map.of("input", "test1");
			OverAllState result = workflow.invoke(inputs, runnableConfig).orElseThrow();

			// Get checkpoint history
			List<StateSnapshot> history = (List<StateSnapshot>) workflow.getStateHistory(runnableConfig);

			System.out.println("Checkpoint history count: " + history.size());

			// Get last saved checkpoint
			StateSnapshot lastSnapshot = workflow.getState(runnableConfig);

			System.out.println("Last checkpoint node: " + lastSnapshot.node());
			
		} finally {
			redisson.shutdown();
		}
	}

	/**
	 * Reload checkpoint from Redis
	 *
	 * @return
	 */
	public static void reloadCheckpointFromRedis(StateGraph stateGraph) throws GraphStateException {
		// Create new saver (reset cache)
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://localhost:6379");

		RedissonClient redisson = Redisson.create(config);
		try {
			RedisSaver newSaver = RedisSaver.builder().redisson(redisson).build();
			
			SaverConfig newSaverConfig = SaverConfig.builder()
					.register(newSaver)
					.build();
			
			// Recompile graph
			CompiledGraph reloadedWorkflow = stateGraph.compile(
					CompileConfig.builder()
							.saverConfig(newSaverConfig)
							.build()
			);
			
			// Use same threadId to get history
			RunnableConfig reloadConfig = RunnableConfig.builder()
					.threadId("test-thread-1")
					.build();
			
			Collection<StateSnapshot> reloadedHistory = reloadedWorkflow.getStateHistory(reloadConfig);
			
			System.out.println("Reloaded checkpoint history count: " + reloadedHistory.size());
		} finally {
			redisson.shutdown();
		}
		
	}

	/**
	 * Restore from a specific checkpoint
	 */
	public static void restoreFromCheckpoint(StateGraph stateGraph) throws GraphStateException{
		Config config = new Config();
		config.useSingleServer()
				.setAddress("redis://localhost:6379");
		
		RedissonClient redisson = Redisson.create(config);
		try {
			RedisSaver newSaver = RedisSaver.builder().redisson(redisson).build();
			
			SaverConfig newSaverConfig = SaverConfig.builder()
					.register(newSaver)
					.build();
			
			// Recompile graph
			CompiledGraph reloadedWorkflow = stateGraph.compile(
					CompileConfig.builder()
							.saverConfig(newSaverConfig)
							.build()
			);
			// Get specific checkpoint
			RunnableConfig checkpointConfig = RunnableConfig.builder()
					.threadId("thread-id")
					.checkPointId("specific-checkpoint-id")
					.build();
			
			// Resume from this checkpoint
			reloadedWorkflow.invoke(Map.of(), checkpointConfig);
			System.out.println("Execution resumed from checkpoint completed");
		}
		finally {
			redisson.shutdown();
		}
		
	}

	public static void main(String[] args) {
		System.out.println("=== Redis Checkpoint Persistence Example ===\n");

		try {
			
			// Define state strategies
			KeyStrategyFactory keyStrategyFactory = () -> {
				Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
				keyStrategyMap.put("input", new ReplaceStrategy());
				keyStrategyMap.put("agent_1:prop1", new ReplaceStrategy());
				return keyStrategyMap;
			};
			
			// Define nodes
			var agent1 = node_async(state -> {
				System.out.println("agent_1 executing");
				return Map.of("agent_1:prop1", "agent_1:test");
			});
			
			// Build graph
			StateGraph stateGraph = new StateGraph(keyStrategyFactory)
					.addNode("agent_1", agent1)
					.addEdge(START, "agent_1")
					.addEdge("agent_1", END);
			
			// Example 1: Complete example - Checkpoint persistence with Redis
			System.out.println("Example 1: Checkpoint persistence with Redis");
			System.out.println("Note: This example requires a Redis connection");
			testCheckpointWithRedis(stateGraph);
			System.out.println();

			// Example 2: Reload checkpoint from Redis
			System.out.println("Example 2: Reload checkpoint from Redis");
			System.out.println("Note: This example requires a Redis connection");
			reloadCheckpointFromRedis(stateGraph);
			System.out.println();

			// Example 3: Restore from a specific checkpoint
			System.out.println("Example 3: Restore from a specific checkpoint");
			System.out.println("Note: This example requires a valid CompiledGraph and checkpointId");
			restoreFromCheckpoint(stateGraph);
			System.out.println();

			System.out.println("All examples completed");
			System.out.println("Tip: Configure Redis connection before running the full example");
			System.out.println("Tip: Redisson dependency required: org.redisson:redisson");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

