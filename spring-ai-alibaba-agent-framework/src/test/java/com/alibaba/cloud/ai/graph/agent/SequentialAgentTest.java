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
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.agent.flow.agent.SequentialAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import static com.alibaba.cloud.ai.graph.agent.tools.PoetTool.createPoetToolCallback;
import static com.alibaba.cloud.ai.graph.agent.tools.ReviewerTool.createReviewerToolCallback;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class SequentialAgentTest {

    private static final Logger log = LoggerFactory.getLogger(SequentialAgentTest.class);
    private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testSequentialAgent() throws Exception {
		ReactAgent writerAgent = ReactAgent.builder()
			.name("writer_agent")
			.model(chatModel)
			.description("You can write articles.")
			.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
			.outputKey("article")
			.enableLogging(true)
			.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
			.name("reviewer_agent")
			.model(chatModel)
			.description("Articles can be commented and modified.")
			.instruction("You are a well-known critic who is good at commenting and revising articles.For prose articles, please ensure that the article must include a description of the scenery of West Lake.Finally, only the revised article will be returned without any comment information.")
			.outputKey("reviewed_article")
			.build();

		SequentialAgent blogAgent = SequentialAgent.builder()
			.name("blog_agent")
			.description("You can write an article based on a topic given by the user and then submit the article to reviewers for comment.")
			.subAgents(List.of(writerAgent, reviewerAgent))
			.build();

		try {
			Optional<OverAllState> result = blogAgent.invoke("Help me write a prose of about 100 words");

			assertTrue(result.isPresent(), "Result should be present");

			OverAllState state = result.get();

			assertTrue(state.value("article").isPresent(), "Article should be present after writer agent");
			assertEquals(5, ((List<?>)state.value("messages").get()).size());
			AssistantMessage article = (AssistantMessage) state.value("article").get();
			assertNotNull(article.getText(), "Article content should not be null");

			assertTrue(state.value("reviewed_article").isPresent(),
					"Reviewed article should be present after reviewer agent");
			AssistantMessage reviewedArticle = (AssistantMessage) state.value("reviewed_article").get();
			assertNotNull(reviewedArticle.getText(), "Reviewed article content should not be null");

			System.out.println(result.get());
		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("SequentialAgent execution failed: " + e.getMessage());
		}
	}


	@Test
	public void testSequentialWithSubAgentReasoningContents() throws Exception {
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("You can write articles.")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.returnReasoningContents(true)
				.tools(List.of(createPoetToolCallback()))
				.outputKey("article")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Articles can be commented and modified.")
				.instruction("You are a well-known critic who is good at commenting and revising articles.For prose articles, please ensure that the article must include a description of the scenery of West Lake.Finally, only the revised article will be returned without any comment information.")
				.returnReasoningContents(true)
				.tools(List.of(createReviewerToolCallback()))
				.outputKey("reviewed_article")
				.build();

		SequentialAgent blogAgent = SequentialAgent.builder()
				.name("blog_agent")
				.description("You can write an article based on a topic given by the user and then submit the article to reviewers for comment.")
				.subAgents(List.of(writerAgent, reviewerAgent))
				.build();

		try {
			Optional<OverAllState> result = blogAgent.invoke("Help me write a prose of about 100 words");

			assertTrue(result.isPresent(), "Result should be present");

			OverAllState state = result.get();

			assertTrue(state.value("article").isPresent(), "Article should be present after writer agent");
			assertTrue(state.value("reviewed_article").isPresent(), "Reviewed article should be present after reviewer agent");
			assertEquals(9, ((List<?>)state.value("messages").get()).size());
		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("SequentialAgent execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testSequentialWithoutSubAgentReasoningContents() throws Exception {
		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("You can write articles.")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.returnReasoningContents(false) // by default false
				.tools(List.of(createPoetToolCallback()))
				.outputKey("article")
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Articles can be commented and modified.")
				.instruction("You are a well-known critic who is good at commenting and revising articles.For prose articles, please ensure that the article must include a description of the scenery of West Lake.Finally, only the revised article will be returned without any comment information.")
				.returnReasoningContents(false)  // by default false
				.tools(List.of(createReviewerToolCallback()))
				.outputKey("reviewed_article")
				.build();

		SequentialAgent blogAgent = SequentialAgent.builder()
				.name("blog_agent")
				.description("You can write an article based on a topic given by the user and then submit the article to reviewers for comment.")
				.subAgents(List.of(writerAgent, reviewerAgent))
				.build();

		try {
			Optional<OverAllState> result = blogAgent.invoke("Help me write a prose of about 100 words");
			assertTrue(result.isPresent(), "Result should be present");
			OverAllState state = result.get();
			assertTrue(state.value("article").isPresent(), "Article should be present after writer agent");
			assertTrue(state.value("reviewed_article").isPresent(), "Reviewed article should be present after reviewer agent");
			assertEquals(5, ((List<?>)state.value("messages").get()).size());
		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("SequentialAgent execution failed: " + e.getMessage());
		}
	}

	@Test
	public void testEmbeddedSequentialAgent() throws Exception {

		ReactAgent writerAgent = ReactAgent.builder()
				.name("writer_agent")
				.model(chatModel)
				.description("You can write articles.")
				.instruction("You are a well-known writer who is good at writing and creating.Please answer based on the user's questions.")
				.outputKey("article")
				.enableLogging(true)
				.build();

		ReactAgent reviewerAgent = ReactAgent.builder()
				.name("reviewer_agent")
				.model(chatModel)
				.description("Articles can be commented and modified.")
				.instruction("You are a well-known critic who is good at commenting and revising articles.For prose articles, please ensure that the article must include a description of the scenery of West Lake.Finally, output the modified article without including any comments.")
				.outputKey("reviewed_article")
				.enableLogging(true)
				.build();

		SequentialAgent child_1 = SequentialAgent.builder()
				.name("child_1")
				.description("You can write an article based on a topic given by the user, and then submit the article to reviewers to comment and make changes if necessary.")
				.subAgents(List.of(writerAgent, reviewerAgent))
				.build();


		ReactAgent signature_agent = ReactAgent.builder()
				.name("signature_agent")
				.model(chatModel)
				.description("Add a fixed byline to the article.")
				.includeContents(true)
				.instruction("You are responsible for signing the generated article. Please append your signature to the end of the article.Signature: Spring AI Alibaba.")
				.outputKey("signed_article")
				.enableLogging(true)
				.build();


		SequentialAgent blogAgentParent = SequentialAgent.builder()
				.name("blogAgentParent")
				.description("You can write an article based on a topic given by the user, and then submit the article to reviewers to comment and make changes if necessary.")
				.subAgents(List.of(child_1, signature_agent, getChild3()))
				.build();

		try {
			List<NodeOutput> result = new ArrayList<>();
			 blogAgentParent.stream( "Help me write a prose of about 100 words").doOnNext(output -> {
				 System.out.println(output);
				 result.add(output);
			}).blockLast();
			assertNotNull(result);
			assertFalse(result.isEmpty());
			var last = result.get(result.size() - 1);
			var finalState = last.state();
			assertTrue(finalState.value("article").isPresent());
			assertTrue(finalState.value("reviewed_article").isPresent());
			assertTrue(finalState.value("signed_article").isPresent());
			assertTrue(finalState.value("revised_article").isPresent());
			assertTrue(finalState.value("censored_article").isPresent());

		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("SequentialAgent execution failed: " + e.getMessage());
		}

		// Verify all hooks were executed
	}

	private ParallelAgent createParallelAgent(String name) throws GraphStateException {
		// Create specialized sub-agents with unique output keys and specific instructions
		ReactAgent proseWriterAgent = ReactAgent.builder()
				.name("prose_writer_agent")
				.model(chatModel)
				.description("AI assistant specializing in prose writing")
				.instruction("You are a well-known prose writer, good at writing beautiful prose.The user will give you a topic, and you only need to create a prose of about 100 words, no poetry or summary.Please focus on prose writing to ensure beautiful content and profound artistic conception.")
				.outputKey("prose_result")
				.build();

		ReactAgent poemWriterAgent = ReactAgent.builder()
				.name("poem_writer_agent")
				.model(chatModel)
				.description("AI assistant specializing in writing modern poetry")
				.instruction("You are a well-known modern poet who is good at writing modern poetry.The user will give you a topic and you just need to create a modern poem, no prose or summary.Please focus on poetry creation, making sure the language is refined and the imagery is rich.")
				.outputKey("poem_result")
				.build();

		ReactAgent summaryAgent = ReactAgent.builder()
				.name("summary_agent")
				.model(chatModel)
				.description("AI assistant specializing in content summarization")
				.instruction("You are a professional content analyst who is good at summarizing and refining topics.The user will give you a topic and you only need to give a brief summary of the topic, not prose or poetry.Please focus on summarizing and analyzing to ensure your views are clear and your summary is accurate.")
				.outputKey("summary_result")
				.build();

		// Create ParallelAgent that will execute all sub-agents in parallel
		ParallelAgent parallelAgent = ParallelAgent.builder()
				.name(name)
				.description("Perform multiple creative tasks in parallel, including writing prose, poetry, and summarizing")
				.subAgents(List.of(proseWriterAgent, poemWriterAgent, summaryAgent))
				.mergeStrategy(new ParallelAgent.DefaultMergeStrategy()) //✅ Add merge strategy
				.build();

		return parallelAgent;

	}

	public SequentialAgent getChild3() throws GraphStateException {

		ReactAgent reviserAgent = ReactAgent.builder()
				.name("reviser_agent")
				.model(chatModel)
				.description("Correct typos in the article.")
				.includeContents(false) //Does not include contextual content and focuses on the review of the current article
				.instruction("""
					You are a typesetting expert responsible for checking typos, grammar issues, etc. Output the revised original document without including any irrelevant information.
			
					The following is the original document:
					{reviewed_article}
				""")
				.outputKey("revised_article")
				.enableLogging(true)
				.build();

		ReactAgent censorAgent = ReactAgent.builder()
				.name("censor_agent")
				.model(chatModel)
				.description("Article content can be reviewed for compliance.")
				.includeContents(false) //Does not include contextual content and focuses on the review of the current article
				.instruction("""
					You are a compliance review officer. Review whether the article contains illegal or non-compliant content, and make improvements if necessary. Output the revised original document without including any irrelevant information.
			
					The following is the original document:
					{reviewed_article}
				""")
				.outputKey("censored_article")
				.enableLogging(true)
				.build();

		SequentialAgent child_3 = SequentialAgent.builder()
				.name("child_3")
				.description("The typesetting, compliance, etc. can be checked and revised based on the articles given by the user.")
				.subAgents(List.of(reviserAgent, censorAgent))
				.build();

		return child_3;
	}

    @Test
    public void testOutputSchema() throws Exception {
        ReactAgent sqlGenerateAgent = ReactAgent.builder()
                .name("sqlGenerateAgent")
                .model(chatModel)
                .description("MySQL SQL code can be generated based on the user's natural language.")
                .instruction("You are a little assistant who is familiar with MySQL database. Please output the corresponding SQL according to the user's natural language.")
                .outputSchema("""
                        {
                            "$schema": "https://json-schema.org/draft/2020-12/schema",
                            "type": "object",
                            "properties": {
                                "query": {
                                    "type": "string"
                                },
                                "output": {
                                    "type": "string"
                                }
                            },
                            "additionalProperties": false
                        }
                        """)
                .outputKey("sql")
				.enableLogging(true)
                .build();

        ReactAgent sqlRatingAgent = ReactAgent.builder()
                .name("sqlRatingAgent")
                .model(chatModel)
                .description("Scoring can be based on the matching degree of the input natural language and SQL statements.")
                .instruction("You are a little assistant who is familiar with MySQL database. Please output a rating based on the natural language input by the user and the corresponding SQL statement.The rating is a floating point number between 0 and 1.The closer it is to 1, the better SQL matches natural language.")
                .outputType(Double.class)
                .outputKey("score")
				.enableLogging(true)
                .build();

        //The test is placed in a SequentialAgent
        SequentialAgent agent = SequentialAgent.builder()
                .name("sql_agent")
                .description("SQL statements can be generated and scored based on user input.")
                .subAgents(List.of(sqlGenerateAgent, sqlRatingAgent))
                .build();

        Optional<OverAllState> state = agent.invoke("Now I have a user table and I want to query the first 10 users. How do I write a SQL statement?");
        assertTrue(state.isPresent());
        OverAllState overAllState = state.get();
        assertTrue(overAllState.value("messages").isPresent());
        assertTrue(overAllState.value("sql").isPresent());
        assertTrue(overAllState.value("score").isPresent());
    }

}
