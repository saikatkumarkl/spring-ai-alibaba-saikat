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
package com.alibaba.cloud.ai.examples.documentation.framework.tutorials;

import com.alibaba.cloud.ai.dashscope.api.DashScopeApi;
import com.alibaba.cloud.ai.dashscope.chat.DashScopeChatModel;
import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.graph.checkpoint.savers.MemorySaver;
import com.alibaba.cloud.ai.graph.exception.GraphRunnerException;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.converter.BeanOutputConverter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Structured Output Tutorial - Complete Code Examples
 * Demonstrates how to have Agent return structured data in a specific format
 *
 * Source: structured-output.md
 */
public class StructuredOutputExample {

	// ==================== Basic Class Definitions ====================

	/**
	 * Example 1: Basic JSON Schema
	 */
	public static void basicJsonSchema() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Use BeanOutputConverter to generate outputSchema
		BeanOutputConverter<ContactInfo> outputConverter = new BeanOutputConverter<>(ContactInfo.class);
		String format = outputConverter.getFormat();

		ReactAgent agent = ReactAgent.builder()
				.name("contact_extractor")
				.model(chatModel)
				.outputSchema(format)
				.build();

		AssistantMessage result = agent.call(
				"Extract contact information from the following: Zhang San, zhangsan@example.com, (555) 123-4567"
		);

		System.out.println(result.getText());
		// Output: {"name": "Zhang San", "email": "zhangsan@example.com", "phone": "(555) 123-4567"}
	}

	/**
	 * Example 2: Complex Nested Schema
	 */
	public static void complexNestedSchema() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Use BeanOutputConverter to generate outputSchema
		BeanOutputConverter<ProductReview> outputConverter = new BeanOutputConverter<>(ProductReview.class);
		String format = outputConverter.getFormat();

		ReactAgent agent = ReactAgent.builder()
				.name("review_analyzer")
				.model(chatModel)
				.outputSchema(format)
				.build();

		AssistantMessage result = agent.call(
				"Analyze the review: This product is great, 5-star rating. Fast delivery, but a bit pricey."
		);

		System.out.println(result.getText());
		// Output: {"rating": 5, "sentiment": "positive", "keyPoints": [...], "details": {...}}
	}

	/**
	 * Example 3: Structured Analysis Schema
	 */
	public static void structuredAnalysisSchema() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Use BeanOutputConverter to generate outputSchema
		BeanOutputConverter<TextAnalysis> outputConverter = new BeanOutputConverter<>(TextAnalysis.class);
		String format = outputConverter.getFormat();

		ReactAgent agent = ReactAgent.builder()
				.name("text_analyzer")
				.model(chatModel)
				.outputSchema(format)
				.build();

		AssistantMessage result = agent.call(
				"Analyze this text: Yesterday, Li Ming attended Alibaba's tech conference in Beijing and felt the power of innovation."
		);

		System.out.println(result.getText());
	}

	// ==================== Output Schema Strategy ====================

	/**
	 * Example 4: Using outputType - ContactInfo
	 */
	public static void outputTypeContactInfo() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("contact_extractor")
				.model(chatModel)
				.outputType(ContactInfo.class)
				.saver(new MemorySaver())
				.build();

		AssistantMessage result = agent.call(
				"Extract contact information from the following: Zhang San, zhangsan@example.com, (555) 123-4567"
		);

		System.out.println(result.getText());
	}

	/**
	 * Example 5: Using outputType - ProductReview
	 */
	public static void outputTypeProductReview() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("review_analyzer")
				.model(chatModel)
				.outputType(ProductReview.class)
				.saver(new MemorySaver())
				.build();

		AssistantMessage result = agent.call(
				"Analyze the review: This product is great, 5-star rating. Fast delivery, but a bit pricey."
		);

		System.out.println(result.getText());
	}

	/**
	 * Example 6: Using outputType - TextAnalysis
	 */
	public static void outputTypeTextAnalysis() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("text_analyzer")
				.model(chatModel)
				.outputType(TextAnalysis.class)
				.saver(new MemorySaver())
				.build();

		AssistantMessage result = agent.call(
				"Analyze this text: Yesterday, Li Ming attended Alibaba's tech conference in Beijing and felt the power of innovation."
		);

		System.out.println(result.getText());
	}

	// ==================== Output Type Strategy ====================

	/**
	 * Example 7: Try-Catch Pattern
	 */
	public static void tryCatchPattern() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("data_extractor")
				.model(chatModel)
				.outputType(ContactInfo.class)
				.build();

		try {
			AssistantMessage result = agent.call("Extract data");
			ObjectMapper mapper = new ObjectMapper();
			ContactInfo data = mapper.readValue(result.getText(), ContactInfo.class);
			// Process data
			System.out.println("Name: " + data.getName());
		}
		catch (JsonProcessingException | GraphRunnerException e) {
			System.err.println("JSON parsing failed: " + e.getMessage());
			// Fallback handling
		}
	}

	/**
	 * Example 8: Validation Pattern
	 */
	public static void validationPattern() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("validated_agent")
				.model(chatModel)
				.outputType(ValidatedOutput.class)
				.build();

		try {
			AssistantMessage result = agent.call("Generate review");
			ObjectMapper mapper = new ObjectMapper();
			ValidatedOutput output = mapper.readValue(result.getText(), ValidatedOutput.class);
			output.validate();  // Throws exception if invalid
			System.out.println("Valid output: " + output.getTitle());
		}
		catch (Exception e) {
			System.err.println("Validation failed: " + e.getMessage());
		}
	}

	/**
	 * Example 9: Retry Pattern
	 */
	public static void retryPattern() {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		ReactAgent agent = ReactAgent.builder()
				.name("retry_agent")
				.model(chatModel)
				.outputType(ContactInfo.class)
				.build();

		int maxRetries = 3;
		ContactInfo data = null;
		ObjectMapper mapper = new ObjectMapper();

		for (int i = 0; i < maxRetries; i++) {
			try {
				AssistantMessage result = agent.call("Extract data");
				data = mapper.readValue(result.getText(), ContactInfo.class);
				break;  // Success
			}
			catch (Exception e) {
				if (i == maxRetries - 1) {
					throw new RuntimeException("Failed after multiple attempts", e);
				}
				System.out.println("Attempt " + (i + 1) + " attempt failed, retrying...");
			}
		}

		if (data != null) {
			System.out.println("Successfully extracted: " + data.getName());
		}
	}

	// ==================== Error Handling ====================

	/**
	 * Example 10: Complete Structured Output Example
	 */
	public static void comprehensiveExample() throws GraphRunnerException {
		DashScopeApi dashScopeApi = DashScopeApi.builder()
				.apiKey(System.getenv("AI_DASHSCOPE_API_KEY"))
				.build();

		ChatModel chatModel = DashScopeChatModel.builder()
				.dashScopeApi(dashScopeApi)
				.build();

		// Usage outputType
		ReactAgent typeAgent = ReactAgent.builder()
				.name("type_agent")
				.model(chatModel)
				.outputType(ContactInfo.class)
				.saver(new MemorySaver())
				.build();

		// Usage outputSchema (generated via BeanOutputConverter)
		BeanOutputConverter<ContactInfo> outputConverter = new BeanOutputConverter<>(ContactInfo.class);
		String format = outputConverter.getFormat();

		ReactAgent schemaAgent = ReactAgent.builder()
				.name("schema_agent")
				.model(chatModel)
				.outputSchema(format)
				.saver(new MemorySaver())
				.build();

		String input = "Contact: Wang Wu, wangwu@example.com, 13800138000";

		// Usage outputType
		AssistantMessage typeResult = typeAgent.call(input);
		System.out.println("Type-based: " + typeResult.getText());

		// Usage outputSchema
		AssistantMessage schemaResult = schemaAgent.call(input);
		System.out.println("Schema-based: " + schemaResult.getText());
	}

	public static void main(String[] args) {
		System.out.println("=== Structured Output Tutorial Examples ===");
		System.out.println("Note: AI_DASHSCOPE_API_KEY environment variable must be set\n");

		try {
			System.out.println("\n--- Example 1: Basic JSON Schema ---");
			basicJsonSchema();

			System.out.println("\n--- Example 2: Complex Nested Schema ---");
			complexNestedSchema();

			System.out.println("\n--- Example 3: Structured Analysis Schema ---");
			structuredAnalysisSchema();

			System.out.println("\n--- Example 4: OutputType - Contact Info ---");
			outputTypeContactInfo();

			System.out.println("\n--- Example 5: OutputType - Product Review ---");
			outputTypeProductReview();

			System.out.println("\n--- Example 6: OutputType - Text Analysis ---");
			outputTypeTextAnalysis();

			System.out.println("\n--- Example 7: Try-Catch Pattern ---");
			tryCatchPattern();

			System.out.println("\n--- Example 8: Validation Pattern ---");
			validationPattern();

			System.out.println("\n--- Example 9: Retry Pattern ---");
			retryPattern();

			System.out.println("\n--- Example 10: Comprehensive Example ---");
			comprehensiveExample();

			System.out.println("\n=== All examples executed successfully ===");
		}
		catch (Exception e) {
			System.err.println("Error executing example: " + e.getMessage());
			e.printStackTrace();
		}
	}

	/**
	 * Contact Information Output Class
	 */
	public static class ContactInfo {
		private String name;
		private String email;
		private String phone;

		// Getters and Setters
		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getEmail() {
			return email;
		}

		public void setEmail(String email) {
			this.email = email;
		}

		public String getPhone() {
			return phone;
		}

		public void setPhone(String phone) {
			this.phone = phone;
		}
	}

	/**
	 * Product Review Output Class
	 */
	public static class ProductReview {
		private int rating;
		private String sentiment;
		private String[] keyPoints;
		private ReviewDetails details;

		public int getRating() {
			return rating;
		}

		public void setRating(int rating) {
			this.rating = rating;
		}

		public String getSentiment() {
			return sentiment;
		}

		public void setSentiment(String sentiment) {
			this.sentiment = sentiment;
		}

		public String[] getKeyPoints() {
			return keyPoints;
		}

		public void setKeyPoints(String[] keyPoints) {
			this.keyPoints = keyPoints;
		}

		public ReviewDetails getDetails() {
			return details;
		}

		public void setDetails(ReviewDetails details) {
			this.details = details;
		}

		public static class ReviewDetails {
			private String[] pros;
			private String[] cons;

			public String[] getPros() {
				return pros;
			}

			public void setPros(String[] pros) {
				this.pros = pros;
			}

			public String[] getCons() {
				return cons;
			}

			public void setCons(String[] cons) {
				this.cons = cons;
			}
		}
	}

	// ==================== Comprehensive Example ====================

	/**
	 * Text Analysis Output Class
	 */
	public static class TextAnalysis {
		private String summary;
		private String[] keywords;
		private String sentiment;
		private Entities entities;

		public String getSummary() {
			return summary;
		}

		public void setSummary(String summary) {
			this.summary = summary;
		}

		public String[] getKeywords() {
			return keywords;
		}

		public void setKeywords(String[] keywords) {
			this.keywords = keywords;
		}

		public String getSentiment() {
			return sentiment;
		}

		public void setSentiment(String sentiment) {
			this.sentiment = sentiment;
		}

		public Entities getEntities() {
			return entities;
		}

		public void setEntities(Entities entities) {
			this.entities = entities;
		}

		public static class Entities {
			private String[] persons;
			private String[] locations;
			private String[] organizations;

			public String[] getPersons() {
				return persons;
			}

			public void setPersons(String[] persons) {
				this.persons = persons;
			}

			public String[] getLocations() {
				return locations;
			}

			public void setLocations(String[] locations) {
				this.locations = locations;
			}

			public String[] getOrganizations() {
				return organizations;
			}

			public void setOrganizations(String[] organizations) {
				this.organizations = organizations;
			}
		}
	}

	// ==================== Main Method ====================

	/**
	 * Validated Output Class
	 */
	public static class ValidatedOutput {
		private String title;
		private Integer rating;

		public void validate() throws IllegalArgumentException {
			if (title == null || title.isEmpty()) {
				throw new IllegalArgumentException("Title cannot be empty");
			}
			if (rating != null && (rating < 1 || rating > 5)) {
				throw new IllegalArgumentException("Rating must be between 1 and 5");
			}
		}

		// Getter and Setter methods
		public String getTitle() {
			return title;
		}

		public void setTitle(String title) {
			this.title = title;
		}

		public Integer getRating() {
			return rating;
		}

		public void setRating(Integer rating) {
			this.rating = rating;
		}
	}
}

