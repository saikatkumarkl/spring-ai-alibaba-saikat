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
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.agent.flow.agent.ParallelAgent;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.ai.chat.model.ChatModel;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test class for ParallelAgent to verify parallel execution and result
 * merging functionality. Tests the actual execution flow with real LLM agents.
 */
@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
class ParallelAgentIntegrationTest {

	private ChatModel chatModel;

	@BeforeEach
	void setUp() {
		// Create DashScopeApi instance using the API key from environment variable
		DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(System.getenv("AI_DASHSCOPE_API_KEY")).build();

		// Create DashScope ChatModel instance
		this.chatModel = DashScopeChatModel.builder().dashScopeApi(dashScopeApi).build();
	}

	@Test
	public void testParallelAgentBasicFunctionality() throws Exception {
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
			.outputKeyStrategy(KeyStrategy.REPLACE)
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
			.name("parallel_creative_agent")
			.description("Perform multiple creative tasks in parallel, including writing prose, poetry, and summarizing")
			.mergeOutputKey("merged_results")
			.subAgents(List.of(proseWriterAgent, poemWriterAgent, summaryAgent))
			.mergeStrategy(new ParallelAgent.DefaultMergeStrategy())
			.build();

		// Execute the parallel workflow
		try {
			String userRequest = "With the theme of 'West Lake'";

			Optional<OverAllState> result = parallelAgent.invoke(userRequest);

			// Verify the results
			assertTrue(result.isPresent(), "Result should be present");
			OverAllState finalState = result.get();

			// Verify input was preserved
			assertTrue(finalState.value("input").isPresent(), "Input should be preserved");
			assertEquals(userRequest, finalState.value("input").get());

			// Verify topic was set (from TransparentNode)
			assertTrue(finalState.value("merged_results").isPresent(), "Topic should be set");

			// Verify all sub-agents produced results
			assertTrue(finalState.value("prose_result").isPresent(), "Prose result should be present");
			assertTrue(finalState.value("poem_result").isPresent(), "Poem result should be present");
			assertTrue(finalState.value("summary_result").isPresent(), "Summary result should be present");

			
			// Verify the merged results contain all individual results
			Map mergedResults = (Map) finalState.value("merged_results").get();
			assertTrue(mergedResults.containsKey("prose_result"),
					"Merged results should contain prose result");
			assertTrue(mergedResults.containsKey("poem_result"),
					"Merged results should contain poem result");
			assertTrue(mergedResults.containsKey("summary_result"),
					"Merged results should contain summary result");

			assertEquals(mergedResults.get("prose_result"),finalState.value("prose_result").get());
			assertEquals(mergedResults.get("poem_result"),finalState.value("poem_result").get());
			assertEquals(mergedResults.get("summary_result"),finalState.value("summary_result").get());

			System.out.println("Final state: " + finalState);
		}
		catch (java.util.concurrent.CompletionException e) {
			e.printStackTrace();
			fail("ParallelAgent execution failed: " + e.getMessage());
		}
	}

	// @Test
	// public void testAdkStyleWorkflow() throws Exception {
	////Create a shared KeyStrategyFactory
	// KeyStrategyFactory sharedStateFactory = () -> {
	// HashMap<String, KeyStrategy> keyStrategyHashMap = new HashMap<>();
	// keyStrategyHashMap.put("input", new ReplaceStrategy());
	// keyStrategyHashMap.put("output", new ReplaceStrategy());
	// keyStrategyHashMap.put("weather_data", new ReplaceStrategy());
	// keyStrategyHashMap.put("news_data", new ReplaceStrategy());
	// keyStrategyHashMap.put("raw_data", new ReplaceStrategy());
	// keyStrategyHashMap.put("daily_report", new ReplaceStrategy());
	// keyStrategyHashMap.put("workflow_output", new ReplaceStrategy());
	// keyStrategyHashMap.put("messages", new AppendStrategy()); // ReactAgentneedmessageskey
	// return keyStrategyHashMap;
	// };
	//
	////Create data acquisition agent - simulate API call
	// ReactAgent fetchWeatherAgent = ReactAgent.builder()
	// .name("WeatherFetcher")
	// .model(chatModel)
	//.instruction("You are a weather data acquisition assistant. Please simulate to obtain today's weather information in Hangzhou, including temperature, humidity, wind, etc.. Return simulated data directly, no real API calls are required.")
	// .outputKey("weather_data")
	// .build();
	//
	// ReactAgent fetchNewsAgent = ReactAgent.builder()
	// .name("NewsFetcher")
	// .model(chatModel)
	//.instruction("You are a news data acquisition assistant. Please simulate to obtain today's main news in Hangzhou, focusing on technology and people's livelihood. Return simulated data directly, no real API calls are required.")
	// .outputKey("news_data")
	// .build();
	//
	////Create parallel data collection Agent - implement Fan-Out mode
	// ParallelAgent dataCollector = ParallelAgent.builder()
	// .name("DataCollector")
	//.description("Parallel collection of weather and news data")
	// .inputKeys("input") // Change toinput, avoid comparing withReactAgentofmessagesconflict
	// .outputKey("raw_data")
	// .state(sharedStateFactory)
	// .subAgents(List.of(fetchWeatherAgent, fetchNewsAgent))
	// .build();
	//
	////Create result synthesis Agent - implement Gather mode
	// ReactAgent synthesizer =
	// ReactAgent.builder().name("DailyReportSynthesizer").model(chatModel).instruction("""
	//You are a daily generator.Please generate a Hangzhou Today comprehensive report based on the following information:
	//
	//Weather information: {weather_data}
	//News: {news_data}
	//
	//Please generate a report containing:
	//1. Today’s weather overview
	//2. Summary of important news
	//3. Analysis of the impact of weather on life
	//4. Tips for today’s city life
	//
	//Requirements: The content must be true and specific, and be analyzed and summarized based on the data provided.
	// """).outputKey("daily_report").build();
	//
	//// Create a complete workflow - combining parallel and sequential execution
	// SequentialAgent dailyWorkflow = SequentialAgent.builder()
	// .name("DailyWorkflow")
	//.description("Collect data and execute it in parallel, then synthesize the results")
	// .inputKeys("input") // Change toinput,anddataCollectorBe consistent
	// .outputKey("workflow_output")
	// .state(sharedStateFactory)
	// .subAgents(List.of(dataCollector, synthesizer))
	// .build();
	//
	// Optional<OverAllState> result = dailyWorkflow.invoke(Map.of("input",
	//"Generate Hangzhou Today's Comprehensive Report"));
	//
	////Verify results
	//assertTrue(result.isPresent(), "Workflow execution result should exist");
	// OverAllState finalState = result.get();
	//
	//// Validate data collected in parallel
	// assertTrue(finalState.value("weather_data").isPresent(), "Weather data should exist");
	// assertTrue(finalState.value("news_data").isPresent(), "News data should exist");
	//
	//// Verify synthetic report
	// assertTrue(finalState.value("daily_report").isPresent(), "Integrated reporting should exist");
	//
	////output result
	// System.out.println("Parallel collection of weather data: " + finalState.value("weather_data").get());
	// System.out.println("Parallel collection of news data: " + finalState.value("news_data").get());
	// System.out.println("Synthetic comprehensive reporting: " + finalState.value("daily_report").get());
	// System.out.println("================================");
	//
	////Verify data quality
	// String weatherData = (String) finalState.value("weather_data").get();
	// String newsData = (String) finalState.value("news_data").get();
	// String dailyReport = (String) finalState.value("daily_report").get();
	//
	// assertFalse(weatherData.trim().isEmpty(), "Weather data should not be empty");
	//assertFalse(newsData.trim().isEmpty(), "News data should not be empty");
	// assertFalse(dailyReport.trim().isEmpty(), "Comprehensive report should not be empty");
	// }

	@Test
	public void testParallelAgentDuplicateOutputKeyValidation() throws GraphStateException {
		// Test that ParallelAgent correctly validates unique output keys

		// Create two agents with the same output key
		ReactAgent agent1 = ReactAgent.builder()
			.name("agent1")
			.model(chatModel)
			.description("The first test agent")
			.instruction("Test Assistant 1")
			.outputKey("duplicate_key") // Same output key as agent2
			.build();

		ReactAgent agent2 = ReactAgent.builder()
			.name("agent2")
			.model(chatModel)
			.description("The second test agent")
			.instruction("Test Assistant 2")
			.outputKey("duplicate_key") // Same output key as agent1
			.build();

		// Test that building ParallelAgent with duplicate output keys throws exception
		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
			ParallelAgent.builder()
				.name("duplicate_key_test")
				.description("Test verification of repeated outputKey")
				.mergeOutputKey("output")
				.subAgents(List.of(agent1, agent2))
				.build();
		}, "Should throw exception when sub-agents have duplicate output keys");

		// Verify the error message contains the duplicate key information
		String errorMessage = exception.getMessage();
		assertTrue(errorMessage.contains("Duplicate output keys found among sub-agents: [duplicate_key]"),
				"Error message should contain the duplicate key information");
		assertTrue(errorMessage.contains("Each sub-agent must have a unique output key"),
				"Error message should explain the requirement for unique output keys");
	}

	@Test
	public void testParallelAgentWithCustomMergeStrategy() throws Exception {
		// Test ParallelAgent with different merge strategies

		ReactAgent agent1 = ReactAgent.builder()
			.name("agent1")
			.model(chatModel)
			.description("The first test agent")
			.instruction("Please return number 1")
			.outputKey("result1")
			.build();

		ReactAgent agent2 = ReactAgent.builder()
			.name("agent2")
			.model(chatModel)
			.description("The second test agent")
			.instruction("Please return number 2")
			.outputKey("result2")
			.build();

		// Test with ListMergeStrategy
		ParallelAgent listMergeAgent = ParallelAgent.builder()
			.name("list_merge_test")
			.description("Test list merging strategy")
			.mergeOutputKey("merged_list")
			.mergeStrategy(new ParallelAgent.ListMergeStrategy())
			.subAgents(List.of(agent1, agent2))
			.build();

		Optional<OverAllState> result = listMergeAgent.invoke("test");
		assertTrue(result.isPresent());

		OverAllState state = result.get();
		assertTrue(state.value("merged_list").isPresent(), "Merged list result should be present");

		System.out.println("List merge result: " + state.value("merged_list").get());
	}

	@Test
	public void testParallelAgentWithMaxConcurrency() throws Exception {
		// Test ParallelAgent with concurrency control

		// Create multiple agents
		List<Agent> agents = new java.util.ArrayList<>();
		for (int i = 0; i < 5; i++) {
			agents.add(ReactAgent.builder()
				.name("worker_" + i)
				.model(chatModel)
				.description("Worker agent " + i)
				.instruction("Please return work results" + i)
				.outputKey("result_" + i)
				.build());
		}

		ParallelAgent concurrencyAgent = ParallelAgent.builder()
			.name("concurrency_test")
			.description("Test concurrency control")
			.mergeOutputKey("concurrency_results")
			.maxConcurrency(3) //Limit the maximum number of concurrencies to 3
			.subAgents(agents)
			.build();

		Optional<OverAllState> result = concurrencyAgent.invoke("test concurrency");
		assertTrue(result.isPresent());

		OverAllState state = result.get();
		assertTrue(state.value("concurrency_results").isPresent(), "Concurrency results should be present");

		System.out.println("Concurrency test completed with maxConcurrency=3");
		System.out.println("Results: " + state.value("concurrency_results").get());
	}

}
