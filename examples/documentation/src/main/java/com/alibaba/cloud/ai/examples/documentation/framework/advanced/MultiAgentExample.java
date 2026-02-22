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
package com.alibaba.cloud.ai.examples.documentation.framework.advanced;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.GraphResponse;
import com.alibaba.cloud.ai.graph.GraphRepresentation;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.LlmRoutingAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SupervisorAgent;
import com.alibaba.cloud.ai.graph.agent.renderer.SaaStTemplateRenderer;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.template.TemplateRenderer;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

import reactor.core.publisher.Flux;

/**
 * Multi-agent example
 *
 * Demonstrates different multi-agent collaboration modes, including:
 * 1. Sequential execution (Sequential Agent)
 * 2. Parallel execution (Parallel Agent)
 * 3. LLM routing (LlmRoutingAgent)
 * 4. Customize merge strategy
 * 5. Supervisor mode (SupervisorAgent)
 *
 * Reference documentation: advanced_doc/multi-agent.md
 */
public class MultiAgentExample {

	private final ChatModel chatModel;

	public MultiAgentExample(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	/**
	 * Main method: run all examples
	 *
	 * Note: A ChatModel instance needs to be configured to run
	 */
	public static void main(String[] args) {
		//Create a DashScope API instance
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		//Create ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		if (chatModel == null) {
			System.err.println("Error: Please configure ChatModel instance first");
			System.err.println("Please set the AI_DASHSCOPE_API_KEY environment variable");
			return;
		}

		//Create a sample instance
		MultiAgentExample example = new MultiAgentExample(chatModel);

		//Run all examples
		example.runAllExamples();
	}

	/**
	 * Example 1: Sequential execution (Sequential Agent)
	 *
	 * Multiple agents are executed sequentially in a predefined order, and the output of each agent becomes the input of the next agent.
	 */
	public void example1_sequentialAgent() throws Exception {
		//Create specialized sub-Agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Professional Writing Agent")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's question: {input}.")
				.outputKey("article")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Professional Review Agent")
				.instruction("You are a well-known critic who is good at commenting and revising articles." +
						"For prose articles, please ensure that the article must include a description of the scenery of West Lake.Articles awaiting comment:\n\n {article}" +
						"Finally, only the revised article will be returned without any comment information.")
				.outputKey("reviewed_article")
				.build();

		//Create a sequence agent
		SequentialAgent blogAgent = SequentialAgent.builder()
				.name("blog_agent")
				.description("Write an article based on the topic given by the user, and then submit the article to reviewers for comments")
				.subAgents(List.of(writerAgent, reviewerAgent))
				.build();

		//use
		Optional<OverAllState> result = blogAgent.invoke("Help me write a prose of about 100 words");

		if (result.isPresent()) {
			OverAllState state = result.get();

			//Access the output of the first Agent
			state.value("article").ifPresent(article -> {
				if (article instanceof AssistantMessage) {
					System.out.println("Original article:" + ((AssistantMessage) article).getText());
				}
			});

			//Access the output of the second agent
			state.value("reviewed_article").ifPresent(reviewedArticle -> {
				if (reviewedArticle instanceof AssistantMessage) {
					System.out.println("Articles after review:" + ((AssistantMessage) reviewedArticle).getText());
				}
			});
		}
	}

	/**
	 * Example 2: Controlling reasoning content
	 *
	 * Use returnReasoningContents to control whether intermediate reasoning is included in the message history
	 */
	public void example2_controlReasoningContents() throws Exception {
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.returnReasoningContents(true)  //Return to reasoning process
				.outputKey("article")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.instruction("Please review and correct the article: \n{article}, and finally return the article content after review and correction.")
				.includeContents(true) //Contains the reasoning content of the previous Agent
				.returnReasoningContents(true)  //Return to reasoning process
				.outputKey("reviewed_article")
				.build();


		//The reasoning content of each sub-agent. The next executing sub-agent will see the reasoning content of the previous sub-agent.
		SequentialAgent blogAgent = SequentialAgent.builder()
				.name("blog_agent")
				.subAgents(List.of(writerAgent, reviewerAgent))
				.build();

		Optional<OverAllState> result = blogAgent.invoke("Help me write a prose of about 100 words");

		if (result.isPresent()) {
			//Message history will contain all tool calls and inference processes
			List<Message> messages = (List<Message>) result.get().value("messages").orElse(List.of());
			System.out.println("Number of messages:" + messages.size()); //Includes all intermediate steps
		}
	}

	/**
	 * Example 3: Parallel execution (Parallel Agent)
	 *
	 * Multiple agents process the same input simultaneously, and their results are collected and combined
	 */
	public void example3_parallelAgent() throws Exception {
		//Create multiple specialized agents
		ReactAgent proseWriterAgent = ReactAgent.builder()
				.name("prose_writer_agent")
				.model(chatModel)
				.description("AI assistant specializing in prose writing")
				.instruction("You are a well-known prose writer, good at writing beautiful prose." +
						"The user will give you a topic: {input}, and you only need to create a prose of about 100 words.")
				.outputKey("prose_result")
				.enableLogging(true)
				.build();

		ReactAgent poemWriterAgent = ReactAgent.builder()
				.name("poem_writer_agent")
				.model(chatModel)
				.description("AI assistant specializing in writing modern poetry")
				.instruction("You are a well-known modern poet who is good at writing modern poetry." +
						"The topic that the user will give you is: {input}, and you only need to create a modern poem.")
				.outputKey("poem_result")
				.enableLogging(true)
				.build();

		ReactAgent summaryAgent = ReactAgent.builder()
				.name("summary_agent")
				.model(chatModel)
				.description("AI assistant specializing in content summarization")
				.instruction("You are a professional content analyst who is good at summarizing and refining topics." +
						"The user will give you a topic: {input}, and you only need to briefly summarize the topic.")
				.outputKey("summary_result")
				.enableLogging(true)
				.build();

		//Create parallel agent
		ParallelAgent parallelAgent = ParallelAgent.builder()
				.name("parallel_creative_agent")
				.description("Perform multiple creative tasks in parallel, including writing prose, poetry, and summarizing")
				.mergeOutputKey("merged_results")
				.subAgents(List.of(proseWriterAgent, poemWriterAgent, summaryAgent))
				.mergeStrategy(new ParallelAgent.DefaultMergeStrategy())
				.build();

		ExecutorService executorService = Executors.newFixedThreadPool(3);
		//use
		Flux<NodeOutput> flux = parallelAgent.stream("With the theme of 'West Lake'", RunnableConfig.builder().addParallelNodeExecutor("parallel_creative_agent", executorService).build());

		AtomicReference<NodeOutput> lastOutput = new AtomicReference<>();
		flux.doOnNext(nodeOutput -> {
			System.out.println("Node output:" + nodeOutput);
			lastOutput.set(nodeOutput);
		}).doOnError(error -> {
			System.err.println("Execution error:" + error.getMessage());
		}).doOnComplete(() -> {
			System.out.println("Parallel Agent streaming execution completed\n\n");

			NodeOutput output = lastOutput.get();
			if (output == null) {
				System.out.println("No output was received and results cannot be displayed.");
				return;
			}

			OverAllState state = output.state();
			//Access the output of each Agent
			state.value("prose_result").ifPresent(r ->
					System.out.println("prose:" + r));
			state.value("poem_result").ifPresent(r ->
					System.out.println("Poetry:" + r));
			state.value("summary_result").ifPresent(r ->
					System.out.println("Summarize:" + r));

			//Access merged results
			state.value("merged_results").ifPresent(r ->
					System.out.println("Combined results:" + r));
		}).blockLast();

	}

	/**
	 * Example 4: Custom merge strategy
	 *
	 * Implement custom merging strategies to control how the output of multiple agents is combined
	 */
	public void example4_customMergeStrategy() throws Exception {
		//Custom merge strategy
		class CustomMergeStrategy implements ParallelAgent.MergeStrategy {
			@Override
			public Map<String, Object> merge(Map<String, Object> mergedState, OverAllState state) {
				//Extract output from each agent's state
				state.data().forEach((key, value) -> {
					//Check that the key is not null and ends with "_result"
					if (key != null && key.endsWith("_result")) {
						String resultText = "";
						if (value instanceof GraphResponse graphResponse) {
                            if (graphResponse.resultValue().isPresent()) {
                                resultText = graphResponse.resultValue().get().toString();
                            }
						} else if (value != null) {
							resultText = value.toString();
						}
						Object existing = mergedState.get("all_results");
						if (existing == null) {
							mergedState.put("all_results", resultText);
						}
						else {
							mergedState.put("all_results", existing + "\n\n---\n\n" + resultText);
						}
					}
				});
				return mergedState;
			}
		}

		//CreateAgent
		ReactAgent agent1 = ReactAgent.builder()
				.name("agent1")
				.model(chatModel)
				.outputKey("agent1_result")
				.build();

		ReactAgent agent2 = ReactAgent.builder()
				.name("agent2")
				.model(chatModel)
				.outputKey("agent2_result")
				.build();

		ReactAgent agent3 = ReactAgent.builder()
				.name("agent3")
				.model(chatModel)
				.outputKey("agent3_result")
				.build();

		//Use a custom merge strategy
		ParallelAgent parallelAgent = ParallelAgent.builder()
				.name("parallel_agent")
				.subAgents(List.of(agent1, agent2, agent3))
				.mergeStrategy(new CustomMergeStrategy())
				.mergeOutputKey("all_results")
				.build();

		Optional<OverAllState> result = parallelAgent.invoke("Analyze this topic");

		if (result.isPresent()) {
			OverAllState state = result.get();
			state.value("all_results").ifPresent(mergeResult -> {
				System.out.println("Combined results:" + mergeResult);
			});
			System.out.println("Custom merge strategy example executed successfully");
		}
	}

	/**
	 * Example 5: LLM routing (LlmRoutingAgent)
	 *
	 * Use a large language model to dynamically decide which sub-agent to route a request to
	 */
	public void example5_llmRoutingAgent() throws Exception {
		//Create specialized sub-Agents
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing all kinds of articles, including prose, poetry and other literary works")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.outputKey("writer_output")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Good at commenting, revising and polishing articles")
				.instruction("You are a well-known critic who is good at commenting and revising articles." +
						"For prose articles, please ensure that the article must include a description of the scenery of West Lake.")
				.outputKey("reviewer_output")
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translating articles into various languages")
				.instruction("You are a professional translator who can accurately translate articles into the target language.")
				.outputKey("translator_output")
				.build();

		//Create routing agent
		LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
				.name("content_routing_agent")
				.description("Intelligent routing to the appropriate expert agent based on user needs")
				.model(chatModel)
				.subAgents(List.of(writerAgent, reviewerAgent, translatorAgent))
				.build();

		//Use - LLM will automatically select the most appropriate Agent
		System.out.println("Route Test 1: Writing Request");
		Optional<OverAllState> result1 = routingAgent.invoke("Help me write an essay about spring");
		//LLM will route to writerAgent

		System.out.println("Routing test 2: Modify request");
		Optional<OverAllState> result2 = routingAgent.invoke("Please help me revise this article: Spring is here and the flowers are blooming.");
		//LLM will route to reviewerAgent

		System.out.println("Route Test 3: Translation Request");
		Optional<OverAllState> result3 = routingAgent.invoke("Please translate the following content into English: Spring is warm and flowers are blooming");
		//LLM will route to translatorAgent

		System.out.println("LLM routing example execution completed");
	}

	/**
	 * Example 5.5: Using a custom TemplateRenderer to work with multiple agents
	 *
	 * Shows how to use StringTemplateRenderer.builder() to customize placeholder delimiters in a multi-agent scenario.
	 * Use [[variable]] instead of the default {variable} as placeholder format.
	 */
	public void example5_5_customTemplateRenderer() throws Exception {
		//Use StringTemplateRenderer.builder() to create a TemplateRenderer with custom delimiters
		//Use [[ and ]] as placeholder separators
		TemplateRenderer customRenderer = SaaStTemplateRenderer.builder()
				.startDelimiter("[[")
				.endDelimiter("]]")
				.build();

		//Create specialized sub-Agents - note the use of [[variable]] format in instructions
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Good at writing all kinds of articles, including prose, poetry and other literary works")
				.instruction("""
						You are a well-known writer who is good at writing and creating.
						Current topic: [[topic]]
						Style requirement: [[style]]
						Word count requirement: [[word_count]]
						Please answer based on the user's question.
						""")
				.templateRenderer(customRenderer)
				.outputKey("writer_output")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Good at commenting, revising and polishing articles")
				.instruction("""
						You are a well-known critic who is good at commenting and revising articles.
						Review criteria: [[review_criteria]]
						Focus points: [[focus_points]]
						For prose articles, please ensure that the article must include a description of the scenery.
						""")
				.templateRenderer(customRenderer)
				.outputKey("reviewer_output")
				.build();

		ReactAgent translatorAgent = ReactAgent.builder()
				.name("translator_agent")
				.model(chatModel)
				.description("Good at translating articles into various languages")
				.instruction("""
						You are a professional translator who can accurately translate articles into the target language.
						Target language: [[target_language]]
						Translation style: [[translation_style]]
						""")
				.templateRenderer(customRenderer)
				.outputKey("translator_output")
				.build();

		//Create routing agent
		LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
				.name("content_routing_agent")
				.description("Intelligent routing to the appropriate expert agent based on user needs")
				.model(chatModel)
				.subAgents(List.of(writerAgent, reviewerAgent, translatorAgent))
				.build();

		//Use - Pass in input with custom placeholder variables
		System.out.println("Custom Template Routing Test 1: Writing Request");
		Map<String, Object> writerInput = Map.of(
				"input", "Help me write an essay about spring",
				"topic", "spring",
				"style", "prose",
				"word_count", "About 200 words"
		);
		Optional<OverAllState> result1 = routingAgent.invoke(writerInput);
		if (result1.isPresent()) {
			result1.get().value("writer_output").ifPresent(output ->
					System.out.println("Writing output:" + output));
		}

		System.out.println("\nCustom Template Routing Test 2: Review Request");
		Map<String, Object> reviewerInput = Map.of(
				"input", "Please help me revise this article: Spring is here and the flowers are blooming.",
				"review_criteria", "Fluent language and vivid descriptions",
				"focus_points", "Rhetorical techniques and artistic conception creation"
		);
		Optional<OverAllState> result2 = routingAgent.invoke(reviewerInput);
		if (result2.isPresent()) {
			result2.get().value("reviewer_output").ifPresent(output ->
					System.out.println("Review output:" + output));
		}

		System.out.println("\nCustom template routing test 3: Translation request");
		Map<String, Object> translatorInput = Map.of(
				"input", "Please translate the following content into English: Spring is warm and flowers are blooming",
				"target_language", "English",
				"translation_style", "literary translation"
		);
		Optional<OverAllState> result3 = routingAgent.invoke(translatorInput);
		if (result3.isPresent()) {
			result3.get().value("translator_output").ifPresent(output ->
					System.out.println("Translation output:" + output));
		}

		System.out.println("\nCustomized TemplateRenderer multi-agent example execution completed");
	}

	/**
	 * Example 6: Optimizing routing accuracy
	 *
	 * Improve routing accuracy by providing clear and unambiguous agent descriptions
	 */
	public void example6_optimizedRouting() throws Exception {
		//1. Provide a clear and unambiguous Agent description
		ReactAgent codeAgent = ReactAgent.builder()
				.name("code_agent")
				.model(chatModel)
				.description("Specializes in programming-related issues, including code writing, debugging, refactoring, and optimization." +
						"Good at mainstream programming languages ​​such as Java, Python, and JavaScript.")
				.instruction("You are a senior software engineer...")
				.build();

		//2. Clarify the boundaries of the Agent’s responsibilities
		ReactAgent businessAgent = ReactAgent.builder()
				.name("business_agent")
				.model(chatModel)
				.description("Specializes in business analysis, market research and strategic planning issues." +
						"Does not deal with technical implementation details.")
				.instruction("You are a senior business analyst...")
				.build();

		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("Specializes in content creation, including writing tasks such as articles, reports, copywriting, etc.")
				.instruction("You are a professional writer...")
				.build();

		//3. Use Agents from different fields to avoid overlap
		LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
				.name("multi_domain_router")
				.model(chatModel)
				.subAgents(List.of(codeAgent, businessAgent, writerAgent))
				.build();

		//test route
		routingAgent.invoke("How to implement singleton pattern in Java?");
		routingAgent.invoke("Analyze the competitive situation of this market");
		routingAgent.invoke("Write a product introduction copy");

		System.out.println("Optimized routing example execution completed");
	}

	/**
	 * Example 7: Mixed mode - combining sequential, parallel and routing
	 *
	 * Combine different patterns to create complex workflows
	 */
	public void example7_hybridPattern() throws Exception {
		//Create research agent (parallel execution)
		ReactAgent webResearchAgent = ReactAgent.builder()
				.name("web_research")
				.model(chatModel)
				.description("Search information from the Internet")
				.instruction("Please search and collect information on the following topics: {input}")
				.outputKey("web_data")
				.build();

		ReactAgent dbResearchAgent = ReactAgent.builder()
				.name("db_research")
				.model(chatModel)
				.description("Query information from database")
				.instruction("Please query and collect information from the database on the following topics: {input}")
				.outputKey("db_data")
				.build();

		ParallelAgent researchAgent = ParallelAgent.builder()
				.name("parallel_research")
				.description("Collect information from multiple data sources in parallel")
				.subAgents(List.of(webResearchAgent, dbResearchAgent))
				.mergeOutputKey("research_data")
				.build();

		//Create analysis agent
		ReactAgent analysisAgent = ReactAgent.builder()
				.name("analysis_agent")
				.model(chatModel)
				.description("Analyze research data")
				.instruction("Please analyze and provide insights from the data collected below: {research_data}")
				.outputKey("analysis_result")
				.build();

		//Create reporting agent (routing format)
		ReactAgent pdfReportAgent = ReactAgent.builder()
				.name("pdf_report")
				.model(chatModel)
				.description("Generate reports in PDF format")
				.instruction("""
						Please generate a PDF format report based on the research results and analysis results.
						
						Research results: {research_data}
						Analysis results: {analysis_result}
						""")
				.outputKey("pdf_report")
				.build();

		ReactAgent htmlReportAgent = ReactAgent.builder()
				.name("html_report")
				.model(chatModel)
				.description("Generate reports in HTML format")
				.instruction("""
						Please generate an HTML format report based on the research results and analysis results.
						
						Research results: {research_data}
						Analysis results: {analysis_result}
						""")
				.outputKey("html_report")
				.build();

		LlmRoutingAgent reportAgent = LlmRoutingAgent.builder()
				.name("report_router")
				.description("Choose the report format based on your needs")
				.model(chatModel)
				.subAgents(List.of(pdfReportAgent, htmlReportAgent))
				.build();

		//Combined into sequential workflows
		SequentialAgent hybridWorkflow = SequentialAgent.builder()
				.name("research_workflow")
				.description("Complete research workflow: Parallel collection -> Analysis -> Route generation report")
				.subAgents(List.of(researchAgent, analysisAgent, reportAgent))
				.build();


		//Print workflow diagram
		System.out.println("\n=== Mixed Mode Workflow Diagram ===");
		printGraphRepresentation(hybridWorkflow);
		System.out.println("=========================\n");

		Optional<OverAllState> result = hybridWorkflow.invoke("Research AI technology trends and generate HTML reports");

		if (result.isPresent()) {
			System.out.println("Mixed mode example executed successfully");
		}
	}

	/**
	 * Example 8: Supervisor mode (SupervisorAgent)
	 *
	 * SupervisorAgent is similar to LlmRoutingAgent, with the following key differences:
	 * 1. After the sub-Agent processing is completed, it will return to the Supervisor instead of ending directly.
	 * 2. Supervisor can decide to continue routing to other sub-agents, or mark the task as completed (FINISH)
	 * 3. Support nested Agents (such as SequentialAgent, ParallelAgent) as sub-Agents
	 *
	 * This example shows how to use SupervisorAgent to manage a complex workflow containing a plain ReactAgent and nested SequentialAgent
	 */
	public void example8_supervisorAgent() throws Exception {
		//Define professional supervisor instructions (if not defined, the system default prompt word will be used)
		final String SUPERVISOR_INSTRUCTION = """
				You are an intelligent content management supervisor, responsible for coordinating and managing multiple specialized Agents to fulfill user content processing needs.

				## Your Responsibilities
				1. Analyze user requirements and decompose them into appropriate subtasks
				2. Select the appropriate Agent based on task characteristics
				3. Monitor task execution status, decide whether to continue processing or complete the task
				4. When all tasks are completed, return FINISH to end the process

				## Available Sub-Agents and Their Responsibilities

				### writer_agent
				- **Capabilities**: Excels at writing various articles, including prose, poetry, and other literary works
				- **Use Cases**:
				  * User needs to create new articles, prose, poetry, or other original content
				  * Simple writing tasks that don't require subsequent review or revision
				- **Output**: writer_output

				### translator_agent
				- **Capabilities**: Excels at translating articles into various languages
				- **Use Cases**:
				  * User needs to translate content into other languages
				  * Translation tasks are typically single operations that don't require multi-step processing
				- **Output**: translator_output

				### writing_workflow_agent
				- **Capabilities**: Complete writing workflow with two steps: first write the article, then review and revise
				- **Use Cases**:
				  * User needs high-quality articles that require review and revision
				  * Task explicitly requires "ensure quality", "Need review", "Need to modify", etc.
				  * Complex writing tasks requiring multi-step processing
				- **Workflow**:
				  1. article_writer: Creates articles based on user requirements
				  2. reviewer: Reviews and revises articles to ensure quality
				- **Output**: reviewed_article

				## Decision Rules

				1. **Single Task Judgment**:
				   - If the user only needs translation, choose translator_agent
				   - If the user only needs simple writing, choose writer_agent
				   - If the user needs high-quality articles or explicitly requires review, choose writing_workflow_agent

				2. **Multi-Step Task Processing**:
				   - If user requirements contain multiple steps (e.g., "Write the article first, then translate it"), process step by step
				   - First route to the appropriate Agent, wait for completion
				   - After completion, continue routing to the next Agent based on remaining requirements
				   - Until all steps are completed, return FINISH

				3. **Task Completion Judgment**:
				   - When all user requirements have been fulfilled, return FINISH
				   - If there are unfinished tasks, continue routing to the appropriate Agent

				## Response Format
				Only return the Agent name (writer_agent, translator_agent, writing_workflow_agent) or FINISH, do not include other explanations.
				""";
		//1. Create a common ReactAgent sub-Agent
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
				.instruction("You are a professional translator who can accurately translate articles into the target language." +
						"If the content to be translated already exists in the state, use: \n\n {writer_output}.")
				.outputKey("translator_output")
				.build();

		//2. Create a nested SequentialAgent as a sub-Agent
		//This SequentialAgent contains multiple steps: first write the article, and then review it
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

		//Create nested SequentialAgent
		SequentialAgent writingWorkflowAgent = SequentialAgent.builder()
				.name("writing_workflow_agent")
				.description("Complete writing workflow: write the article first, then review and revise it")
				.subAgents(List.of(articleWriterAgent, reviewerAgent))
				.build();

		//3. Create SupervisorAgent, including ordinary Agent and nested Agent
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("Content management supervisor, responsible for coordinating tasks such as writing, translation and complete writing workflow")
				.model(chatModel)
				.systemPrompt(SUPERVISOR_INSTRUCTION)
				.subAgents(List.of(writerAgent, translatorAgent, writingWorkflowAgent))
				.build();

		//Usage example
		System.out.println("Supervisor Test 1: Simple Writing Task");
		Optional<OverAllState> result1 = supervisorAgent.invoke("Help me write a short essay about spring");
		//Supervisor will route to writer_agent and return to Supervisor after processing is completed. Supervisor will return FINISH after judging that it is completed.
		if (result1.isPresent()) {
			result1.get().value("writer_output").ifPresent(output ->
					System.out.println("Writing result:" + output));
		}

		System.out.println("\nSupervisor Test 2: Tasks requiring complete workflow");
		Optional<OverAllState> result2 = supervisorAgent.invoke("Help me write an essay about West Lake and ensure the quality");
		// Supervisor will be routed to writing_workflow_agent (nested SequentialAgent),
		//The Agent will first write the article, then review it, and return to the Supervisor after completion. The Supervisor will return FINISH after judging the completion.
		if (result2.isPresent()) {
			result2.get().value("reviewed_article").ifPresent(output ->
					System.out.println("Articles after review:" + output));
		}

		System.out.println("\nSupervisor Test 3: Translation Task");
		Optional<OverAllState> result3 = supervisorAgent.invoke("Please translate the following content into English: Spring is warm and flowers are blooming");
		//Supervisor will route to translator_agent and return to Supervisor after processing is completed. Supervisor will return FINISH after judging that it is completed.
		if (result3.isPresent()) {
			result3.get().value("translator_output").ifPresent(output ->
					System.out.println("Translation results:" + output));
		}

		System.out.println("\nSupervisor Test 4: Multi-step task (may require multiple routes)");
		Optional<OverAllState> result4 = supervisorAgent.invoke("First help me write an article about spring and then translate it into English");
		//Supervisor may:
		//1. First route to writer_agent to write the article, and then return to Supervisor after completion
		//2. Supervisor determines that translation is still needed and routes to translator_agent.
		//3. After the translation is completed, return to Supervisor. Supervisor determines that all tasks are completed and returns FINISH
		if (result4.isPresent()) {
			result4.get().value("writer_output").ifPresent(output ->
					System.out.println("Writing result:" + output));
			result4.get().value("translator_output").ifPresent(output ->
					System.out.println("Translation results:" + output));
		}

		//Print workflow diagram
		System.out.println("\n=== SupervisorAgent Workflow Diagram ===");
		printGraphRepresentation(supervisorAgent);
		System.out.println("==================================\n");

		//Example 5: SupervisorAgent as a sub-Agent of SequentialAgent, using placeholders
		System.out.println("\nSupervisor Test 5: SupervisorAgent as a sub-Agent of SequentialAgent (use placeholder)");
		example8_supervisorAgentAsSequentialSubAgent();
		System.out.println();

		System.out.println("SupervisorAgent example execution completed");
	}

	/**
	 * Example 8.1: SupervisorAgent as a sub-Agent of SequentialAgent, using placeholders
	 *
	 * This example shows:
	 * 1. SupervisorAgentcan be used asSequentialAgentsonAgent
	 * 2. SupervisorAgent instructions can use placeholders to reference the output of the pre-order Agent.
	 * 3. SupervisorAgent’s sub-Agent instructions can also use placeholders to reference the output of the pre-order Agent.
	 */
	private void example8_supervisorAgentAsSequentialSubAgent() throws Exception {
		//1. Create the first Agent to generate article content
		ReactAgent articleWriterAgent = ReactAgent.builder()
				.name("article_writer")
				.model(chatModel)
				.description("Professional writing agent, responsible for creating articles")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's question: {input}.")
				.outputKey("article_content")
				.build();

		// 2. createSupervisorAgentsonAgent
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

		//3. Define the instruction of SupervisorAgent and use placeholders to reference the output of the pre-order Agent.
		//This instruction contains the {article_content} placeholder, which will be replaced with the output of the first Agent
		final String SUPERVISOR_INSTRUCTION = """
				You are an intelligent content processing supervisor. You can see the chat history and task processing records of the preceding Agent. Currently, you received the following article content:

				{article_content}

				Please decide whether to translate or review based on the characteristics of the article content and user needs:
				- If the user requests translation or the article needs to be translated into another language, select translator_agent
				- If the user requests review, improvement, or optimization of the article, select reviewer_agent
				- If the task is completed, return FINISH
				""";

		final String SUPERVISOR_SYSTEM_PROMPT = """
				You are an intelligent content processing supervisor, responsible for coordinating translation and review tasks.

				## Available Sub-Agents and Their Responsibilities

				### translator_agent
				- **Function**: Good at translating articles into various languages
				- **Applicable Scenarios**: When the article needs to be translated into other languages
				- **Output**: translator_output

				### reviewer_agent
				- **Function**: Good at reviewing and revising articles
				- **Applicable Scenarios**: When the article needs review, improvement, or optimization
				- **Output**: reviewer_output

				## Decision Rules

				1. **Based on article content and user needs**:
				   - If the user requests translation or the article needs to be translated into another language, select translator_agent
				   - If the user requests review, improvement, or optimization of the article, select reviewer_agent

				2. **Task Completion Judgment**:
				   - Return FINISH when all tasks are completed

				## Response Format
				Only return Agent name (translator_agent, reviewer_agent) or FINISH, do not include other explanations.
				""";

		//4. Create SupervisorAgent and use placeholders for its instructions.
		SupervisorAgent supervisorAgent = SupervisorAgent.builder()
				.name("content_supervisor")
				.description("The content processing supervisor determines translation or review based on the output of the pre-order Agent.")
				.model(chatModel)
				.systemPrompt(SUPERVISOR_SYSTEM_PROMPT)
				.instruction(SUPERVISOR_INSTRUCTION) //This instruction contains {article_content} placeholder
				.subAgents(List.of(translatorAgent, reviewerAgent))
				.build();

		// 5. createSequentialAgent, execute firstarticleWriterAgent, and then executesupervisorAgent
		SequentialAgent sequentialAgent = SequentialAgent.builder()
				.name("content_processing_workflow")
				.description("Content processing workflow: write the article first, then decide on translation or review based on the content of the article")
				.subAgents(List.of(articleWriterAgent, supervisorAgent))
				.build();

		//Test scenario 1: Write an article and then translate it
		System.out.println("Scenario 1: Write the article and then translate it");
		Optional<OverAllState> result1 = sequentialAgent.invoke("Help me write a short article about spring and translate it into English");
		if (result1.isPresent()) {
			OverAllState state = result1.get();
			state.value("article_content").ifPresent(output -> {
				if (output instanceof AssistantMessage) {
					System.out.println("Article content:" + ((AssistantMessage) output).getText());
				}
			});
			state.value("translator_output").ifPresent(output -> {
				if (output instanceof AssistantMessage) {
					System.out.println("Translation results:" + ((AssistantMessage) output).getText());
				}
			});
		}

		//Test scenario 2: Review after writing the article
		System.out.println("\nScenario 2: Review after writing the article");
		Optional<OverAllState> result2 = sequentialAgent.invoke("Help me write a short article about spring, and then review and improve it");
		if (result2.isPresent()) {
			OverAllState state = result2.get();
			state.value("article_content").ifPresent(output -> {
				if (output instanceof AssistantMessage) {
					System.out.println("Article content:" + ((AssistantMessage) output).getText());
				}
			});
			state.value("reviewer_output").ifPresent(output -> {
				if (output instanceof AssistantMessage) {
					System.out.println("Review results:" + ((AssistantMessage) output).getText());
				}
			});
		}
	}

	/**
	 * Print workflow diagram (supports SupervisorAgent)
	 */
	private void printGraphRepresentation(SupervisorAgent agent) {
		GraphRepresentation representation = agent.getAndCompileGraph().getGraph(GraphRepresentation.Type.PLANTUML);
		System.out.println(representation.content());
	}

	private void testRoutingSequentialEmbedding() throws GraphRunnerException {
		ReactAgent reactAgent = ReactAgent.builder()
				.name("weather_agent")
				.description("Query weather based on user questions and refined location information.\n\n User question: {input} \n\n Location information: {location}")
				.model(chatModel)
				.outputKey("weather")
				.systemPrompt("You are a weather query expert").build();

		ReactAgent locationAgent = ReactAgent.builder()
				.name("location_agent")
				.description("Based on the user's question, perform location query.\n User question: {input}")
				.model(chatModel)
				.outputKey("location")
				.systemPrompt("You are a location lookup expert").build();

		SequentialAgent sequentialAgent = SequentialAgent.builder()
				.name("Weather assistant")
				.description("Weather assistant")
				.subAgents(List.of(locationAgent, reactAgent))
				.build();

		LlmRoutingAgent routingAgent = LlmRoutingAgent.builder()
				.name("User assistant")
				.description("Help users complete various needs")
//.routingInstruction(""); // Can provide detailed instructions to inform routing responsibilities, how to select sub-Agents, etc., used to replace the system's default prompt.
				.model(chatModel)
				.subAgents(List.of(sequentialAgent)).build();

		Optional<OverAllState> invoke = routingAgent.invoke("how is the weather");
		System.out.println(invoke);
	}

	/**
	 * Print workflow diagram
	 *
	 * Use PlantUML format to display the structure of Agent workflow
	 */
	private void printGraphRepresentation(SequentialAgent agent) {
		GraphRepresentation representation = agent.getAndCompileGraph().getGraph(GraphRepresentation.Type.PLANTUML);
		System.out.println(representation.content());
	}

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("===Multi-agent example ===\n");

		try {
			System.out.println("Example 1: Sequential execution (Sequential Agent)");
			example1_sequentialAgent();
			System.out.println();

			System.out.println("Example 2: Controlling reasoning content");
			example2_controlReasoningContents();
			System.out.println();

			System.out.println("Example 3: Parallel execution (Parallel Agent)");
			example3_parallelAgent();
			System.out.println();

			System.out.println("Example 4: Custom merge strategy");
			example4_customMergeStrategy();
			System.out.println();
//
			System.out.println("Example 5: LLM routing (LlmRoutingAgent)");
			example5_llmRoutingAgent();
			System.out.println();

			System.out.println("Example 6: Optimizing routing accuracy");
			example6_optimizedRouting();
			System.out.println();

			System.out.println("Example 7: Mixed modes");
			example7_hybridPattern();
			System.out.println();

			System.out.println("Example 8: Supervisor mode (SupervisorAgent)");
			example8_supervisorAgent();
			System.out.println();

			testRoutingSequentialEmbedding();

		}
		catch (Exception e) {
			System.err.println("An error occurred while executing the example:" + e.getMessage());
			e.printStackTrace();
		}
	}
}

