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
import com.alibaba.cloud.ai.graph.agent.ReactAgent;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Retrieval-Augmented Generation (RAG) Example
 *
 * Demonstrates how to use RAG technology to provide external knowledge to LLMs, including:
 * 1. Building a knowledge base
 * 2. Two-step RAG
 * 3. Agentic RAG
 * 4. Hybrid RAG
 *
 * Reference: advanced_doc/rag.md
 */
public class RAGExample {

	private final ChatModel chatModel;
	private final VectorStore vectorStore;

	public RAGExample(ChatModel chatModel, VectorStore vectorStore) {
		this.chatModel = chatModel;
		this.vectorStore = vectorStore;
	}

	/**
	 * Main method: run all examples
	 *
	 * Note: ChatModel and VectorStore instances must be configured to run
	 */
	public static void main(String[] args) {
		// Create DashScope API instance
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Create ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// TODO: Configure your VectorStore instance
		// Example: VectorStore vectorStore = new YourVectorStoreImplementation();
		VectorStore vectorStore = null; // Replace with an actual VectorStore instance

		if (chatModel == null || vectorStore == null) {
			System.err.println("Error: Please configure ChatModel and VectorStore instances first");
			System.err.println("Please set the AI_DASHSCOPE_API_KEY environment variable and configure a VectorStore instance");
			return;
		}

		// Create example instance
		RAGExample example = new RAGExample(chatModel, vectorStore);

		// Run all examples
		example.runAllExamples();
	}

	/**
	 * Example 1: Building a Knowledge Base
	 *
	 * Load, split, embed, and store documents into a vector database
	 */
	public void example1_buildKnowledgeBase() {
		// 1. Load documents
		Resource resource = new FileSystemResource("path/to/document.txt");
		TextReader textReader = new TextReader(resource);
		List<Document> documents = textReader.get();

		// 2. Split documents into chunks
		TokenTextSplitter splitter = new TokenTextSplitter();
		List<Document> chunks = splitter.apply(documents);

		// 3. Add chunks to vector store
		vectorStore.add(chunks);

		// Now the vector store can be used for retrieval
		List<Document> results = vectorStore.similaritySearch("query text");

		System.out.println("Knowledge base built, retrieved " + results.size() + " relevant documents");
	}

	/**
	 * Example 2: Two-Step RAG
	 *
	 * The retrieval step is always executed before the generation step
	 */
	public void example2_twoStepRAG() {
		// Two-step RAG: Retrieve -> Generate
		String userQuestion = "What models does CordonData support?";

		// Step 1: Retrieve relevant documents
		List<Document> relevantDocs = vectorStore.similaritySearch(userQuestion);

		// Step 2: Build context from documents
		String context = relevantDocs.stream()
				.map(Document::getText)
				.collect(Collectors.joining("\n\n"));

		// Step 3: Generate answer with context
		ChatClient chatClient = ChatClient.builder(chatModel).build();
		String answer = chatClient.prompt()
				.user(u -> u.text("Answer the question based on the following context:\n\nContext:\n" + context + "\n\nQuestion: " + userQuestion))
				.call()
				.content();

		System.out.println("Answer: " + answer);

		// Retrieved documents are added as context to the prompt
		// ChatModel uses the augmented context to generate the answer

		System.out.println("Two-step RAG example completed");
	}

	/**
	 * Example 3: Agentic RAG
	 *
	 * The Agent decides when and how to retrieve information
	 */
	public void example3_agenticRAG() throws Exception {
		// Create document search tool
		class DocumentSearchTool {
			public Response search(Request request) {
				// Retrieve relevant documents from vector store
				List<Document> docs = vectorStore.similaritySearch(request.query());

				// Combine document contents
				String combinedContent = docs.stream()
						.map(Document::getText)
						.collect(Collectors.joining("\n\n"));

				return new Response(combinedContent);
			}

			public record Request(String query) { }

			public record Response(String content) { }
		}

		DocumentSearchTool searchTool = new DocumentSearchTool();

		// Create tool callback
		ToolCallback searchCallback = FunctionToolCallback.builder("search_documents",
						(Function<DocumentSearchTool.Request, DocumentSearchTool.Response>)
								request -> searchTool.search(request))
				.description("Search documents to find relevant information")
				.inputType(DocumentSearchTool.Request.class)
				.build();

		// Create Agent with retrieval tools
		ReactAgent ragAgent = ReactAgent.builder()
				.name("rag_agent")
				.model(chatModel)
				.instruction("You are an intelligent assistant. Use the search_documents tool when you need to look up information. " +
						"Answer the user's questions based on the retrieved information and cite relevant passages.")
				.tools(searchCallback)
				.build();

		// The Agent automatically decides when to call the retrieval tool
		ragAgent.invoke("What vector databases does CordonData support?");

		System.out.println("Agentic RAG example completed");
	}

	/**
	 * Example 4: Multi-Source RAG
	 *
	 * The Agent can retrieve information from multiple sources
	 */
	public void example4_multiSourceRAG() throws Exception {
		// Create multiple retrieval tools
		class WebSearchTool {
			public Response search(Request request) {
				return new Response("Information from web search: " + request.query());
			}

			public record Request(String query) { }

			public record Response(String content) { }
		}

		class DatabaseQueryTool {
			public Response query(Request request) {
				return new Response("Information from database query: " + request.query());
			}

			public record Request(String query) { }

			public record Response(String content) { }
		}

		class DocumentSearchTool {
			public Response search(Request request) {
				List<Document> docs = vectorStore.similaritySearch(request.query());
				String content = docs.stream()
						.map(Document::getText)
						.collect(Collectors.joining("\n\n"));
				return new Response(content);
			}

			public record Request(String query) { }

			public record Response(String content) { }
		}

		WebSearchTool webSearchTool = new WebSearchTool();
		DatabaseQueryTool dbQueryTool = new DatabaseQueryTool();
		DocumentSearchTool docSearchTool = new DocumentSearchTool();

		ToolCallback webSearchCallback = FunctionToolCallback.builder("web_search",
						(Function<WebSearchTool.Request, WebSearchTool.Response>)
								req -> webSearchTool.search(req))
				.description("Search the internet for the latest information")
				.inputType(WebSearchTool.Request.class)
				.build();

		ToolCallback databaseQueryCallback = FunctionToolCallback.builder("database_query",
						(Function<DatabaseQueryTool.Request, DatabaseQueryTool.Response>)
								req -> dbQueryTool.query(req))
				.description("Query the internal database")
				.inputType(DatabaseQueryTool.Request.class)
				.build();

		ToolCallback documentSearchCallback = FunctionToolCallback.builder("document_search",
						(Function<DocumentSearchTool.Request, DocumentSearchTool.Response>)
								req -> docSearchTool.search(req))
				.description("Search the document library")
				.inputType(DocumentSearchTool.Request.class)
				.build();

		// The Agent can access multiple retrieval sources
		ReactAgent multiSourceAgent = ReactAgent.builder()
				.name("multi_source_rag_agent")
				.model(chatModel)
				.instruction("You have access to multiple information sources:\n" +
						"1. web_search - for the latest internet information\n" +
						"2. database_query - for internal data\n" +
						"3. document_search - for the document library\n" +
						"Choose the most appropriate tool based on the question.")
				.tools(webSearchCallback, databaseQueryCallback, documentSearchCallback)
				.build();

		multiSourceAgent.invoke("Compare the features in our product documentation with the latest market trends");

		System.out.println("Multi-tool Agentic RAG example completed");
	}

	/**
	 * Example 5: Hybrid RAG
	 *
	 * Combines query enhancement, retrieval validation, and answer validation
	 */
	public void example5_hybridRAG() {
		class HybridRAGSystem {
			private final ChatModel chatModel;
			private final VectorStore vectorStore;

			public HybridRAGSystem(ChatModel chatModel, VectorStore vectorStore) {
				this.chatModel = chatModel;
				this.vectorStore = vectorStore;
			}

			public String answer(String userQuestion) {
				// 1. Query enhancement
				String enhancedQuery = enhanceQuery(userQuestion);

				int maxAttempts = 3;
				for (int attempt = 0; attempt < maxAttempts; attempt++) {
					// 2. Retrieve documents
					List<Document> docs = vectorStore.similaritySearch(enhancedQuery);

					// 3. Retrieval validation
					if (!isRetrievalSufficient(docs)) {
						enhancedQuery = refineQuery(enhancedQuery, docs);
						continue;
					}

					// 4. Generate answer
					String answer = generateAnswer(userQuestion, docs);

					// 5. Answer validation
					ValidationResult validation = validateAnswer(answer, docs);
					if (validation.isValid()) {
						return answer;
					}

					// 6. Decide next step based on validation result
					if (validation.shouldRetry()) {
						enhancedQuery = refineBasedOnValidation(enhancedQuery, validation);
					}
					else {
						return answer; // Return the current best answer
					}
				}

				return "Unable to generate a satisfactory answer";
			}

			private String enhanceQuery(String query) {
				return query; // Implement query enhancement logic
			}

			private boolean isRetrievalSufficient(List<Document> docs) {
				return !docs.isEmpty() && calculateRelevanceScore(docs) > 0.7;
			}

			private double calculateRelevanceScore(List<Document> docs) {
				return 0.8; // Implement relevance scoring logic
			}

			private String refineQuery(String query, List<Document> docs) {
				return query; // Implement query refinement logic
			}

			private String generateAnswer(String question, List<Document> docs) {
				String context = docs.stream()
						.map(Document::getText)
						.collect(Collectors.joining("\n\n"));

				ChatClient client = ChatClient.builder(chatModel).build();
				return client.prompt()
						.system("Answer the question based on the following context:\n" + context)
						.user(question)
						.call()
						.content();
			}

			private ValidationResult validateAnswer(String answer, List<Document> docs) {
				// Implement answer validation logic
				return new ValidationResult(true, false);
			}

			private String refineBasedOnValidation(String query, ValidationResult validation) {
				return query; // Refine query based on validation result
			}

			class ValidationResult {
				private boolean valid;
				private boolean shouldRetry;

				public ValidationResult(boolean valid, boolean shouldRetry) {
					this.valid = valid;
					this.shouldRetry = shouldRetry;
				}

				public boolean isValid() {
					return valid;
				}

				public boolean shouldRetry() {
					return shouldRetry;
				}
			}
		}

		HybridRAGSystem hybridRAG = new HybridRAGSystem(chatModel, vectorStore);
		String answer = hybridRAG.answer("Explain the core features of CordonData");

		System.out.println("Hybrid RAG answer: " + answer);
		System.out.println("Hybrid RAG example completed");
	}

	/**
	 * Run all examples
	 */
	public void runAllExamples() {
		System.out.println("=== Retrieval-Augmented Generation (RAG) Examples ===\n");

		try {
			System.out.println("Example 1: Building a Knowledge Base");
			// example1_buildKnowledgeBase(); // Requires an actual file path
			System.out.println();

			System.out.println("Example 2: Two-Step RAG");
			example2_twoStepRAG();
			System.out.println();

			System.out.println("Example 3: Agentic RAG");
			example3_agenticRAG();
			System.out.println();

			System.out.println("Example 4: Multi-Source RAG");
			example4_multiSourceRAG();
			System.out.println();

			System.out.println("Example 5: Hybrid RAG");
			example5_hybridRAG();
			System.out.println();

		}
		catch (Exception e) {
			System.err.println("Error running example: " + e.getMessage());
			e.printStackTrace();
		}
	}
}

