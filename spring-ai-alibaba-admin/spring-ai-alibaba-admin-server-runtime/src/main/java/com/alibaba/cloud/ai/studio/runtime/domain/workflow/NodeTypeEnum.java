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
package com.alibaba.cloud.ai.studio.runtime.domain.workflow;

import lombok.Getter;

@Getter
public enum NodeTypeEnum {

	START("Start", "start node"), INPUT("Input", "input node"), OUTPUT("Output", "Output node"),
	VARIABLE_ASSIGN("VariableAssign", "Variable assignment node"), VARIABLE_HANDLE("VariableHandle", "Variable processing node"),
	APP_CUSTOM("AppCustom", "Custom application node"), AGENT_GROUP("AgentGroup", "Agent group node"), SCRIPT("Script", "script node"),
	CLASSIFIER("Classifier", "Problem Classification Node"), LLM("LLM", "Large model node"), COMPONENT("AppComponent", "Application component node"),
	JUDGE("Judge", "Judgment node"), RETRIEVAL("Retrieval", "Knowledge base node"), API("API", "Api call node"), PLUGIN("Plugin", "Plug-in node"),
	MCP("MCP", "MCP node"), PARAMETER_EXTRACTOR("ParameterExtractor", "Parameter extraction node"),
	ITERATOR_START("IteratorStart", "loop body start node"), ITERATOR("Iterator", "loop node"), ITERATOR_END("IteratorEnd", "End node of loop body"),
	PARALLEL_START("ParallelStart", "Batch processing start node"), PARALLEL("Parallel", "batch processing node"), PARALLEL_END("ParallelEnd", "Batch end node"),
	END("End", "end node");

	private final String code;

	private final String desc;

	NodeTypeEnum(String code, String desc) {
		this.code = code;
		this.desc = desc;
	}

}
