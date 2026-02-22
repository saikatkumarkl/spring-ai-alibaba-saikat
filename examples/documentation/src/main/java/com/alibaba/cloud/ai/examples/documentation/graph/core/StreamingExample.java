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
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.AsyncNodeAction;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.streaming.StreamingOutput;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;

import java.util.HashMap;
import java.util.Map;

import reactor.core.publisher.Flux;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;

/**
 * Streaming Output Example
 * Demonstrates how to implement streaming output in CordonData Graph
 */
public class StreamingExample {

	/**
	 * Complete example of implementing streaming output using StateGraph
	 *
	 * @param chatClientBuilder ChatClient builder
	 * @throws GraphStateException graph execution exception
	 */
	public static void streamLLMTokens(ChatClient.Builder chatClientBuilder) throws GraphStateException {
		// Define state strategies
		KeyStrategyFactory keyStrategyFactory = () -> {
			Map<String, KeyStrategy> keyStrategyMap = new HashMap<>();
			keyStrategyMap.put("query", new AppendStrategy());
			keyStrategyMap.put("messages", new AppendStrategy());
			keyStrategyMap.put("result", new AppendStrategy());
			return keyStrategyMap;
		};

		// Create streaming node
		StreamingNode streamingNode = new StreamingNode(chatClientBuilder, "streaming_node");

		// Create processing node
		ProcessStreamingNode processNode = new ProcessStreamingNode();

		// Build graph
		StateGraph stateGraph = new StateGraph(keyStrategyFactory)
				.addNode("streaming_node", AsyncNodeAction.node_async(streamingNode))
				.addNode("process_node", AsyncNodeAction.node_async(processNode))
				.addEdge(START, "streaming_node")
				.addEdge("streaming_node", "process_node")
				.addEdge("process_node", END);

		// Compile graph
		CompiledGraph graph = stateGraph.compile(
				CompileConfig.builder()
						.build()
		);

		// Create configuration
		RunnableConfig config = RunnableConfig.builder()
				.threadId("streaming_thread")
				.build();

		// Execute graph in streaming mode
		System.out.println("Starting streaming output...\n");

		graph.stream(Map.of("query", "Describe Spring AI in one sentence"), config)
				.doOnNext(output -> {
					// Process streaming output
					if (output instanceof StreamingOutput<?> streamingOutput) {
						// Streaming output chunk
						String chunk = streamingOutput.chunk();
						if (chunk != null && !chunk.isEmpty()) {
							System.out.print(chunk); // Print streaming content in real-time
						}
					}
					else {
						// Normal node output
						String nodeId = output.node();
						Map<String, Object> state = output.state().data();
						System.out.println("\nNode '" + nodeId + "' execution completed");
						if (state.containsKey("result")) {
							System.out.println("Final result: " + state.get("result"));
						}
					}
				})
				.doOnComplete(() -> {
					System.out.println("\n\nStreaming output completed");
				})
				.doOnError(error -> {
					System.err.println("Streaming output error: " + error.getMessage());
				})
				.blockLast(); // Block and wait for stream completion
	}

	public static void main(String[] args) {
		System.out.println("=== Streaming Output Example ===\n");

		try {
			// Example 1: Streaming LLM tokens with Spring AI (requires ChatClient)
			System.out.println("Example 1: Streaming LLM tokens with Spring AI");
			System.out.println("Note: This example requires ChatClient, skipping execution");
			System.out.println("Usage: streamLLMTokens(ChatClient.builder()...)");
			// streamLLMTokens(ChatClient.builder()...);
			System.out.println();

			System.out.println("All examples completed");
			System.out.println("Tip: Configure ChatClient before running the full example");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}

	public static class StreamingNode implements NodeAction {

		private final ChatClient chatClient;
		private final String nodeId;

		public StreamingNode(ChatClient.Builder chatClientBuilder, String nodeId) {
			this.chatClient = chatClientBuilder.build();
			this.nodeId = nodeId;
		}

		@Override
		public Map<String, Object> apply(OverAllState state) {
			String query = (String) state.value("query").orElse("");

			// Get streaming response
			Flux<ChatResponse> chatResponseFlux = chatClient.prompt()
					.user(query)
					.stream()
					.chatResponse();

			return Map.of("messages", chatResponseFlux);
		}
	}

	/**
	 * Node for processing streaming output - receives and processes streaming responses
	 */
	public static class ProcessStreamingNode implements NodeAction {

		@Override
		public Map<String, Object> apply(OverAllState state) {
			// Get streaming response result from state
			Object messages = state.value("messages").orElse("");
			String result = "Streaming response processed: " + messages;
			return Map.of("result", result);
		}
	}
}

