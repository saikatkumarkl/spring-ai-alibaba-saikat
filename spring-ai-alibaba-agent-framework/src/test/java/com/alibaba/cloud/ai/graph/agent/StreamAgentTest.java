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
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class StreamAgentTest {

	private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		//First create a DashScopeApi instance
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		//Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testStreamLlmRoutingAgent() throws Exception {
		ReactAgent proseWriterAgent = ReactAgent.builder()
			.name("prose_writer_agent")
			.model(chatModel)
			.description("Can write prose articles.")
			.instruction("You are a well-known writer, good at writing prose.Please answer based on the user's questions.")
			.outputKey("prose_article")
			.build();

		ReactAgent poemWriterAgent = ReactAgent.builder()
			.name("poem_writer_agent")
			.model(chatModel)
			.description("Can write modern poetry.")
			.instruction("You are a well-known poet who is good at writing modern poetry.Please answer based on the user's questions.")
			.outputKey("poem_article")
			.build();

		LlmRoutingAgent blogAgent = LlmRoutingAgent.builder()
			.name("blog_agent")
			.model(chatModel)
			.description("You can write articles or poems based on topics given by the user.")
			.subAgents(List.of(proseWriterAgent, poemWriterAgent))
			.build();

		try {
			List<NodeOutput> outputs = new ArrayList<>();

			Flux<NodeOutput> result = blogAgent.stream("Help me write a prose of about 100 words");
			result.doOnNext(nodeOutput -> {
				System.out.println(nodeOutput);
				outputs.add(nodeOutput);
			}).then().block();

			assertFalse(outputs.isEmpty());
			var last = outputs.get(outputs.size() - 1);
			var finalState = last.state();
			assertTrue(finalState.value("prose_article").isPresent());
			assertFalse(finalState.value("poem_article").isPresent());
		}
		catch (Exception e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testStreamMessageAgent() throws Exception {
		ReactAgent proseWriterAgent = ReactAgent.builder()
				.name("prose_writer_agent")
				.model(chatModel)
				.description("Can write prose articles.")
				.instruction("You are a well-known writer, good at writing prose.Please answer based on the user's questions.")
				.build();

		List<Message> outputs = new ArrayList<>();

		Flux<Message> result = proseWriterAgent.streamMessages("Help me write a prose of about 100 words");
		result.doOnNext(message -> {
			System.out.println(message);
			outputs.add(message);
		}).then().block();

		assertFalse(outputs.isEmpty());
    }

	@Test
	public void testStreamMessageLlmRoutingAgent() throws Exception {
		ReactAgent proseWriterAgent = ReactAgent.builder()
				.name("prose_writer_agent")
				.model(chatModel)
				.description("Can write prose articles.")
				.instruction("You are a well-known writer who only writes prose. If you do not write prose, you will directly refuse to write.Please answer based on the user's questions.")
				.outputKey("prose_article")
				.build();

		ReactAgent poemWriterAgent = ReactAgent.builder()
				.name("poem_writer_agent")
				.model(chatModel)
				.description("Can write modern poetry.")
				.instruction("You are a well-known poet who only writes modern poetry. If you do not write modern poetry, you will directly refuse to write.Please answer based on the user's questions.")
				.outputKey("poem_article")
				.build();

		LlmRoutingAgent writerAgent = LlmRoutingAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("You can write articles or poems based on topics given by the user.")
				.subAgents(List.of(proseWriterAgent, poemWriterAgent))
				.build();

		List<Message> outputs = new ArrayList<>();

		Flux<Message> result = writerAgent.streamMessages("Help me write a prose of about 100 words");
		result.doOnNext(message -> {
			System.out.println(message);
			outputs.add(message);
		}).then().block();

		assertFalse(outputs.isEmpty());
	}

	@Test
	public void testStreamMessageWithAgentToolFinishedType() throws Exception {
		ReactAgent oddAgent = ReactAgent.builder()
				.name("return_agent0")
				.model(chatModel)
				.description("Odd Agent")
				.instruction("If it is an odd number, the number 111 is returned.Otherwise, no information is returned.")
				.build();

		ReactAgent evenAgent = ReactAgent.builder()
				.name("return_agent1")
				.model(chatModel)
				.description("Even Agent")
				.instruction("If it is an even number, the number 222 is returned.Otherwise, no information is returned.")
				.build();

		ReactAgent numberAgent = ReactAgent.builder()
				.name("blog_agent")
				.model(chatModel)
				.instruction("The number entered by the user is handed over to the corresponding Agent for processing." +
						"After the call is completed, if the result is 111, 333 is output; if the result is 222, 444 is output." +
						"Don't make any additional reasoning or tool calls.")
				.tools(List.of(
						AgentTool.getFunctionToolCallback(oddAgent),
						AgentTool.getFunctionToolCallback(evenAgent)
				))
				.build();

		List<Message> outputs = new ArrayList<>();
		numberAgent.streamMessages("2")
				.filter(message -> message instanceof ToolResponseMessage)
				.doOnNext(outputs::add)
				.then()
				.block();

		assertFalse(outputs.isEmpty());
	}

}
