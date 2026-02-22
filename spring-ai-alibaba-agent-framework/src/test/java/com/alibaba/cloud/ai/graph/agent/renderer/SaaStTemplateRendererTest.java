/*
 * Copyright 2024-2025 the original author or authors.
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
package com.alibaba.cloud.ai.graph.agent.renderer;

import org.junit.jupiter.api.Test;
import org.springframework.ai.template.ValidationMode;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link SaaStTemplateRenderer}.
 *
 * @author Spring AI Alibaba
 */
class SaaStTemplateRendererTest {

	@Test
	void testBasicCharDelimiter() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiterToken('{')
				.endDelimiterToken('}')
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "Hello {name}!";
		Map<String, Object> variables = Map.of("name", "World");

		String result = renderer.apply(template, variables);
		assertEquals("Hello World!", result);
	}

	@Test
	void testStringDelimiter() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "Hello {{name}}!";
		Map<String, Object> variables = Map.of("name", "World");

		String result = renderer.apply(template, variables);
		assertEquals("Hello World!", result);
	}

	@Test
	void testJsonContentWithStringDelimiter() {
		//Test using multi-character delimiter to avoid conflicts with JSON content
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = """
				Please process the following JSON data: {"name": "test", "value": 123}
				User information: {{userName}}
				Data content: {{jsonData}}
				""";

		Map<String, Object> variables = Map.of(
				"userName", "Zhang San",
				"jsonData", "{\"key\": \"value\"}"
		);

		String result = renderer.apply(template, variables);

		//Verify that {} in JSON has not been replaced
		assertTrue(result.contains("{\"name\": \"test\", \"value\": 123}"));
		//Verify template variables are replaced correctly
		assertTrue(result.contains("User information: Zhang San"));
		assertTrue(result.contains("Data content: {\"key\": \"value\"}"));
	}

	@Test
	void testJsonContentConflictWithSingleCharDelimiter() {
		//Test for single-character delimiter conflicts with JSON content
		//Implementations should be able to automatically identify and protect JSON content to avoid conflicts with template variables
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiterToken('{')
				.endDelimiterToken('}')
				.validationMode(ValidationMode.NONE)
				.build();

		String template = """
				Please process the following JSON data: {"name": "test", "value": 123}
				User information: {userName}
				""";

		Map<String, Object> variables = Map.of("userName", "Zhang San");

		//Implementations should be able to recognize JSON content and protect it while properly replacing template variables
		String result = renderer.apply(template, variables);

		//Verify that the JSON content is protected (not replaced by mistake)
		assertTrue(result.contains("{\"name\": \"test\", \"value\": 123}"));
		//Verify template variables are replaced correctly
		assertTrue(result.contains("User information: Zhang San"));
	}

	@Test
	void testComplexJsonWithStringDelimiter() {
		//Test complex JSON content mixed with template variables
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = """
				{
				  "request": {
				    "user": "{{userName}}",
				    "data": {"key": "value", "count": {{count}}},
				    "metadata": {"type": "test", "nested": {"level": 2}}
				  },
				  "response": "{{responseText}}"
				}
				""";

		Map<String, Object> variables = Map.of(
				"userName", "Alice",
				"count", "42",
				"responseText", "Success"
		);

		String result = renderer.apply(template, variables);

		//Verify variables are replaced correctly
		assertTrue(result.contains("\"user\": \"Alice\""));
		assertTrue(result.contains("\"count\": 42"));
		assertTrue(result.contains("\"response\": \"Success\""));
		//Verify that ordinary {} in the JSON structure has not been replaced by mistake
		assertTrue(result.contains("\"metadata\": {\"type\": \"test\""));
	}

	@Test
	void testNestedDelimiters() {
		//Test nested delimiters
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "Outer: {{outer}}, Inner JSON: {\"key\": \"{{value}}\"}";
		Map<String, Object> variables = Map.of(
				"outer", "OUTER_VALUE",
				"value", "VALUE"
		);

		String result = renderer.apply(template, variables);
		assertTrue(result.contains("Outer: OUTER_VALUE"));
		assertTrue(result.contains("\"key\": \"VALUE\""));
	}

	@Test
	void testPropertyAccess() {
		//Test property access
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		Map<String, Object> user = Map.of("name", "John", "age", 30);
		String template = "User: {{user.name}}, Age: {{user.age}}";
		Map<String, Object> variables = Map.of("user", user);

		String result = renderer.apply(template, variables);
		assertTrue(result.contains("User: John"));
		assertTrue(result.contains("Age: 30"));
	}

	@Test
	void testMultipleVariables() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "{{greeting}} {{name}}, today is {{day}}";
		Map<String, Object> variables = Map.of(
				"greeting", "Hello",
				"name", "Alice",
				"day", "Monday"
		);

		String result = renderer.apply(template, variables);
		assertEquals("Hello Alice, today is Monday", result);
	}

	@Test
	void testValidationModeThrow() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.THROW)
				.build();

		String template = "Hello {{name}}, missing: {{missing}}";
		Map<String, Object> variables = Map.of("name", "World");

		assertThrows(IllegalStateException.class, () -> {
			renderer.apply(template, variables);
		});
	}

	@Test
	void testValidationModeWarn() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.WARN)
				.build();

		String template = "Hello {{name}}, missing: {{missing}}";
		Map<String, Object> variables = Map.of("name", "World");

		// Should not throw, but log warning
		String result = renderer.apply(template, variables);
		assertTrue(result.contains("Hello World"));
	}

	@Test
	void testValidationModeNone() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "Hello {{name}}, missing: {{missing}}";
		Map<String, Object> variables = Map.of("name", "World");

		// Should not throw
		String result = renderer.apply(template, variables);
		assertTrue(result.contains("Hello World"));
	}

	@Test
	void testJsonArrayWithStringDelimiter() {
		//Testing a case containing a JSON array
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = """
				{
				  "items": [{"id": 1, "name": "item1"}, {"id": 2, "name": "item2"}],
				  "user": "{{userName}}",
				  "count": {{count}}
				}
				""";

		Map<String, Object> variables = Map.of(
				"userName", "Bob",
				"count", "10"
		);

		String result = renderer.apply(template, variables);

		//Verify that {} in the JSON array has not been replaced by mistake
		assertTrue(result.contains("\"items\": [{\"id\": 1"));
		//Verify template variables are replaced correctly
		assertTrue(result.contains("\"user\": \"Bob\""));
		assertTrue(result.contains("\"count\": 10"));
	}

	@Test
	void testMixedContent() {
		//Test mixed content: both JSON, normal text, and template variables
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = """
				User {{userName}} submitted the following data:
				{"type": "request", "data": {"key": "value"}}
				Processing result: {{result}}
				Time: {{timestamp}}
				""";

		Map<String, Object> variables = Map.of(
				"userName", "Charlie",
				"result", "Success",
				"timestamp", "2024-01-01"
		);

		String result = renderer.apply(template, variables);

		//Verify that all variables are replaced
		assertTrue(result.contains("User Charlie submitted the following data:"));
		assertTrue(result.contains("Processing result: Success"));
		assertTrue(result.contains("Time: 2024-01-01"));
		//Verify that the JSON content remains unchanged
		assertTrue(result.contains("{\"type\": \"request\""));
	}

	@Test
	void testBuilderWithCharDelimiters() {
		// test Builder use char delimiter
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiterToken('<')
				.endDelimiterToken('>')
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "Hello <name>!";
		Map<String, Object> variables = Map.of("name", "World");

		String result = renderer.apply(template, variables);
		assertEquals("Hello World!", result);
	}

	@Test
	void testBuilderWithStringDelimiters() {
		// test Builder use String delimiter
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("<<")
				.endDelimiter(">>")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "Hello <<name>>!";
		Map<String, Object> variables = Map.of("name", "World");

		String result = renderer.apply(template, variables);
		assertEquals("Hello World!", result);
	}

	@Test
	void testEmptyTemplate() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "";
		Map<String, Object> variables = Map.of();

		assertThrows(IllegalArgumentException.class, () -> {
			renderer.apply(template, variables);
		});
	}

	@Test
	void testNullVariables() {
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = "Hello {{name}}!";

		assertThrows(IllegalArgumentException.class, () -> {
			renderer.apply(template, null);
		});
	}

	@Test
	void testComplexNestedJson() {
		//Test complex nested JSON structures
		SaaStTemplateRenderer renderer = SaaStTemplateRenderer.builder()
				.startDelimiter("{{")
				.endDelimiter("}}")
				.validationMode(ValidationMode.NONE)
				.build();

		String template = """
				{
				  "level1": {
				    "level2": {
				      "level3": {
				        "value": "{{value}}",
				        "items": [{"a": 1}, {"b": 2}]
				      }
				    },
				    "user": "{{userName}}"
				  }
				}
				""";

		Map<String, Object> variables = Map.of(
				"value", "test",
				"userName", "User"
		);

		String result = renderer.apply(template, variables);

		//Verify that deeply nested variables are replaced
		assertTrue(result.contains("\"value\": \"test\""));
		assertTrue(result.contains("\"user\": \"User\""));
		//Verify that the JSON structure is complete
		assertTrue(result.contains("\"level3\": {"));
		assertTrue(result.contains("\"items\": [{\"a\": 1}"));
	}

}

