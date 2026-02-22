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

public class ReviewerTool implements BiFunction<String, ToolContext, String> {
	public int count = 0;

	public ReviewerTool() {
	}

	@Override
	public String apply(
			@ToolParam(description = "The poem or article that needs to be reviewed.") String article,
			ToolContext toolContext) {
		count++;
		System.out.println("Reviewer tool called : " + article);
		return "The morning light is beginning to shine through, and the mist is like a gauze, gently covering the surface of the West Lake.The shadow of the broken bridge reflects in the water, willow silk ruffles the waves, and dewdrops hang on the grass tips, about to fall but not yet.The mountains in the distance are empty, and the boat gently moves, opening a pool of green glass.Wherever the wind passes, the fragrance of lotus is carried secretly, and the fallen leaves swirl gently, as if whispering the secrets of the years.It turns out that the most beautiful thing in the world is the silence and poetry of the West Lake for a moment.";
	}

	public static ToolCallback createReviewerToolCallback() {
		return FunctionToolCallback.builder("reviewer", new ReviewerTool())
				.description("Tools for commenting or revising poetry and prose")
				.inputType(String.class)
				.build();
	}

	public static ToolCallback createReviewerToolCallback(String name, ReviewerTool reviewerTool) {
		return FunctionToolCallback.builder(name, reviewerTool)
				.description("Tools for commenting or revising poetry and prose")
				.inputType(String.class)
				.build();
	}

}
