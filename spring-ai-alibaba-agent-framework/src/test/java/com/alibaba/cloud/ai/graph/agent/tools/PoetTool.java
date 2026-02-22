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
package com.alibaba.cloud.ai.graph.agent.tools;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.function.FunctionToolCallback;

import java.util.function.BiFunction;

public class PoetTool implements BiFunction<String, ToolContext, String> {
	public int count = 0;

	public PoetTool() {
	}

	@Override
	public String apply(
			@ToolParam(description = "The original user query that triggered this tool call") String originalUserQuery,
			ToolContext toolContext) {
		count++;
		System.out.println("Poet tool called : " + originalUserQuery);
		return "In the gaps of the city, \n" + "A beam of light sprouts quietly, \n" + "Through the silence of reinforced concrete,\n" + "Talk softly in the wind.\n" + "\n" + "The night is like ink, but no longer dark, \n"
				+ "Stars light up every corner, \n" + "I stand on the edge of time,\n" + "Wait for a cloud to fall gently";
	}

	public static ToolCallback createPoetToolCallback() {
		return FunctionToolCallback.builder("poem", new PoetTool())
				.description("Tools for writing poetry")
				.inputType(String.class)
				.build();
	}

	public static ToolCallback createPoetToolCallback(String name, PoetTool poetTool) {
		return FunctionToolCallback.builder(name, poetTool)
				.description("Tools for writing poetry")
				.inputType(String.class)
				.build();
	}

}
