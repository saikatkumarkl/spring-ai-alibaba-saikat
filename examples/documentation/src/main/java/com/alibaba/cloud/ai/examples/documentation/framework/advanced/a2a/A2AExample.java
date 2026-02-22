/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.cloud.ai.examples.documentation.framework.advanced.a2a;

import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.A2aRemoteAgent;
import com.alibaba.cloud.ai.graph.agent.a2a.AgentCardProvider;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.model.ChatModel;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * A2A (Agent-to-Agent) integrated example: Register -> Discover -> Invoke
 *
 * - After starting this application, data_analysis_agent will be automatically registered to A2A as a local ReactAgent (and registered to Nacos based on configuration)
 * - Discover the Agent through AgentCardProvider from the registry
 * - Construct A2aRemoteAgent proxy and complete the invocation
 */
@Component
public class A2AExample {

	private final ChatModel chatModel;
	private final AgentCardProvider agentCardProvider;
	private final ReactAgent localDataAnalysisAgent;

	@Autowired
	public A2AExample(@Qualifier("dashScopeChatModel") ChatModel chatModel,
			AgentCardProvider agentCardProvider,
			@Qualifier("dataAnalysisAgent") ReactAgent localDataAnalysisAgent) {
		this.chatModel = chatModel;
		this.agentCardProvider = agentCardProvider;
		this.localDataAnalysisAgent = localDataAnalysisAgent;
	}

	/**
	 * Run the integrated demo
	 * 1) The local Agent has been created by the Spring container and automatically exposed through the A2A Server
	 * 2) Use AgentCardProvider to discover the Agent from the registry
	 * 3) Build A2aRemoteAgent and complete a remote invocation
	 */
	public void runDemo() throws GraphRunnerException {
		System.out.println("=== A2A Integrated Demo: Register -> Discover -> Invoke ===\n");

		// Architecture description
		System.out.println("[Architecture]");
		System.out.println("1. Registry: Local Agent registers to Nacos for other services to discover");
		System.out.println("2. Discovery: Query Agent through AgentCardProvider from Nacos");
		System.out.println("3. Invocation: Construct A2aRemoteAgent to complete the remote call");
		System.out.println();

		// 1) Direct local call: verify the locally registered ReactAgent is available
		System.out.println("[Phase 1: Local Direct Call] Verify ReactAgent Bean functionality");
		System.out.println("- Agent name: data_analysis_agent");
		System.out.println("- Call method: Direct Bean invocation");
		System.out.println("- Registration status: Registered to Nacos via A2A Server AutoConfiguration");
		System.out.println();

		System.out.println("Executing local call...");
		Optional<OverAllState> localResult = localDataAnalysisAgent.invoke("Please perform trend analysis on last month's sales data and provide key conclusions.");
		localResult.flatMap(s -> s.value("messages")).ifPresent(r ->
				System.out.println("OK Local call succeeded, result: " + (r.toString().length() > 100 ? r.toString()
						.substring(0, 100) + "..." : r)));
		System.out.println();

		// 2) Discovery: Get the Agent's AgentCard from the registry via AgentCardProvider
		System.out.println("[Phase 2: Service Discovery] Using AgentCardProvider to discover Agent from Nacos");
		System.out.println("- Discovery mechanism: Nacos Discovery (spring.ai.alibaba.a2a.nacos.discovery.enabled=true)");
		System.out.println("- AgentCardProvider type: " + agentCardProvider.getClass().getSimpleName());
		System.out.println("- Querying Agent: data_analysis_agent");
		System.out.println();

		System.out.println("Building A2aRemoteAgent...");
		A2aRemoteAgent remote = A2aRemoteAgent.builder()
				.name("data_analysis_agent")
				.agentCardProvider(agentCardProvider)  // Automatically get AgentCard from Nacos
				.description("Data analysis remote agent")
				.instruction("{input}")  // Pass user input to the remote Agent
				.build();
		System.out.println("OK A2aRemoteAgent built successfully, AgentCard obtained from Nacos");
		System.out.println();

		// 3) Remote call: invoke via A2aRemoteAgent (even in-process, simulates the remote call path)
		System.out.println("[Phase 3: Remote Invocation] Execute remote call through A2aRemoteAgent");
		System.out.println("- Call path: A2aRemoteAgent -> REST API (/a2a/message) -> Local ReactAgent");
		System.out.println("- Transport protocol: JSON-RPC over HTTP");
		System.out.println();

		System.out.println("Executing remote call...");
		Optional<OverAllState> remoteResult = remote.invoke("Please provide a year-over-year and quarter-over-quarter analysis summary based on quarterly data.");
		remoteResult.flatMap(s -> s.value("output")).ifPresent(r ->
				System.out.println("OK Remote call succeeded, result: " + (r.toString().length() > 100 ? r.toString()
						.substring(0, 100) + "..." : r)));
		System.out.println();

		// Verification points
		System.out.println("[Verification Points]");
		System.out.println("1. Local AgentCard:");
		System.out.println("   → curl http://localhost:8080/.well-known/agent.json");
		System.out.println();
		System.out.println("2. Nacos Console (verify registration):");
		System.out.println("   -> http://localhost:8848/nacos");
		System.out.println("   -> Login (nacos/nacos)");
		System.out.println("   -> View A2A service registration");
		System.out.println();
		System.out.println("3. Configuration notes:");
		System.out.println("   -> registry.enabled=true  : Register local Agent to Nacos (service provider)");
		System.out.println("   -> discovery.enabled=true : Discover other Agents from Nacos (service consumer)");
		System.out.println();
		System.out.println("4. Other service invocation:");
		System.out.println("   Other services can discover and invoke data_analysis_agent the same way:");
		System.out.println("   ```");
		System.out.println("   A2aRemoteAgent remote = A2aRemoteAgent.builder()");
		System.out.println("       .name(\"data_analysis_agent\")");
		System.out.println("       .agentCardProvider(agentCardProvider)");
		System.out.println("       .build();");
		System.out.println("   remote.invoke(\"analysis request...\");");
		System.out.println("   ```");
	}
}
