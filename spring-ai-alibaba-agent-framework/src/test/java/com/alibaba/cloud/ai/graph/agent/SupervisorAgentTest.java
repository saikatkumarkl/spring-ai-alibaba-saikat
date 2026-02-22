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
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.hook.AgentHook;
import com.alibaba.cloud.ai.graph.agent.utils.HookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class SupervisorAgentTest {

	private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testSupervisorAgentWithSimpleAgents() throws Exception {
		// Create simple ReactAgent sub-agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing all kinds of articles, including prose, poetry and other literary works")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.outputKey("writer_output")
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translating articles into various languages")
				.instruction("You are a professional translator who can accurately translate articles into the target language.")
				.outputKey("translator_output")
				.build();

		// Create SupervisorAgent (mainAgent is required)
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("Content management supervisor, responsible for coordinating writing, translation and other tasks")
				.model(chatModel)
				.mainAgent(ReactAgent.builder()
						.name("main_agent")
						.model(chatModel)
						.description("Supervisor main agent, responsible for routing decisions")
						.systemPrompt("""
							You are an intelligent content processing supervisor.
							Available sub-agents: writer_agent (writing), translator_agent (translation)

							## Routing Decision Output Format (only applicable when selecting a sub-agent)
							When and only when a routing decision is needed (selecting the next sub-agent to invoke or finishing the task), output in JSON array format for the system to parse the routing; this format is only used for this routing decision and does not affect your main task output format in other scenarios.
							- To select a single sub-agent, output: ["writer_agent"] or ["translator_agent"]
							- To select multiple sub-agents in parallel, output: ["writer_agent", "translator_agent"]
							- When all tasks are completed, output: [] or ["FINISH"]
							Valid elements are limited to: writer_agent, translator_agent, FINISH. When making routing decisions, only output the above JSON array without any other explanation.
							""")
						.instruction("The user's request is: {input}")
						.outputKey("final_output")
						.build())
				.subAgents(List.of(writerAgent, translatorAgent))
				.build();

		try {
			// Test 1: Simple writing task
			Optional<OverAllState> result1 = supervisorAgent.invoke("Help me write a short essay about spring");

			assertTrue(result1.isPresent(), "Result should be present");
			OverAllState state1 = result1.get();

			// Verify input is preserved
			assertTrue(state1.value("input").isPresent(), "Input should be present in state");
			assertEquals("Help me write a short essay about spring", state1.value("input").get(), "Input should match the request");

			// Verify writer agent output exists
			assertTrue(state1.value("writer_output").isPresent(), "Writer output should be present");
			AssistantMessage writerContent = (AssistantMessage) state1.value("writer_output").get();
			assertNotNull(writerContent.getText(), "Writer content should not be null");
			assertTrue(writerContent.getText().length() > 0, "Writer content should not be empty");

			// Test 2: Translation task
			Optional<OverAllState> result2 = supervisorAgent.invoke("Please translate the following content into English: Spring is warm and flowers are blooming");

			assertTrue(result2.isPresent(), "Translation result should be present");
			OverAllState state2 = result2.get();

			// Verify translator agent output exists
			assertTrue(state2.value("translator_output").isPresent(), "Translator output should be present");
			AssistantMessage translatorContent = (AssistantMessage) state2.value("translator_output").get();
			assertNotNull(translatorContent.getText(), "Translator content should not be null");
			assertTrue(translatorContent.getText().length() > 0, "Translator content should not be empty");

			System.out.println("Test 1 - Writer output: " + writerContent.getText());
			System.out.println("Test 2 - Translator output: " + translatorContent.getText());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testSupervisorAgentWithNestedSequentialAgent() throws Exception {
		// Create simple ReactAgent
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing all kinds of articles, including prose, poetry and other literary works")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.outputKey("writer_output")
				.build();

		// Create nested SequentialAgent
		ReactAgent articleWriterAgent = ReactAgent.builder()
				.name("article_writer")
				.model(chatModel)
				.description("Professional Writing Agent")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's question: {input}.")
				.outputKey("article")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer")
				.model(chatModel)
				.description("Professional Review Agent")
				.instruction("You are a well-known critic who is good at commenting and revising articles." +
						"For prose articles, please ensure that the article must include a description of the scenery of West Lake.Articles awaiting comment:\n\n {article}" +
						"Finally, only the revised article will be returned without any comment information.")
				.outputKey("reviewed_article")
				.build();

		// Create nested SequentialAgent
		SequentialAgent writingWorkflowAgent = SequentialAgent.builder()
				.name("writing_workflow_agent")
				.description("Complete writing workflow: write the article first, then review and revise it")
				.subAgents(List.of(articleWriterAgent, reviewerAgent))
				.build();

		// Define professional supervisor instruction
		final String SUPERVISOR_SYSTEM_PROMPT = """
				You are an intelligent content management supervisor responsible for coordinating and managing multiple specialized agents to fulfill users' content processing needs.

				## Your Responsibilities
				1. Analyze user requirements and break them down into appropriate sub-tasks
				2. Select the appropriate agent based on task characteristics
				3. Monitor task execution status and decide whether to continue processing or complete the task
				4. When all tasks are completed, return FINISH to end the workflow

				## Available Sub-Agents and Their Responsibilities

				### writer_agent
				- **Function**: Excels at creating various articles, including prose, poetry, and other literary works
				- **Applicable Scenarios**: 
				  * User needs to create new articles, prose, poetry, or other original content
				  * Simple writing tasks that do not require subsequent review or revision
				- **Output**: writer_output

				### writing_workflow_agent
				- **Function**: Complete writing workflow with two steps: write the article first, then review and revise it
				- **Applicable Scenarios**:
				  * User needs high-quality articles that require review and revision
				  * Task explicitly requires "ensure quality", "Need review", "Need to modify", etc.
				  * Complex writing tasks requiring multi-step processing
				- **Workflow**: 
				  1. article_writer: Creates articles based on user requirements
				  2. reviewer: Reviews and revises the article to ensure quality
				- **Output**: reviewed_article

				## Decision Rules

				1. **Single Task Judgment**:
				   - If the user only needs simple writing, select writer_agent
				   - If the user needs high-quality articles or explicitly requires review, select writing_workflow_agent

				2. **Task Completion Judgment**:
				   - When all user requirements are fulfilled, return FINISH
				   - If there are still unfinished tasks, continue routing to the appropriate agent

				## Routing Decision Output Format (only applicable when selecting a sub-agent)
				When and only when a routing decision is needed (selecting the next sub-agent to invoke or finishing the task), output in JSON array format for the system to parse the routing; this format is only used for this routing decision and does not affect your main task output format in other scenarios.
				- To select a single sub-agent, output: ["writer_agent"] or ["writing_workflow_agent"]
				- To select multiple sub-agents in parallel, output: ["writer_agent", "writing_workflow_agent"]
				- When all tasks are completed, output: [] or ["FINISH"]
				Valid elements are limited to: writer_agent, writing_workflow_agent, FINISH. When making routing decisions, only output the above JSON array without any other explanation.
				""";

		// Create SupervisorAgent with nested SequentialAgent (mainAgent is required)
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("Content management supervisor responsible for tasks such as coordinating writing and the complete writing workflow")
				.model(chatModel)
				.mainAgent(ReactAgent.builder()
						.name("main_agent")
						.model(chatModel)
						.description("Supervisor main agent, responsible for routing decisions")
						.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
						.instruction("The user's request is: {input}")
						.outputKey("final_output")
						.build())
				.subAgents(List.of(writerAgent, writingWorkflowAgent))
				.build();

		try {
			// Test: Task requiring quality (should route to writing_workflow_agent)
			Optional<OverAllState> result = supervisorAgent.invoke("Help me write an essay about West Lake and ensure the quality");

			assertTrue(result.isPresent(), "Result should be present");
			OverAllState state = result.get();

			// Verify input is preserved
			assertTrue(state.value("input").isPresent(), "Input should be present in state");
			assertEquals("Help me write an essay about West Lake and ensure the quality", state.value("input").get(), "Input should match the request");

			// Verify nested SequentialAgent output exists (reviewed_article from writing_workflow_agent)
			assertTrue(state.value("reviewed_article").isPresent(),
					"Reviewed article should be present after writing workflow agent");
			AssistantMessage reviewedContent = (AssistantMessage) state.value("reviewed_article").get();
			assertNotNull(reviewedContent.getText(), "Reviewed content should not be null");
			assertTrue(reviewedContent.getText().length() > 0, "Reviewed content should not be empty");

			// Verify intermediate output from nested agent also exists
			assertTrue(state.value("article").isPresent(), "Article should be present from nested SequentialAgent");

			System.out.println("Reviewed article: " + reviewedContent.getText());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent with nested SequentialAgent execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testSupervisorAgentGraphRepresentation() throws Exception {
		// Create simple sub-agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing various articles")
				.instruction("You are a well-known author.")
				.outputKey("writer_output")
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translating articles into various languages")
				.instruction("You are a professional translator.")
				.outputKey("translator_output")
				.build();

		// Create SupervisorAgent (mainAgent is required)
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

							## Routing Decision Output Format (only applicable when selecting a sub-agent)
							When and only when a routing decision is needed (selecting the next sub-agent to invoke or finishing the task), output in JSON array format for the system to parse the routing; this format is only used for this routing decision and does not affect your main task output format in other scenarios.
							- To select a single sub-agent, output: ["writer_agent"] or ["translator_agent"]
							- To select multiple sub-agents in parallel, output: ["writer_agent", "translator_agent"]
							- When all tasks are completed, output: [] or ["FINISH"]
							Valid elements are limited to: writer_agent, translator_agent, FINISH. When making routing decisions, only output the above JSON array without any other explanation.
							""")
						.instruction("The user's request is: {input}")
						.outputKey("final_output")
						.build())
				.subAgents(List.of(writerAgent, translatorAgent))
				.build();

		try {
			// Test graph representation
			GraphRepresentation representation = supervisorAgent.getGraph()
					.getGraph(GraphRepresentation.Type.PLANTUML);
			assertNotNull(representation, "Graph representation should not be null");
			assertNotNull(representation.content(), "Graph representation content should not be null");
			assertTrue(representation.content().length() > 0, "Graph representation content should not be empty");

			// Verify graph contains supervisor and sub-agents
			String content = representation.content();
			assertTrue(content.contains("content_supervisor"), "Graph should contain supervisor agent");
			assertTrue(content.contains("writer_agent"), "Graph should contain writer agent");
			assertTrue(content.contains("translator_agent"), "Graph should contain translator agent");

			System.out.println("Graph representation:");
			System.out.println(representation.content());
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent graph representation failed: " + e.getMessage());
		}
	}

	@Test
	public void testSupervisorAgentMultiStepTask() throws Exception {
		// Create sub-agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing all kinds of articles, including prose, poetry and other literary works")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's question: \n\n {input}.")
				.outputKey("writer_output")
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translating articles into various languages")
				.instruction("You are a professional translator who can accurately translate articles into the target language.Articles to be translated:\n\n {writer_output}.")
				.outputKey("translator_output")
				.build();

		// Define supervisor instruction for multi-step tasks
		final String SUPERVISOR_SYSTEM_PROMPT = """
				You are an intelligent content management supervisor.
				
				## Available Sub-Agents and Their Responsibilities
				
				### writer_agent
				- **Function**: Excels at creating various articles, including prose, poetry, and other literary works
				- **Output**: writer_output
				
				### translator_agent
				- **Function**: Excels at translating articles into various languages
				- **Output**: translator_output
				
				## Decision Rules
				
				1. **Multi-step Task Processing**:
				   - If the user's request involves multiple steps (e.g., "Write the article first, then translate it"), process them step by step
				   - Route to the first appropriate agent and wait for completion
				   - After completion, continue routing to the next agent based on remaining requirements
				   - Return FINISH when all steps are completed
				
				2. **Task Completion Judgment**:
				   - When all user requirements are fulfilled, return FINISH
				
				## Routing Decision Output Format (only applicable when selecting a sub-agent)
				When and only when a routing decision is needed (selecting the next sub-agent to invoke or finishing the task), output in JSON array format for the system to parse the routing; this format is only used for this routing decision and does not affect your main task output format in other scenarios.
				- To select a single sub-agent, output: ["writer_agent"] or ["translator_agent"]
				- To select multiple sub-agents in parallel, output: ["writer_agent", "translator_agent"]
				- When all tasks are completed, output: [] or ["FINISH"]
				Valid elements are limited to: writer_agent, translator_agent, FINISH. When making routing decisions, only output the above JSON array without any other explanation.
				""";

		// Create SupervisorAgent (mainAgent is required)
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("Content management supervisor, responsible for coordinating writing and translation tasks")
				.model(chatModel)
				.mainAgent(ReactAgent.builder()
						.name("main_agent")
						.model(chatModel)
						.description("Supervisor main agent, responsible for routing decisions")
						.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
						.instruction("The user's request is: {input}")
						.outputKey("final_output")
						.build())
				.subAgents(List.of(writerAgent, translatorAgent))
				.build();

		GraphRepresentation representation = supervisorAgent.getGraph()
				.getGraph(GraphRepresentation.Type.PLANTUML);
		// Verify graph contains supervisor and sub-agents
		String content = representation.content();

		System.out.println("===================");
		System.out.println(content);

		try {
			// Test multi-step task: write first, then translate
			Optional<OverAllState> result = supervisorAgent.invoke("First help me write an article about spring and then translate it into English");

			assertTrue(result.isPresent(), "Result should be present");
			OverAllState state = result.get();

			// Verify input is preserved
			assertTrue(state.value("input").isPresent(), "Input should be present in state");

			// Verify both outputs exist (indicating multi-step execution)
			// Note: Depending on the supervisor's decision, both outputs may or may not be present
			// The supervisor might route to writer first, then translator, or handle it differently
			boolean hasWriterOutput = state.value("writer_output").isPresent();
			boolean hasTranslatorOutput = state.value("translator_output").isPresent();

			// At least one output should be present
			assertTrue(hasWriterOutput || hasTranslatorOutput,
					"At least one agent output should be present after multi-step task");

			if (hasWriterOutput) {
				AssistantMessage writerContent = (AssistantMessage) state.value("writer_output").get();
				assertNotNull(writerContent.getText(), "Writer content should not be null");
				System.out.println("Writer output: " + writerContent.getText());
			}

			if (hasTranslatorOutput) {
				AssistantMessage translatorContent = (AssistantMessage) state.value("translator_output").get();
				assertNotNull(translatorContent.getText(), "Translator content should not be null");
				System.out.println("Translator output: " + translatorContent.getText());
			}
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent multi-step task execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testSupervisorAgentAsSequentialSubAgentWithPlaceholder() throws Exception {
		// Create first ReactAgent that will output content for SupervisorAgent to process
		ReactAgent articleWriterAgent = ReactAgent.builder()
				.name("article_writer")
				.model(chatModel)
				.description("Professional writing agent, responsible for creating articles")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's question: {input}.")
				.outputKey("article_content")
				.build();

		// Create sub-agents for SupervisorAgent
		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translating articles into various languages")
				.instruction("You are a professional translator who can accurately translate articles into the target language.Article to be translated:\n\n {article_content}.")
				.outputKey("translator_output")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Good at reviewing and revising articles")
				.instruction("You are a well-known critic who is good at commenting and revising articles.Articles to be reviewed:\n\n {article_content}."
						+ "Please review the article, point out the strengths and areas for improvement, and return an improved version after review.")
				.outputKey("reviewer_output")
				.build();

		// Define supervisor instruction that uses placeholder to read previous agent output
		// The instruction contains {article_content} placeholder which will be replaced
		// with the output from the first ReactAgent in SequentialAgent
		final String SUPERVISOR_INSTRUCTION = """
				You are an intelligent content processing supervisor who can see the chat history and task processing records of preceding Agents. Currently, you have received the following article content:

				{article_content}

				Based on the characteristics of the article content, decide whether to translate or review it:
				- If the article is in Chinese and needs translation, choose translator_agent
				- If the article needs review and improvement, choose reviewer_agent
				- If the task is complete, return FINISH
				""";

		final String SUPERVISOR_SYSTEM_PROMPT = """
				You are an intelligent content processing supervisor responsible for coordinating translation and review tasks.

				## Available Sub-Agents and Their Responsibilities

				### translator_agent
				- **Function**: Skilled at translating articles into various languages
				- **Use Case**: When articles need to be translated into other languages
				- **Output**: translator_output

				### reviewer_agent
				- **Function**: Skilled at reviewing and revising articles
				- **Use Case**: When articles need review, improvement, or optimization
				- **Output**: reviewer_output

				## Decision Rules

				1. **Judge based on article content**:
				   - If the article is in Chinese and the user requests translation, choose translator_agent
				   - If the article needs review, improvement, or optimization, choose reviewer_agent

				2. **Task completion judgment**:
				   - When all tasks are completed, return FINISH

				## Routing Decision Output Format (only applicable when selecting sub-Agents)
				When and only when a routing decision needs to be made (selecting the next sub-Agent to invoke or ending the task), output in JSON array format for system routing parsing; this format is only for routing and does not affect your primary task output format in other scenarios.
				- When selecting a single sub-Agent, output: ["translator_agent"] or ["reviewer_agent"]
				- When selecting multiple sub-Agents in parallel, output: ["translator_agent", "reviewer_agent"]
				- When all tasks are completed, output: [] or ["FINISH"]
				Valid elements are limited to: translator_agent, reviewer_agent, FINISH. When making routing decisions, only output the above JSON array without any additional explanation.
				""";

		// Create SupervisorAgent with instruction that uses placeholder (mainAgent is required)
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("The content processing supervisor determines translation or review based on the output of the pre-order Agent.")
				.model(chatModel)
				.mainAgent(ReactAgent.builder()
						.name("main_agent")
						.model(chatModel)
						.description("Supervisor main agent, responsible for routing decisions")
						.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
						.instruction(SUPERVISOR_INSTRUCTION) // This instruction contains {article_content} placeholder
						.outputKey("final_output")
						.build())
				.subAgents(List.of(translatorAgent, reviewerAgent))
				.build();

		// Create SequentialAgent with articleWriterAgent first, then supervisorAgent
		SequentialAgent sequentialAgent = SequentialAgent.builder()
				.name("content_processing_workflow")
				.description("Content processing workflow: write the article first, then decide on translation or review based on the content of the article")
				.subAgents(List.of(articleWriterAgent, supervisorAgent))
				.build();

		try {
			// Test: Write an article first, then supervisor decides to translate it
			Optional<OverAllState> result = sequentialAgent.invoke("Help me write a short essay about spring");

			assertTrue(result.isPresent(), "Result should be present");
			OverAllState state = result.get();

			// Verify input is preserved
			assertTrue(state.value("input").isPresent(), "Input should be present in state");
			assertEquals("Help me write a short essay about spring", state.value("input").get(),
					"Input should match the request");

			// Verify first agent output exists (article_content)
			assertTrue(state.value("article_content").isPresent(),
					"Article content should be present from first agent");
			AssistantMessage articleContent = (AssistantMessage) state.value("article_content").get();
			assertNotNull(articleContent.getText(), "Article content should not be null");
			assertTrue(articleContent.getText().length() > 0, "Article content should not be empty");

			// Verify supervisor agent processed the article content
			// The supervisor should have routed to either translator or reviewer based on the instruction
			boolean hasTranslatorOutput = state.value("translator_output").isPresent();
			boolean hasReviewerOutput = state.value("reviewer_output").isPresent();

			// At least one output from supervisor's sub-agents should be present
			assertTrue(hasTranslatorOutput || hasReviewerOutput,
					"At least one supervisor sub-agent output should be present");

			System.out.println("Article content: " + articleContent.getText());
			if (hasTranslatorOutput) {
				AssistantMessage translatorContent = (AssistantMessage) state.value("translator_output").get();
				assertNotNull(translatorContent.getText(), "Translator content should not be null");
				System.out.println("Translator output: " + translatorContent.getText());
			}
			if (hasReviewerOutput) {
				AssistantMessage reviewerContent = (AssistantMessage) state.value("reviewer_output").get();
				assertNotNull(reviewerContent.getText(), "Reviewer content should not be null");
				System.out.println("Reviewer output: " + reviewerContent.getText());
			}

			// Verify that the supervisor's instruction placeholder was properly replaced
			// by checking that the supervisor actually processed the article content
			// (This is implicit in the fact that one of the sub-agents was invoked)
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent as SequentialAgent sub-agent with placeholder failed: " + e.getMessage());
		}
	}

	@Test
	public void testSupervisorAgentWithHookFactory() throws Exception {
		// Create sub-agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing various articles")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer according to the user's question: {input}")
				.includeContents(false)
				.returnReasoningContents(false)
				.outputKey("writer_output")
				.build();

		ReactAgent reviewAgent = ReactAgent.builder()
				.name("review_agent")
				.model(chatModel)
				.includeContents(false)
				.returnReasoningContents(false)
				.description("Comment on article content")
				.instruction("You are a well-known writer who is good at writing and creating.Please comment on user articles: {writer_output}")
				.outputKey("review_output")
				.build();

		// Use HookFactory to create a LogAgentHook
		AgentHook logHook = HookFactory.createLogAgentHook();

		final String SUPERVISOR_SYSTEM_PROMPT = """
				You are an intelligent content processing supervisor responsible for coordinating writing and review tasks.

				## Available Sub-Agents and Their Responsibilities

				### writer_agent
				- **Function**: Skilled at writing various articles and poems
				- **Use Case**: When there is a writing requirement
				- **Output**: writer_output

				### review_agent
				- **Function**: Skilled at reviewing and revising articles
				- **Use Case**: When articles need review, improvement, or optimization
				- **Output**: review_output

				## Decision Rules

				1. **Judge based on the current task to be completed**:
				   - If an article or poem needs to be written, choose writer_agent
				   - If the article needs review, improvement, or optimization, choose review_agent

				2. **Task completion judgment**:
				   - When all tasks are completed, return an empty array or FINISH
				
				3. **Note**:
				   - If needed, multiple sub-Agents can be selected simultaneously for parallel task processing

				## Routing Decision Output Format (only applicable when selecting sub-Agents)
				When and only when a routing decision needs to be made (selecting the next sub-Agent to invoke or ending the task), output in JSON array format for system routing parsing; this format is only for routing and does not affect your primary task output format in other scenarios.
				- When selecting a single sub-Agent, output: ["writer_agent"] or ["review_agent"]
				- When selecting multiple sub-Agents in parallel, output: ["writer_agent", "review_agent"]
				- When all tasks are completed, output: [] or ["FINISH"]
				Valid elements are limited to: writer_agent, review_agent, FINISH. When making routing decisions, only output the above JSON array without any additional explanation.
				""";

		// Create SupervisorAgent with the hook (mainAgent is assembled from systemPrompt, instruction, model, hooks)
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("Content Management Supervisor, responsible for coordinating writing")
				.model(chatModel)
				.mainAgent(ReactAgent.builder()
						.name("main_agent")
						.model(chatModel)
						.description("Supervisor main agent, responsible for routing decisions")
						.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
						.instruction("The user’s writing requirement is: {input}")
						.outputKey("final_output")
						.build())
				.subAgents(List.of(writerAgent, reviewAgent))
				.hooks(List.of(logHook))
				.build();

		try {

			printGraphRepresentation(supervisorAgent.asStateGraph());

			System.out.println("\n========== Starting SupervisorAgent with HookFactory Test ==========\n");

			// Execute the agent
			Optional<OverAllState> result = supervisorAgent.invoke("Help me write a short essay about spring");

			assertTrue(result.isPresent(), "Result should be present");
			OverAllState state = result.get();

			// Verify input is preserved
			assertTrue(state.value("input").isPresent(), "Input should be present in state");
			assertEquals("Help me write a short essay about spring", state.value("input").get(), "Input should match the request");

			// Verify at least one agent output exists
			boolean hasWriterOutput = state.value("writer_output").isPresent();
			assertTrue(hasWriterOutput, "Writer output should be present");

			System.out.println("\n========== SupervisorAgent with HookFactory Test Completed ==========\n");

            AssistantMessage writerContent = (AssistantMessage) state.value("writer_output").get();
            System.out.println("Writer output: " + writerContent.getText());
        }
		catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent with HookFactory execution failed: " + e.getMessage());
		}
	}

	/**
	 * Stream test for the same SupervisorAgent setup as testSupervisorAgentWithHookFactory.
	 * Prints each NodeOutput with agent name and node name to verify streaming output from
	 * main agent and sub-agents.
	 */
	@Test
	public void testSupervisorAgentWithHookFactoryStream() throws Exception {
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing various articles")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer according to the user's question: {input}")
				.includeContents(false)
				.returnReasoningContents(false)
				.outputKey("writer_output")
				.build();

		ReactAgent reviewAgent = ReactAgent.builder()
				.name("review_agent")
				.model(chatModel)
				.includeContents(false)
				.returnReasoningContents(false)
				.description("Comment on article content")
				.instruction("You are a well-known writer who is good at writing and creating.Please comment on user articles: {writer_output}")
				.outputKey("review_output")
				.build();

		AgentHook logHook = HookFactory.createLogAgentHook();

		final String SUPERVISOR_SYSTEM_PROMPT = """
				You are an intelligent content processing supervisor responsible for coordinating writing and review tasks.

				## Available Sub-Agents and Their Responsibilities

				### writer_agent
				- **Function**: Skilled at writing various articles and poems
				- **Use Case**: When there is a writing requirement
				- **Output**: writer_output

				### review_agent
				- **Function**: Skilled at reviewing and revising articles
				- **Use Case**: When articles need review, improvement, or optimization
				- **Output**: review_output

				## Decision Rules

				1. **Judge based on the current task to be completed**:
				   - If an article or poem needs to be written, choose writer_agent
				   - If the article needs review, improvement, or optimization, choose review_agent

				2. **Task completion judgment**:
				   - When all tasks are completed, return an empty array or FINISH

				3. **Note**:
				   - If needed, multiple sub-Agents can be selected simultaneously for parallel task processing

				## Routing Decision Output Format (only applicable when selecting sub-Agents)
				When and only when a routing decision needs to be made (selecting the next sub-Agent to invoke or ending the task), output in JSON array format for system routing parsing; this format is only for routing and does not affect your primary task output format in other scenarios.
				- When selecting a single sub-Agent, output: ["writer_agent"] or ["review_agent"]
				- When selecting multiple sub-Agents in parallel, output: ["writer_agent", "review_agent"]
				- When all tasks are completed, output: [] or ["FINISH"]
				Valid elements are limited to: writer_agent, review_agent, FINISH. When making routing decisions, only output the above JSON array without any additional explanation.
				""";

		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("Content Management Supervisor, responsible for coordinating writing")
				.model(chatModel)
				.mainAgent(ReactAgent.builder()
						.name("main_agent")
						.model(chatModel)
						.description("Supervisor main agent, responsible for routing decisions")
						.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
						.instruction("The user’s writing requirement is: {input}")
						.outputKey("final_output")
						.build())
				.subAgents(List.of(writerAgent, reviewAgent))
				.hooks(List.of(logHook))
				.build();

		try {
			printGraphRepresentation(supervisorAgent.asStateGraph());
			System.out.println("\n========== SupervisorAgent with HookFactory Stream Test ==========\n");

			List<NodeOutput> outputs = new ArrayList<>();
			Flux<NodeOutput> stream = supervisorAgent.stream("Help me write a short essay about spring");

			stream.doOnNext(output -> {
				String agentName = output.agent() != null ? output.agent() : "(no agent)";
				String nodeName = output.node() != null ? output.node() : "(no node)";
				System.out.println("[agent=" + agentName + "] [node=" + nodeName + "] " + output);
				outputs.add(output);
			}).blockLast();

			System.out.println("\n--- Stream completed. Total outputs: " + outputs.size() + " ---\n");

			assertFalse(outputs.isEmpty(), "Stream should emit at least one NodeOutput");
			NodeOutput last = outputs.get(outputs.size() - 1);
			assertTrue(last.state() != null && last.state().value("input").isPresent(),
					"Final state should contain input");

			System.out.println("========== SupervisorAgent with HookFactory Stream Test Completed ==========\n");
		}
		catch (Exception e) {
			e.printStackTrace();
			fail("SupervisorAgent with HookFactory stream execution failed: " + e.getMessage());
		}
	}

	private void printGraphRepresentation(StateGraph graph) {
		GraphRepresentation representation = graph.getGraph(GraphRepresentation.Type.PLANTUML);
		System.out.println(representation.content());
	}
}

