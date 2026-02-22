///*
// * Copyright 2024-2026 the original author or authors.
// *
// * Licensed under the Apache License, Version 2.0 (the "License");
// * you may not use this file except in compliance with the License.
// * You may obtain a copy of the License at
// *
// *      https://www.apache.org/licenses/LICENSE-2.0
// *
// * Unless required by applicable law or agreed to in writing, software
// * distributed under the License is distributed on an "AS IS" BASIS,
// * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// * See the License for the specific language governing permissions and
// * limitations under the License.
// */
//package com.alibaba.cloud.ai.graph.node;
//
//import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
//import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingModel;
//import com.alibaba.cloud.ai.dashscope.embedding.DashScopeEmbeddingOptions;
//import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankModel;
//import com.alibaba.cloud.ai.dashscope.rerank.DashScopeRerankOptions;
//import com.alibaba.cloud.ai.graph.OverAllState;
//import com.alibaba.cloud.ai.model.RerankModel;
//
//import org.springframework.ai.document.Document;
//import org.springframework.ai.document.MetadataMode;
//import org.springframework.ai.embedding.EmbeddingModel;
//import org.springframework.ai.vectorstore.SimpleVectorStore;
//import org.springframework.ai.vectorstore.filter.Filter;
//import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
//
//import java.util.HashMap;
//import java.util.List;
//import java.util.Map;
//
//import org.junit.jupiter.api.Test;
//import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
//import org.slf4j.Logger;
//import org.slf4j.LoggerFactory;
//
//import static org.junit.jupiter.api.Assertions.assertEquals;
//
//public class KnowledgeRetrievalNodeTest {
//
//	private static final Logger logger = LoggerFactory.getLogger(KnowledgeRetrievalNode.class);
//
//	List<Document> documents = List.of(new Document(
//"Product manual: Product name: Intelligent robot\n" + "Product description: An intelligent robot is an intelligent device that can automatically complete various tasks.\n" + "Function:\n" + "1. Automatic navigation: The robot can automatically navigate to a designated location.\n"
//+ "2. Automatic grabbing: the robot can automatically grab items.\n" + "3. Automatic placement: the robot can automatically place items.\n",
//			Map.of("type", "instruction", // Document type
//					"year", "2023", // years
//					"month", "06" // month
//			)),
//			new Document(
//"Product manual: Product name: Smart home controller\n" + "Product description: The smart home controller is an integrated device that can remotely control a variety of smart home appliances.\n" + "Function:\n"
//+ "1. Remote control: Remotely control the switching and adjustment of home appliances through the mobile phone APP.\n" + "2. Scheduled tasks: Set home appliances to be turned on or off at a scheduled time.\n" + "3. Scene mode: Supports one-click switching of multiple scene modes.\n"
//+ "4. Energy consumption statistics: real-time monitoring and statistics of household appliances energy consumption data.\n",
//
//					Map.of("type", "instruction", // Document type
//							"year", "2024", // years
//							"month", "02" // month
//
//					)));
//
//	String apiKey = System.getenv().getOrDefault("AI_DASHSCOPE_API_KEY", "test-api-key");
//
//	DashScopeApi dashScopeApi = DashScopeApi.builder().apiKey(apiKey).build();
//
//	;
//
//	EmbeddingModel embeddingModel = new DashScopeEmbeddingModel(dashScopeApi, MetadataMode.EMBED,
//			DashScopeEmbeddingOptions.builder().withModel("text-embedding-v2").build());
//
//	SimpleVectorStore simpleVectorStore = SimpleVectorStore.builder(embeddingModel).build();
//
//	RerankModel rerankModel = new DashScopeRerankModel(dashScopeApi);
//
//	Filter.Expression filterExpression = new FilterExpressionBuilder().eq("type", "instruction").build();
//
//	DashScopeRerankOptions rerankOptions = new DashScopeRerankOptions();
//
//	Map<String, Object> initStateMap() {
//		Map<String, Object> modifiableMap = new HashMap<>();
//modifiableMap.put("user_prompt", "As a robot product expert, you will answer users' needs");
//		modifiableMap.put("top_k", 5);
//		modifiableMap.put("similarity_threshold", 0.1);
//		modifiableMap.put("filter_expression", filterExpression);
//		modifiableMap.put("enable_ranker", true);
//		modifiableMap.put("rerank_model", rerankModel);
//		modifiableMap.put("rerank_options", rerankOptions);
//		modifiableMap.put("vector_store", simpleVectorStore);
//		return modifiableMap;
//	}
//
//	KnowledgeRetrievalNode.Builder initNodeBuilder() {
//		return KnowledgeRetrievalNode.builder()
//			.userPromptKey("user_prompt")
//			.topKKey("top_k")
//			.similarityThresholdKey("similarity_threshold")
//			.filterExpressionKey("filter_expression")
//			.enableRankerKey("enable_ranker")
//			.rerankModelKey("rerank_model")
//			.rerankOptionsKey("rerank_options")
//			.vectorStoreKey("vector_store");
//	}
//
//	@Test
//	@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
//	void testValueFirst() throws Exception {
//		simpleVectorStore.add(documents);
//
//		KnowledgeRetrievalNode node = initNodeBuilder().topK(5).isKeyFirst(false).build();
//		Map<String, Object> stateMap = initStateMap();
////Modify topk
//		stateMap.put("top_k", 1);
//		Map<String, Object> newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(2, node.documents.size());
//	}
//
//	@Test
//	@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
//	void testTopK() throws Exception {
//
//		simpleVectorStore.add(documents);
//
//		KnowledgeRetrievalNode node = initNodeBuilder().build();
//		Map<String, Object> stateMap = initStateMap();
////The original topk is 5
//		Map<String, Object> newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(2, node.documents.size());
////Modify topk
//		stateMap.put("top_k", 1);
//		newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(1, node.documents.size());
//
//	}
//
//	@Test
//	@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
//	void testSimilarityThreshold() throws Exception {
//
//		simpleVectorStore.add(documents);
//
//		KnowledgeRetrievalNode node = initNodeBuilder().build();
//		Map<String, Object> stateMap = initStateMap();
//		// originalsimilarity_thresholdfor0，1
//		Map<String, Object> newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(2, node.documents.size());
////Modify to 0.5
//		stateMap.put("similarity_threshold", 0.5);
//		newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(1, node.documents.size());
//
//	}
//
//	@Test
//	@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
//	void testFilterExpression() throws Exception {
//
//		simpleVectorStore.add(documents);
//
//		KnowledgeRetrievalNode node = initNodeBuilder().build();
//		Map<String, Object> stateMap = initStateMap();
//// The original filtering condition is eq("type", "instruction")
//		Map<String, Object> newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(2, node.documents.size());
//// Now modified to eq("type", "book")
//		stateMap.put("filter_expression", new FilterExpressionBuilder().eq("type", "book").build());
//		newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(0, node.documents.size());
//
//	}
//
//	@Test
//	@EnabledIfEnvironmentVariable(named = "AI_DASHSCOPE_API_KEY", matches = ".+")
//	void testRerank() throws Exception {
//
//		simpleVectorStore.add(documents);
//
//		KnowledgeRetrievalNode node = initNodeBuilder().build();
//		Map<String, Object> stateMap = initStateMap();
//
//// rerankOptions originally defaulted to topN as 3
//		Map<String, Object> newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(2, node.documents.size());
////Reset topN quantity
//		rerankOptions.setTopN(1);
//		stateMap.put("rerank_options", rerankOptions);
//		newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(1, node.documents.size());
//// Disable reordering
//		stateMap.put("enable_ranker", false);
//		newState = node.apply(new OverAllState(stateMap));
//		logger.info("Document search results addedpromptfor{}", newState.get("user_prompt"));
//		assertEquals(2, node.documents.size());
//
//	}
//
//}
