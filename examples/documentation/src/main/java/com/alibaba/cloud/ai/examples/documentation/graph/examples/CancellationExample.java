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

import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.RunnableConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import reactor.core.Disposable;
import reactor.core.publisher.Flux;

/**
 * Graph Execution Cancellation Example
 * Demonstrates how to cancel graph execution
 */
public class CancellationExample {

	/**
	 * Example 1: Cancel while consuming stream with forEachAsync
	 */
	public static void cancelWithForEachAsync(CompiledGraph compiledGraph, boolean mayInterruptIfRunning) {
		// 创建运行配置
		RunnableConfig runnableConfig = RunnableConfig.builder()
				.threadId("test-thread")
				.build();

		// 准备输入数据
		Map<String, Object> inputData = new HashMap<>();
		// ... 添加输入数据

		// 执行图并获取流
		Flux<NodeOutput> stream = compiledGraph.stream(inputData, runnableConfig);

		// Request cancellation from a new thread after 500 milliseconds
		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(500);
				// Flux uses dispose() to cancel
				System.out.println("Requesting execution cancellation");
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});

		// Asynchronously process each output
		var disposable = stream.subscribe(
				output -> System.out.println("Current iteration node: " + output),
				error -> System.out.println("Stream error: " + error.getMessage()),
				() -> System.out.println("Stream completed")
		);

		// Wait for stream completion or cancellation
		try {
			stream.blockLast();
		}
		catch (Exception e) {
			System.err.println("Execution exception: " + e.getMessage());
		}

		// Check if cancelled (Flux uses isDisposed to check)
		System.out.println("Is cancelled: " + disposable.isDisposed());
	}

	/**
	 * Example 2: Cancel while consuming stream with iterator
	 */
	public static void cancelWithIterator(CompiledGraph compiledGraph, boolean mayInterruptIfRunning) {
		// Create run configuration
		RunnableConfig runnableConfig = RunnableConfig.builder()
				.threadId("test-thread")
				.build();

		// Prepare input data
		Map<String, Object> inputData = new HashMap<>();
		// ... Add input data

		// Execute graph and get stream
		Flux<NodeOutput> stream = compiledGraph.stream(inputData, runnableConfig);

		// Request cancellation from a new thread after 500 milliseconds
		var disposable = stream.subscribe(
				output -> {
					System.out.println("Current iteration node: " + output);
				},
				error -> {
					System.out.println("Stream error: " + error.getMessage());
				},
				() -> {
					System.out.println("Stream completed");
				}
		);

		CompletableFuture.runAsync(() -> {
			try {
				Thread.sleep(500);
				disposable.dispose(); // Cancel stream
				System.out.println("Execution cancellation requested");
			}
			catch (InterruptedException e) {
				throw new RuntimeException(e);
			}
		});

		// 等待流完成或取消
		try {
			stream.blockLast();
		}
		catch (Exception e) {
			System.out.println("Stream interrupted: " + e.getMessage());
		}

		// Check cancellation status
		System.out.println("Is cancelled: " + disposable.isDisposed());
	}

	/**
	 * Check cancellation status
	 */
	public static void checkCancellationStatus(Disposable disposable) {
		if (disposable.isDisposed()) {
			System.out.println("Stream has been cancelled");
		}
		else {
			System.out.println("Stream is still running");
		}
	}

	public static void main(String[] args) {
		System.out.println("=== Graph Execution Cancellation Example ===\n");

		try {
			// Example 1: Cancel while consuming stream with forEachAsync (requires CompiledGraph)
			System.out.println("Example 1: Cancel while consuming stream with forEachAsync");
			System.out.println("Note: This example requires CompiledGraph, skipping execution");
			// cancelWithForEachAsync(compiledGraph, true);
			System.out.println();

			// Example 2: Cancel while consuming stream with iterator (requires CompiledGraph)
			System.out.println("Example 2: Cancel while consuming stream with iterator");
			System.out.println("Note: This example requires CompiledGraph, skipping execution");
			// cancelWithIterator(compiledGraph, true);
			System.out.println();

			System.out.println("All examples completed");
			System.out.println("Tip: Configure CompiledGraph before running the full example");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

