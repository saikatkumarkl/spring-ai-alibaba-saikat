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
package com.alibaba.cloud.ai.graph.agent.hooks.messages;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.hook.HookPosition;
import com.alibaba.cloud.ai.graph.agent.hook.HookPositions;
import com.alibaba.cloud.ai.graph.agent.hook.JumpTo;
import com.alibaba.cloud.ai.graph.agent.hook.ModelHook;
import com.alibaba.cloud.ai.graph.agent.hook.messages.AgentCommand;
import com.alibaba.cloud.ai.graph.agent.hook.messages.UpdatePolicy;
import com.alibaba.cloud.ai.graph.agent.hook.messages.MessagesModelHook;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
public class MessagesModelHookTest {

	private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	/**
	 * Test 1: Verify MessagesModelHook is correctly loaded and executed
	 */
	@Test
	public void testMessagesModelHookLoadedAndExecuted() throws Exception {
		AtomicInteger beforeModelCallCount = new AtomicInteger(0);
		AtomicInteger afterModelCallCount = new AtomicInteger(0);

		TestMessagesModelHook hook = new TestMessagesModelHook("test_hook", beforeModelCallCount, afterModelCallCount);

		ReactAgent agent = createAgentWithMessagesHook(hook, "test-agent-loaded");

		System.out.println("\n=== Test that MessagesModelHook is loaded and executed correctly ===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Hello, please introduce yourself briefly."));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");
		assertTrue(beforeModelCallCount.get() > 0, "beforeModel should be called");
		assertTrue(afterModelCallCount.get() > 0, "afterModel should be called");

		System.out.println("✓ Number of beforeModel calls:" + beforeModelCallCount.get());
		System.out.println("✓ Number of afterModel calls:" + afterModelCallCount.get());
	}

	/**
	 * Test 2: Verify REPLACE policy works correctly
	 */
	@Test
	public void testReplacePolicy() throws Exception {
		ReplacePolicyMessagesHook hook = new ReplacePolicyMessagesHook();

		ReactAgent agent = createAgentWithMessagesHook(hook, "test-agent-replace");

		System.out.println("\n=== Testing the REPLACE strategy ===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Original message 1"));
		messages.add(new UserMessage("Original message 2"));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");
		Object messagesObj = result.get().value("messages").get();
		assertNotNull(messagesObj, "The message should be present in the result");

		if (messagesObj instanceof List) {
			List<Message> resultMessages = (List<Message>) messagesObj;
			System.out.println("Number of messages returned:" + resultMessages.size());

			//Verify that the message is replaced: it should contain the replaced system message and not the original message
			boolean foundSystemMessage = false;
			boolean foundOriginalMessage1 = false;
			boolean foundOriginalMessage2 = false;

			for (Message message : resultMessages) {
				if (message instanceof SystemMessage) {
					String content = message.getText();
					if (content.contains("This is the system message after replacement")) {
						foundSystemMessage = true;
					}
				} else if (message instanceof UserMessage) {
					String content = message.getText();
					if (content.equals("Original message 1")) {
						foundOriginalMessage1 = true;
					}
					if (content.equals("Original message 2")) {
						foundOriginalMessage2 = true;
					}
				}
			}

			assertTrue(foundSystemMessage, "The replaced system message should be found");
			assertTrue(foundOriginalMessage2, "The last user original message 2 should be found");
			assertFalse(foundOriginalMessage1, "The first user original message 1 should not be found");
			//Due to the REPLACE policy, the original message may be replaced, but new messages may be added during agent execution.
			//So we mainly verify that the replaced system message exists
			System.out.println("✓ Successful verification of REPLACE policy: replaced system message exists");
		}
	}

	/**
	 * Test 3: Verify APPEND policy works correctly
	 */
	@Test
	public void testAppendPolicy() throws Exception {
		AppendPolicyMessagesHook hook = new AppendPolicyMessagesHook();

		ReactAgent agent = createAgentWithMessagesHook(hook, "test-agent-append");

		System.out.println("\n=== Test APPEND strategy ===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Original user message"));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");
		Object messagesObj = result.get().value("messages").get();
		assertNotNull(messagesObj, "The message should be present in the result");

		if (messagesObj instanceof List) {
			List<Message> resultMessages = (List<Message>) messagesObj;
			System.out.println("Number of messages returned:" + resultMessages.size());

			//Verification message is appended instead of replaced
			boolean foundOriginalMessage = false;
			boolean foundAppendedMessage = false;
			for (Message message : resultMessages) {
				if (message instanceof UserMessage) {
					String content = message.getText();
					if (content.equals("Original user message")) {
						foundOriginalMessage = true;
					}
					if (content.equals("This is an additional message")) {
						foundAppendedMessage = true;
					}
				}
			}
			assertTrue(foundOriginalMessage, "The original message should be preserved");
			assertTrue(foundAppendedMessage, "The appended message should be found");
			System.out.println("✓ Successful validation of APPEND policy: message is appended instead of replaced");
		}
	}

	/**
	 * Test 4: Verify JumpTo End functionality - skip subsequent hooks
	 */
	@Test
	public void testJumpToEnd() throws Exception {
		AtomicInteger firstHookBeforeCount = new AtomicInteger(0);
		AtomicInteger firstHookAfterCount = new AtomicInteger(0);
		AtomicInteger secondHookBeforeCount = new AtomicInteger(0);
		AtomicInteger secondHookAfterCount = new AtomicInteger(0);
		AtomicInteger thirdHookBeforeCount = new AtomicInteger(0);
		AtomicInteger thirdHookAfterCount = new AtomicInteger(0);

		// First hook will jump to end, skipping subsequent hooks
		JumpToEndMessagesHook firstHook = new JumpToEndMessagesHook("jump_to_end_hook",
				firstHookBeforeCount, firstHookAfterCount);
		// Second hook should be skipped
		TestMessagesModelHook secondHook = new TestMessagesModelHook("second_hook",
				secondHookBeforeCount, secondHookAfterCount);
		// Third hook should also be skipped
		TestMessagesModelHook thirdHook = new TestMessagesModelHook("third_hook",
				thirdHookBeforeCount, thirdHookAfterCount);

		ReactAgent agent = ReactAgent.builder()
				.name("test-agent-jump-to-end")
				.model(chatModel)
				.hooks(List.of(firstHook, secondHook, thirdHook))
				.saver(new MemorySaver())
				.build();

		System.out.println("\n=== Test JumpTo End function ===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Hello, please introduce yourself briefly."));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");

		// First hook should be called
		assertTrue(firstHookBeforeCount.get() > 0, "beforeModel of the first hook should be called");
		assertTrue(firstHookAfterCount.get() == 0, "The first hook's afterModel should not be called (because it jumps to end)");

		// Second and third hooks should be skipped
		assertEquals(0, secondHookBeforeCount.get(), "beforeModel of the second hook should not be called (is skipped)");
		assertEquals(0, secondHookAfterCount.get(), "The second hook's afterModel should not be called (is skipped)");
		assertEquals(0, thirdHookBeforeCount.get(), "The beforeModel of the third hook should not be called (skipped)");
		assertEquals(0, thirdHookAfterCount.get(), "The third hook's afterModel should not be called (skipped)");

		System.out.println("✓ Number of first hook beforeModel calls:" + firstHookBeforeCount.get());
		System.out.println("✓ Number of first hook afterModel calls:" + firstHookAfterCount.get());
		System.out.println("✓ Number of second hook beforeModel calls:" + secondHookBeforeCount.get());
		System.out.println("✓ Number of second hook afterModel calls:" + secondHookAfterCount.get());
		System.out.println("✓ Number of third hook beforeModel calls:" + thirdHookBeforeCount.get());
		System.out.println("✓ Number of third hook afterModel calls:" + thirdHookAfterCount.get());
		System.out.println("✓ Successful verification of JumpTo End: subsequent hooks are correctly skipped");
	}

	/**
	 * Test 5: Verify JumpTo End with mixed MessagesModelHook and ModelHook
	 */
	@Test
	public void testJumpToEndWithMixedHooks() throws Exception {
		AtomicInteger messagesHookBeforeCount = new AtomicInteger(0);
		AtomicInteger messagesHookAfterCount = new AtomicInteger(0);
		AtomicInteger modelHookBeforeCount = new AtomicInteger(0);
		AtomicInteger modelHookAfterCount = new AtomicInteger(0);
		AtomicInteger secondMessagesHookBeforeCount = new AtomicInteger(0);
		AtomicInteger secondMessagesHookAfterCount = new AtomicInteger(0);

		// First MessagesModelHook will jump to end
		JumpToEndMessagesHook firstMessagesHook = new JumpToEndMessagesHook("first_messages_hook",
				messagesHookBeforeCount, messagesHookAfterCount);
		// ModelHook should be skipped
		TestModelHook modelHook = new TestModelHook("test_model_hook",
				modelHookBeforeCount, modelHookAfterCount);
		// Second MessagesModelHook should also be skipped
		TestMessagesModelHook secondMessagesHook = new TestMessagesModelHook("second_messages_hook",
				secondMessagesHookBeforeCount, secondMessagesHookAfterCount);

		ReactAgent agent = ReactAgent.builder()
				.name("test-agent-jump-to-end-mixed")
				.model(chatModel)
				.hooks(List.of(firstMessagesHook, modelHook, secondMessagesHook))
				.saver(new MemorySaver())
				.build();

		System.out.println("\n=== test JumpTo End function (mixed MessagesModelHook and ModelHook）===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Hello, please introduce yourself briefly."));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");

		// First MessagesModelHook should be called
		assertTrue(messagesHookBeforeCount.get() > 0, "beforeModel of the first MessagesModelHook should be called");
		assertEquals(0, messagesHookAfterCount.get(), "afterModel of the first MessagesModelHook should not be called");

		// ModelHook should be skipped
		assertEquals(0, modelHookBeforeCount.get(), "ModelHook's beforeModel should not be called (skipped)");
		assertEquals(0, modelHookAfterCount.get(), "ModelHook's afterModel should not be called (is skipped)");

		// Second MessagesModelHook should be skipped
		assertEquals(0, secondMessagesHookBeforeCount.get(), "The beforeModel of the second MessagesModelHook should not be called (is skipped)");
		assertEquals(0, secondMessagesHookAfterCount.get(), "The afterModel of the second MessagesModelHook should not be called (is skipped)");

		System.out.println("✓ Number of first MessagesModelHook beforeModel calls:" + messagesHookBeforeCount.get());
		System.out.println("✓ ModelHook beforeModel Number of calls: " + modelHookBeforeCount.get());
		System.out.println("✓ Number of second MessagesModelHook beforeModel calls:" + secondMessagesHookBeforeCount.get());
		System.out.println("✓ Successfully verified JumpTo End (mixed hooks): subsequent hooks are correctly skipped");
	}

	/**
	 * Test 6: Verify MessagesModelHook and ModelHook can work together
	 */
	@Test
	public void testMessagesModelHookWithModelHook() throws Exception {
		AtomicInteger messagesHookBeforeCount = new AtomicInteger(0);
		AtomicInteger messagesHookAfterCount = new AtomicInteger(0);
		AtomicInteger modelHookBeforeCount = new AtomicInteger(0);
		AtomicInteger modelHookAfterCount = new AtomicInteger(0);

		TestMessagesModelHook messagesHook = new TestMessagesModelHook("test_messages_hook",
				messagesHookBeforeCount, messagesHookAfterCount);
		TestModelHook modelHook = new TestModelHook("test_model_hook",
				modelHookBeforeCount, modelHookAfterCount);

		ReactAgent agent = ReactAgent.builder()
				.name("test-agent-both-hooks")
				.model(chatModel)
				.hooks(List.of(messagesHook, modelHook))
				.saver(new MemorySaver())
				.build();

		System.out.println("\n=== test MessagesModelHook and ModelHook Use at the same time ===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Hello, please introduce yourself briefly."));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");
		assertTrue(messagesHookBeforeCount.get() > 0, "MessagesModelHook beforeModel should be called");
		assertTrue(messagesHookAfterCount.get() > 0, "MessagesModelHook afterModel should be called");
		assertTrue(modelHookBeforeCount.get() > 0, "ModelHook beforeModel should be called");
		assertTrue(modelHookAfterCount.get() > 0, "ModelHook afterModel should be called");

		System.out.println("✓ MessagesModelHook beforeModel Number of calls: " + messagesHookBeforeCount.get());
		System.out.println("✓ MessagesModelHook afterModel Number of calls: " + messagesHookAfterCount.get());
		System.out.println("✓ ModelHook beforeModel Number of calls: " + modelHookBeforeCount.get());
		System.out.println("✓ ModelHook afterModel Number of calls: " + modelHookAfterCount.get());
		System.out.println("✓ Two Hooks can run normally at the same time");
	}

	private ReactAgent createAgentWithMessagesHook(MessagesModelHook hook, String name) throws GraphStateException {
		return ReactAgent.builder()
				.name(name)
				.model(chatModel)
				.hooks(List.of(hook))
				.saver(new MemorySaver())
				.build();
	}

	/**
	 * Test MessagesModelHook implementation for testing
	 */
	@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
	private static class TestMessagesModelHook extends MessagesModelHook {
		private final String name;
		private final AtomicInteger beforeModelCallCount;
		private final AtomicInteger afterModelCallCount;

		public TestMessagesModelHook(String name, AtomicInteger beforeModelCallCount,
				AtomicInteger afterModelCallCount) {
			this.name = name;
			this.beforeModelCallCount = beforeModelCallCount;
			this.afterModelCallCount = afterModelCallCount;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			beforeModelCallCount.incrementAndGet();
			System.out.println("TestMessagesModelHook.beforeModel called with " + previousMessages.size() + " messages");
			return new AgentCommand(previousMessages);
		}

		@Override
		public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
			afterModelCallCount.incrementAndGet();
			System.out.println("TestMessagesModelHook.afterModel called with " + previousMessages.size() + " messages");
			return new AgentCommand(previousMessages);
		}
	}

	/**
	 * MessagesModelHook implementation that uses REPLACE policy
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	private static class ReplacePolicyMessagesHook extends MessagesModelHook {
		@Override
		public String getName() {
			return "replace_policy_hook";
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			// Replace all messages with a new system message and keep the last user message
			// This ensures agent can still function while demonstrating REPLACE policy
			List<Message> newMessages = new ArrayList<>();
			newMessages.add(new SystemMessage("This is the system message after replacement"));
			// Keep the last user message so agent can still respond
			if (!previousMessages.isEmpty()) {
				Message lastMessage = previousMessages.get(previousMessages.size() - 1);
				if (lastMessage instanceof UserMessage) {
					newMessages.add(lastMessage);
				}
			}
			return new AgentCommand(newMessages, UpdatePolicy.REPLACE);
		}
	}

	/**
	 * MessagesModelHook implementation that uses APPEND policy
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	private static class AppendPolicyMessagesHook extends MessagesModelHook {
		@Override
		public String getName() {
			return "append_policy_hook";
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			// Append a new message to existing messages
			List<Message> newMessages = new ArrayList<>();
			newMessages.add(new UserMessage("This is an additional message"));
			return new AgentCommand(newMessages, UpdatePolicy.APPEND);
		}
	}

	/**
	 * MessagesModelHook implementation that jumps to end
	 */
	@HookPositions({HookPosition.BEFORE_MODEL})
	private static class JumpToEndMessagesHook extends MessagesModelHook {
		private final String name;
		private final AtomicInteger beforeModelCallCount;
		private final AtomicInteger afterModelCallCount;

		public JumpToEndMessagesHook(String name, AtomicInteger beforeModelCallCount,
				AtomicInteger afterModelCallCount) {
			this.name = name;
			this.beforeModelCallCount = beforeModelCallCount;
			this.afterModelCallCount = afterModelCallCount;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public List<JumpTo> canJumpTo() {
			return List.of(JumpTo.end);
		}

		@Override
		public AgentCommand beforeModel(List<Message> previousMessages, RunnableConfig config) {
			beforeModelCallCount.incrementAndGet();
			System.out.println("JumpToEndMessagesHook.beforeModel called - jumping to end");
			// Return jumpTo end to skip subsequent hooks and model call
			return new AgentCommand(JumpTo.end, previousMessages);
		}

		@Override
		public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
			afterModelCallCount.incrementAndGet();
			System.out.println("JumpToEndMessagesHook.afterModel called");
			return new AgentCommand(previousMessages);
		}
	}

	/**
	 * Test ModelHook implementation for testing
	 */
	@HookPositions({HookPosition.BEFORE_MODEL, HookPosition.AFTER_MODEL})
	private static class TestModelHook extends ModelHook {
		private final String name;
		private final AtomicInteger beforeModelCallCount;
		private final AtomicInteger afterModelCallCount;

		public TestModelHook(String name, AtomicInteger beforeModelCallCount, AtomicInteger afterModelCallCount) {
			this.name = name;
			this.beforeModelCallCount = beforeModelCallCount;
			this.afterModelCallCount = afterModelCallCount;
		}

		@Override
		public String getName() {
			return name;
		}

		@Override
		public CompletableFuture<Map<String, Object>> beforeModel(OverAllState state, RunnableConfig config) {
			beforeModelCallCount.incrementAndGet();
			System.out.println("TestModelHook.beforeModel called");
			return CompletableFuture.completedFuture(Map.of());
		}

		@Override
		public CompletableFuture<Map<String, Object>> afterModel(OverAllState state, RunnableConfig config) {
			afterModelCallCount.incrementAndGet();
			System.out.println("TestModelHook.afterModel called");
			return CompletableFuture.completedFuture(Map.of());
		}
	}

	/**
	 * Test 7: Verify JumpTo.end works in afterModel hook when agent has tools
	 * This test addresses the bug where JumpTo in afterModel was ignored when tools were configured
	 */
	@Test
	public void testJumpToEndInAfterModelWithTools() throws Exception {
		AtomicInteger afterModelCallCount = new AtomicInteger(0);
		AtomicInteger toolCallCount = new AtomicInteger(0);

		// Hook that only overrides afterModel and uses JumpTo.end
		@HookPositions({HookPosition.AFTER_MODEL})
		class AfterModelOnlyJumpHook extends MessagesModelHook {
			@Override
			public String getName() {
				return "after_model_jump_hook";
			}

			@Override
			public List<JumpTo> canJumpTo() {
				return List.of(JumpTo.end);
			}

			@Override
			public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
				afterModelCallCount.incrementAndGet();
				System.out.println("AfterModelOnlyJumpHook.afterModel called - jumping to end");
				// Return JumpTo.end to skip tool execution and end immediately
				return new AgentCommand(JumpTo.end, previousMessages);
			}
		}

		AfterModelOnlyJumpHook hook = new AfterModelOnlyJumpHook();

		// Create a simple test tool
		ToolCallback testTool = FunctionToolCallback.builder("test_tool", args -> {
					toolCallCount.incrementAndGet();
					System.out.println("Test tool called - this should NOT happen!");
					return "Tool executed";
				})
				.description("A test tool")
				.inputType(String.class)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("test-agent-after-model-jump-with-tools")
				.model(chatModel)
				.tools(List.of(testTool))
				.hooks(List.of(hook))
				.saver(new MemorySaver())
				.build();

		System.out.println("\n=== Test JumpTo.end in afterModel (tool configured) ===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Hello, please introduce yourself briefly."));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");
		assertTrue(afterModelCallCount.get() > 0, "afterModel should be called");

		// Key verification: tool should NOT be called because JumpTo.end should skip it
		assertEquals(0, toolCallCount.get(), "The tool should not be called (because JumpTo.end ends directly)");

		System.out.println("afterModel call times:" + afterModelCallCount.get());
		System.out.println("Number of tool calls:" + toolCallCount.get());
		System.out.println("Successfully verified that JumpTo.end in afterModel works correctly when configured with tools");
	}

	/**
	 * Test 8: Verify JumpTo.model works in afterModel hook when agent has tools
	 */
	@Test
	public void testJumpToModelInAfterModelWithTools() throws Exception {
		AtomicInteger afterModelCallCount = new AtomicInteger(0);
		AtomicInteger modelCallCount = new AtomicInteger(0);
		AtomicInteger toolCallCount = new AtomicInteger(0);

		// Hook that only overrides afterModel and uses JumpTo.model on first call
		@HookPositions({HookPosition.AFTER_MODEL})
		class AfterModelJumpToModelHook extends MessagesModelHook {
			@Override
			public String getName() {
				return "after_model_jump_to_model_hook";
			}

			@Override
			public List<JumpTo> canJumpTo() {
				return List.of(JumpTo.model, JumpTo.end);
			}

			@Override
			public AgentCommand afterModel(List<Message> previousMessages, RunnableConfig config) {
				afterModelCallCount.incrementAndGet();
				System.out.println("AfterModelJumpToModelHook.afterModel called, count: " + afterModelCallCount.get());

				// On first call, jump back to model; on second call, end
				if (afterModelCallCount.get() == 1) {
					System.out.println("First call - jumping back to model");
					return new AgentCommand(JumpTo.model, previousMessages);
				} else {
					System.out.println("Second call - ending");
					return new AgentCommand(JumpTo.end, previousMessages);
				}
			}
		}

		AfterModelJumpToModelHook hook = new AfterModelJumpToModelHook();

		// Create a test tool (required to trigger makeModelToTools routing)
		ToolCallback testTool = FunctionToolCallback.builder("test_tool", args -> {
					toolCallCount.incrementAndGet();
					System.out.println("Test tool called");
					return "Tool executed";
				})
				.description("A test tool")
				.inputType(String.class)
				.build();

		// Track model calls
		ChatModel trackingChatModel = new ChatModel() {
			@Override
			public ChatResponse call(Prompt prompt) {
				modelCallCount.incrementAndGet();
				System.out.println("Model called, count: " + modelCallCount.get());
				return chatModel.call(prompt);
			}

			@Override
			public ChatOptions getDefaultOptions() {
				return chatModel.getDefaultOptions();
			}
		};

		ReactAgent agent = ReactAgent.builder()
				.name("test-agent-after-model-jump-to-model")
				.model(trackingChatModel)
				.tools(List.of(testTool))
				.hooks(List.of(hook))
				.saver(new MemorySaver())
				.build();

		System.out.println("\n=== test afterModel in JumpTo.model ===");

		List<Message> messages = new ArrayList<>();
		messages.add(new UserMessage("Hello"));

		Optional<OverAllState> result = agent.invoke(messages);

		assertTrue(result.isPresent(), "The result should exist");
		assertEquals(2, afterModelCallCount.get(), "afterModel should be called 2 times (the first time jumps back to the model, the second time ends)");
		assertEquals(2, modelCallCount.get(), "The model should be called 2 times (first time normally, second time because of JumpTo.model)");
		// Tools should not be called because JumpTo redirects flow before tool execution
		assertEquals(0, toolCallCount.get(), "The tool should not be called (because JumpTo jumps directly)");

		System.out.println("afterModel call times:" + afterModelCallCount.get());
		System.out.println("Number of model calls:" + modelCallCount.get());
		System.out.println("Number of tool calls:" + toolCallCount.get());
		System.out.println("Successfully verified that JumpTo.model in afterModel works properly when tool configuration is available");
	}
}
