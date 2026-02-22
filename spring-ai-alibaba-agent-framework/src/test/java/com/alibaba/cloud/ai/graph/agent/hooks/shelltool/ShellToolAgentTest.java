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
package com.alibaba.cloud.ai.graph.agent.hooks.shelltool;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.shelltool.ShellToolAgentHook;
import com.alibaba.cloud.ai.graph.agent.tools.ShellTool;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.tool.ToolCallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
public class ShellToolAgentTest {

	private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testShellToolWithShellToolAgentHook() throws Exception {
		Path tempWorkspace = Files.createTempDirectory("shelltool_test");

		try {

			ShellTool.Builder shellToolBuilder = ShellTool.builder(tempWorkspace.toString())
				.withCommandTimeout(30000)  //30 seconds timeout
				.withMaxOutputLines(500);

			if (System.getProperty("os.name").toLowerCase().contains("windows")) {
				shellToolBuilder = shellToolBuilder.withShellCommand(List.of("powershell.exe"));
			}

			ToolCallback shellToolCallback = shellToolBuilder.build();

			List<ToolCallback> tools = List.of(shellToolCallback);

			ShellToolAgentHook shellToolAgentHook = ShellToolAgentHook.builder().build();

			ReactAgent agent = ReactAgent.builder()
				.name("shell-tool-agent-example")
				.model(chatModel)
				.instruction("You are a helpful assistant that can execute shell commands when needed. " +
					"Use the shell tool to run commands and report the results. " +
					"When creating files, always put them in the current directory unless specified otherwise. " +
					"Prefer using 'ls' or 'dir' command to see the current directory contents.")
				.tools(tools)
				.hooks(List.of(shellToolAgentHook))
				.saver(new MemorySaver())
				.build();

			GraphRepresentation representation = agent.getAndCompileGraph().stateGraph.getGraph(GraphRepresentation.Type.PLANTUML);
			System.out.println("Agent Graph Representation:\n" + representation.content());

			List<Message> messages = new ArrayList<>();
			String testCommand = System.getProperty("os.name").toLowerCase().contains("windows") ?
				"Please help me create a file named test.txt with the content 'Hello from ShellTool!', then list the files in the current directory, and finally display the contents of the test.txt file.Please use Windows PowerShell compatible commands." :
				"Please help me with the following tasks:\n" +
				"1. Create a file named test.txt with the content 'Hello from ShellTool!'\n" +
				"2. List files in the current directory\n" +
				"3. Display the contents of the test.txt file\n" +
				"Please follow the steps and tell me the results after each step.";

			messages.add(new UserMessage(testCommand));

			//Execute Agent
			System.out.println("Start executing Agent...");
			Optional<OverAllState> result = agent.invoke(messages);

			assertTrue(result.isPresent(), "Agent should return results");
			Object messagesObj = result.get().value("messages").get();
			assertNotNull(messagesObj, "The returned message should not be null");

			System.out.println("The Agent executes successfully and returns the number of messages:" +
				(messagesObj instanceof List ? ((List<?>) messagesObj).size() : "unknown"));
			System.out.println("Agent results:" + messagesObj);
			System.out.println("✓ ShellTool and ShellToolAgentHook integration test executed successfully");
		}
		finally {
			//Clean up temporary directory
			deleteDirectory(tempWorkspace);
		}
	}

	private static CompileConfig getCompileConfig() {
		SaverConfig saverConfig = SaverConfig.builder()
			.register(new MemorySaver())
			.build();
		return CompileConfig.builder().saverConfig(saverConfig).build();
	}

	private void deleteDirectory(Path directory) throws IOException {
		if (Files.exists(directory)) {
			Files.walk(directory)
				.sorted(java.util.Comparator.reverseOrder())
				.map(Path::toFile)
				.forEach(java.io.File::delete);
		}
	}
}
