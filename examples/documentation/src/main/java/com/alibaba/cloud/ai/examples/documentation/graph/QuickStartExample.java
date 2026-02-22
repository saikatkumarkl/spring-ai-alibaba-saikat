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
package com.alibaba.cloud.ai.examples.documentation.graph;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.CompileConfig;
import com.alibaba.cloud.ai.graph.CompiledGraph;
import com.alibaba.cloud.ai.graph.KeyStrategy;
import com.alibaba.cloud.ai.graph.KeyStrategyFactory;
import com.alibaba.cloud.ai.graph.NodeOutput;
import com.alibaba.cloud.ai.graph.OverAllState;
import com.alibaba.cloud.ai.graph.RunnableConfig;
import com.alibaba.cloud.ai.graph.StateGraph;
import com.alibaba.cloud.ai.graph.action.NodeAction;
import com.alibaba.cloud.ai.graph.checkpoint.config.SaverConfig;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphStateException;
import com.alibaba.cloud.ai.graph.state.strategy.AppendStrategy;
import com.alibaba.cloud.ai.graph.state.strategy.ReplaceStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.alibaba.cloud.ai.graph.StateGraph.END;
import static com.alibaba.cloud.ai.graph.StateGraph.START;
import static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async;
import static com.alibaba.cloud.ai.graph.action.AsyncNodeAction.node_async;

/**
 * Graph Workflow Orchestration Quick Start Example
 * 
 * This example demonstrates how to build intelligent workflows using CordonData Graph by decomposing a customer email processing flow into discrete steps.
 * 
 * This example includes:
 * 1. State definition (EmailClassification)
 * 2. Node implementation (read email, classify intent, search docs, bug tracking, draft reply, human review, send reply)
 * 3. Graph assembly and configuration
 * 4. Test execution
 */
public class QuickStartExample {

	private static final Logger log = LoggerFactory.getLogger(QuickStartExample.class);

	// ==================== State Definition ====================

	/**
	 * Email Classification Structure
	 */
	public static class EmailClassification {
		private String intent;      // "question", "bug", "billing", "feature", "complex"
		private String urgency;     // "low", "medium", "high", "critical"
		private String topic;
		private String summary;

		public EmailClassification() {
		}

		public EmailClassification(String intent, String urgency, String topic, String summary) {
			this.intent = intent;
			this.urgency = urgency;
			this.topic = topic;
			this.summary = summary;
		}

		public String getIntent() {
			return intent;
		}

		public void setIntent(String intent) {
			this.intent = intent;
		}

		public String getUrgency() {
			return urgency;
		}

		public void setUrgency(String urgency) {
			this.urgency = urgency;
		}

		public String getTopic() {
			return topic;
		}

		public void setTopic(String topic) {
			this.topic = topic;
		}

		public String getSummary() {
			return summary;
		}

		public void setSummary(String summary) {
			this.summary = summary;
		}

		@Override
		public String toString() {
			return String.format("EmailClassification{intent='%s', urgency='%s', topic='%s', summary='%s'}", 
					intent, urgency, topic, summary);
		}
	}

	/**
	 * Configure State Key Strategies
	 */
	public static KeyStrategyFactory createKeyStrategyFactory() {
		return () -> {
			HashMap<String, KeyStrategy> strategies = new HashMap<>();
			strategies.put("email_content", new ReplaceStrategy());
			strategies.put("sender_email", new ReplaceStrategy());
			strategies.put("email_id", new ReplaceStrategy());
			strategies.put("classification", new ReplaceStrategy());
			strategies.put("search_results", new ReplaceStrategy());
			strategies.put("customer_history", new ReplaceStrategy());
			strategies.put("draft_response", new ReplaceStrategy());
			strategies.put("messages", new AppendStrategy());
			strategies.put("next_node", new ReplaceStrategy());
			strategies.put("status", new ReplaceStrategy());
			strategies.put("review_data", new ReplaceStrategy());
			return strategies;
		};
	}

	// ==================== Node Implementation ====================

	/**
	 * Read Email Node
	 */
	public static class ReadEmailNode implements NodeAction {

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			// In production, this would connect to your email service
			String emailContent = state.value("email_content")
					.map(v -> (String) v)
					.orElse("");

			log.info("Processing email: {}", emailContent);

			List<String> messages = new ArrayList<>();
			messages.add("Processing email: " + emailContent);

			return Map.of("messages", messages);
		}
	}

	/**
	 * Classify Intent Node
	 */
	public static class ClassifyIntentNode implements NodeAction {

		private final ChatClient chatClient;

		public ClassifyIntentNode(ChatClient.Builder chatClientBuilder) {
			this.chatClient = chatClientBuilder.build();
		}

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			String emailContent = state.value("email_content")
					.map(v -> (String) v)
					.orElseThrow(() -> new IllegalStateException("No email content"));
			String senderEmail = state.value("sender_email")
					.map(v -> (String) v)
					.orElse("unknown");

			// Format prompt on demand, not stored in state
			String classificationPrompt = String.format("""
					Analyze this customer email and classify it:

					Email: %s
					Sender: %s

					Provide classification including intent, urgency, topic, and summary.

					Intent should be one of: question, bug, billing, feature, complex
					Urgency should be one of: low, medium, high, critical

					Return in JSON format: {"intent": "...", "urgency": "...", "topic": "...", "summary": "..."}
					""", emailContent, senderEmail);

			// Get structured response
			String response = chatClient.prompt()
					.user(classificationPrompt)
					.call()
					.content();

			// Parse into EmailClassification object
			EmailClassification classification = parseClassification(response);

			// Determine next node based on classification
			String nextNode;
			if ("billing".equals(classification.getIntent()) ||
					"critical".equals(classification.getUrgency())) {
				nextNode = "human_review";
			} else if (List.of("question", "feature").contains(classification.getIntent())) {
				nextNode = "search_documentation";
			} else if ("bug".equals(classification.getIntent())) {
				nextNode = "bug_tracking";
			} else {
				nextNode = "draft_response";
			}

			// Store classification as a single object in state
			return Map.of(
					"classification", classification,
					"next_node", nextNode
			);
		}

		/**
		 * Simplified JSON parsing (use Jackson or Gson in real applications)
		 */
		private EmailClassification parseClassification(String jsonResponse) {
			EmailClassification classification = new EmailClassification();

			// Simple regex parsing
			Pattern intentPattern = Pattern.compile("\"intent\"\\s*:\\s*\"([^\"]+)\"");
			Pattern urgencyPattern = Pattern.compile("\"urgency\"\\s*:\\s*\"([^\"]+)\"");
			Pattern topicPattern = Pattern.compile("\"topic\"\\s*:\\s*\"([^\"]+)\"");
			Pattern summaryPattern = Pattern.compile("\"summary\"\\s*:\\s*\"([^\"]+)\"");

			Matcher matcher = intentPattern.matcher(jsonResponse);
			if (matcher.find()) {
				classification.setIntent(matcher.group(1));
			}

			matcher = urgencyPattern.matcher(jsonResponse);
			if (matcher.find()) {
				classification.setUrgency(matcher.group(1));
			}

			matcher = topicPattern.matcher(jsonResponse);
			if (matcher.find()) {
				classification.setTopic(matcher.group(1));
			}

			matcher = summaryPattern.matcher(jsonResponse);
			if (matcher.find()) {
				classification.setSummary(matcher.group(1));
			}

			// If parsing fails, set default values
			if (classification.getIntent() == null) {
				classification.setIntent("question");
			}
			if (classification.getUrgency() == null) {
				classification.setUrgency("medium");
			}
			if (classification.getTopic() == null) {
				classification.setTopic("general");
			}
			if (classification.getSummary() == null) {
				classification.setSummary("Customer email that needs processing");
			}

			return classification;
		}
	}

	/**
	 * Documentation Search Node
	 */
	public static class SearchDocumentationNode implements NodeAction {

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			// Build search query from classification
			EmailClassification classification = state.value("classification")
					.map(v -> (EmailClassification) v)
					.orElse(new EmailClassification());
			String query = classification.getIntent() + " " + classification.getTopic();

			try {
				// Implement your search logic
				// Store raw search results, not formatted text
				List<String> searchResults = List.of(
						"Reset password via Settings > Security > Change Password",
						"Password must be at least 12 characters",
						"Include uppercase, lowercase, numbers, and symbols"
				);

				log.info("Searching documentation for: {}", query);

				return Map.of(
						"search_results", searchResults,
						"next_node", "draft_response"
				);
			} catch (Exception e) {
				// For recoverable search errors, store error and continue
				log.warn("Search error: {}", e.getMessage());
				List<String> errorResult = List.of("Search temporarily unavailable: " + e.getMessage());
				return Map.of(
						"search_results", errorResult,
						"next_node", "draft_response"
				);
			}
		}
	}

	/**
	 * Bug Tracking Node
	 */
	public static class BugTrackingNode implements NodeAction {

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			// Create ticket in your bug tracking system
			String ticketId = "BUG-12345";  // Will be created via API

			log.info("Created bug ticket: {}", ticketId);

			return Map.of(
					"search_results", List.of("Bug ticket created: " + ticketId),
					"current_step", "bug_tracked",
					"next_node", "draft_response"
			);
		}
	}

	/**
	 * Draft Response Node
	 */
	public static class DraftResponseNode implements NodeAction {

		private final ChatClient chatClient;

		public DraftResponseNode(ChatClient.Builder chatClientBuilder) {
			this.chatClient = chatClientBuilder.build();
		}

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			EmailClassification classification = state.value("classification")
					.map(v -> (EmailClassification) v)
					.orElse(new EmailClassification());
			String emailContent = state.value("email_content")
					.map(v -> (String) v)
					.orElse("");

			// Format context on demand from raw state data
			List<String> contextSections = new ArrayList<>();

			Optional<List<String>> searchResults = state.value("search_results")
					.map(v -> (List<String>) v);
			if (searchResults.isPresent()) {
				// Format search results for prompt
				List<String> docs = searchResults.get();
				String formattedDocs = docs.stream()
						.map(doc -> "- " + doc)
						.collect(Collectors.joining("\n"));
				contextSections.add("Related documentation:\n" + formattedDocs);
			}

			Optional<Map<String, Object>> customerHistory = state.value("customer_history")
					.map(v -> (Map<String, Object>) v);
			if (customerHistory.isPresent()) {
				// Format customer data for prompt
				Map<String, Object> history = customerHistory.get();
				contextSections.add("Customer tier: " + history.getOrDefault("tier", "standard"));
			}

			// Build prompt with formatted context
			String draftPrompt = String.format("""
					Draft a reply for this customer email:
					%s

					Email intent: %s
					Urgency: %s

					%s

					Guidelines:
					- Be professional and helpful
					- Address their specific issue
					- Use the provided documentation when relevant
					""",
					emailContent,
					classification.getIntent(),
					classification.getUrgency(),
					String.join("\n", contextSections)
			);

			String response = chatClient.prompt()
					.user(draftPrompt)
					.call()
					.content();

			// Determine if human review is needed based on urgency and intent
			boolean needsReview =
					List.of("high", "critical").contains(classification.getUrgency()) ||
							"complex".equals(classification.getIntent());

			// Route to appropriate next node
			String nextNode = needsReview ? "human_review" : "send_reply";

			return Map.of(
					"draft_response", response,  // Store only raw response
					"next_node", nextNode
			);
		}
	}

	/**
	 * Human Review Node
	 * 
	 * Note: In interruptBefore mode, the interruption is set in the compile configuration (see createEmailAgentGraph method).
	 * The node itself does not need any special handling, just return state normally.
	 * When execution reaches before this node, the Graph will automatically interrupt and wait for human input.
	 */
	public static class HumanReviewNode implements NodeAction {

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			EmailClassification classification = state.value("classification")
					.map(v -> (EmailClassification) v)
					.orElse(new EmailClassification());

			// Prepare review data
			Map<String, Object> reviewData = Map.of(
					"email_id", state.value("email_id").map(v -> (String) v).orElse(""),
					"original_email", state.value("email_content").map(v -> (String) v).orElse(""),
					"draft_response", state.value("draft_response").map(v -> (String) v).orElse(""),
					"urgency", classification.getUrgency(),
					"intent", classification.getIntent(),
					"action", "Please review and approve/edit this response"
			);

			log.info("Waiting for human review: {}", reviewData);

			// Return review data and next node
			// Note: In interruptBefore mode, this node is executed only after human input
			return Map.of(
					"review_data", reviewData,
					"status", "waiting_for_review",
					"next_node", "send_reply"
			);
		}
	}

	/**
	 * Send Reply Node
	 */
	public static class SendReplyNode implements NodeAction {

		@Override
		public Map<String, Object> apply(OverAllState state) throws Exception {
			String draftResponse = state.value("draft_response")
					.map(v -> (String) v)
					.orElse("");

			// Integrate with email service
			log.info("Sending reply: {}...", 
					draftResponse.length() > 100 
							? draftResponse.substring(0, 100) 
							: draftResponse);

			return Map.of("status", "sent");
		}
	}

	// ==================== Graph Assembly ====================

	/**
	 * Create Email Processing Graph
	 */
	public static CompiledGraph createEmailAgentGraph(ChatModel chatModel) throws GraphStateException {
		// Configure ChatClient
		ChatClient.Builder chatClientBuilder = ChatClient.builder(chatModel);

		// Create nodes
		var readEmail = node_async(new ReadEmailNode());
		var classifyIntent = node_async(new ClassifyIntentNode(chatClientBuilder));
		var searchDocumentation = node_async(new SearchDocumentationNode());
		var bugTracking = node_async(new BugTrackingNode());
		var draftResponse = node_async(new DraftResponseNode(chatClientBuilder));
		var humanReview = node_async(new HumanReviewNode());
		var sendReply = node_async(new SendReplyNode());

		// Create graph
		StateGraph workflow = new StateGraph(createKeyStrategyFactory())
				.addNode("read_email", readEmail)
				.addNode("classify_intent", classifyIntent)
				.addNode("search_documentation", searchDocumentation)
				.addNode("bug_tracking", bugTracking)
				.addNode("draft_response", draftResponse)
				.addNode("human_review", humanReview)
				.addNode("send_reply", sendReply);

		// Add basic edges
		workflow.addEdge(START, "read_email");
		workflow.addEdge("read_email", "classify_intent");
		workflow.addEdge("send_reply", END);

		// Add conditional edges (based on next_node returned by nodes)
		workflow.addConditionalEdges("classify_intent",
				edge_async(state -> {
					return (String) state.value("next_node").orElse("draft_response");
				}),
				Map.of(
						"search_documentation", "search_documentation",
						"bug_tracking", "bug_tracking",
						"human_review", "human_review",
						"draft_response", "draft_response"
				));

		workflow.addConditionalEdges("draft_response",
				edge_async(state -> {
					return (String) state.value("next_node").orElse("send_reply");
				}),
				Map.of(
						"human_review", "human_review",
						"send_reply", "send_reply"
				));

		workflow.addConditionalEdges("human_review",
				edge_async(state -> {
					return (String) state.value("next_node").orElse("send_reply");
				}),
				Map.of(
						"send_reply", "send_reply"
				));

		workflow.addEdge("search_documentation", "draft_response");
		workflow.addEdge("bug_tracking", "draft_response");

		// Configure persistence
		var memory = new MemorySaver();
		var compileConfig = CompileConfig.builder()
				.saverConfig(SaverConfig.builder()
						.register(memory)
						.build())
				.interruptBefore("human_review")  // Interrupt before human review
				.build();

		return workflow.compile(compileConfig);
	}

	// ==================== Test Methods ====================

	/**
	 * Test Urgent Billing Issue
	 */
	public static void testBillingIssue(CompiledGraph app) throws Exception {
		log.info("=== Test Urgent Billing Issue ===");

		// Test urgent billing issue
		Map<String, Object> initialState = Map.of(
				"email_content", "My subscription was charged twice! This is urgent!",
				"sender_email", "customer@example.com",
				"email_id", "email_123",
				"messages", new ArrayList<String>()
		);

		// Run with thread_id for persistence
		var config = RunnableConfig.builder()
				.threadId("customer_123")
				.build();

		// Execute with stream until interrupt point (human_review)
		// Graph will pause at human_review (because interruptBefore is configured)
		Flux<NodeOutput> stream = app.stream(initialState, config);
		stream
				.doOnNext(output -> log.info("Node output: {}", output))
				.doOnError(error -> log.error("Execution error: {}", error.getMessage()))
				.doOnComplete(() -> log.info("Stream completed"))
				.blockLast();

		// Get current state, check if draft reply exists
		var currentState = app.getState(config);
		Map<String, Object> stateData = currentState.state().data();
		String draftResponse = (String) stateData.get("draft_response");
		if (draftResponse != null) {
			log.info("Draft ready for review: {}...", 
					draftResponse.length() > 100 
							? draftResponse.substring(0, 100) 
							: draftResponse);
		}

		// When ready, provide human input to resume
		// Use updateState to update state (in interruptBefore mode, pass null as node ID)
		var updatedConfig = app.updateState(config, Map.of(
				"approved", true,
				"edited_response", "We sincerely apologize for the duplicate charge. I have immediately initiated a refund..."
		), null);

		// Continue execution (input is null, use previous state)
		app.stream(null, updatedConfig)
				.doOnNext(output -> log.info("Node output: {}", output))
				.doOnError(error -> log.error("Execution error: {}", error.getMessage()))
				.doOnComplete(() -> log.info("Stream completed"))
				.blockLast();

		// Get final state
		var finalState = app.getState(updatedConfig);
		String status = (String) finalState.state().data().get("status");
		log.info("Email sent successfully! Status: {}", status);
	}

	/**
	 * Test Simple Question
	 */
	public static void testSimpleQuestion(CompiledGraph app) {
		log.info("=== Test Simple Question ===");

		Map<String, Object> initialState = Map.of(
				"email_content", "How do I reset my password?",
				"sender_email", "user@example.com",
				"email_id", "email_456",
				"messages", new ArrayList<String>()
		);

		var config = RunnableConfig.builder()
				.threadId("user_456")
				.build();

		// invoke returns Optional<OverAllState>, use orElseThrow() to get result
		var result = app.invoke(initialState, config).orElseThrow();
		log.info("Simple question processed. Status: {}", result.data().get("status"));
	}

	/**
	 * Main Method
	 */
	public static void main(String[] args) throws Exception {
		log.info("========================================");
		log.info("Graph Workflow Orchestration Quick Start Example");
		log.info("========================================\n");

		// Note: A ChatModel instance must be provided for actual usage
		// Create DashScope API instance
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		// Create ChatModel
		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		CompiledGraph app = createEmailAgentGraph(chatModel);

		testBillingIssue(app);
//		testSimpleQuestion(app);
	}
}

