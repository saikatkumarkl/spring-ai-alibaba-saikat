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

package com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.nodedata;

import com.alibaba.cloud.ai.studio.admin.builder.generator.model.Variable;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.VariableSelector;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.VariableType;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.NodeData;

import java.util.List;

/**
 * @author vlsmb
 * @since 2025/7/21
 */
public class IterationNodeData extends NodeData {

	public static List<Variable> getDefaultOutputSchemas() {
		return List.of(new Variable("state", VariableType.ARRAY_NUMBER), // Remaining unprocessed element indexes
				new Variable("index", VariableType.NUMBER), // Iteration index
				new Variable("isFinished", VariableType.BOOLEAN) // Whether iteration is finished
		);
	}

	// Source node name for this NodeData
	private final String sourceVarName;

	private int parallelCount = 1;

	private int maxIterationCount = Integer.MAX_VALUE;

	// Dify uses 0-based iteration index; Studio uses 1-based, so adjust with this offset
	private int indexOffset = 0;

	// itemKey/outputKey suffixes are fixed in Dify but customizable in Studio
	private String itemKey = "item";

	private String outputKey = "output";

	// Selector for iteration input
	private VariableSelector inputSelector;

	// Selector for iteration result elements
	private VariableSelector resultSelector;

	public IterationNodeData(IterationNodeData other) {
		parallelCount = other.parallelCount;
		maxIterationCount = other.maxIterationCount;
		indexOffset = other.indexOffset;
		itemKey = other.itemKey;
		outputKey = other.outputKey;
		inputSelector = other.inputSelector;
		resultSelector = other.resultSelector;
		sourceVarName = other.getVarName();
		setVarName(other.getVarName());
	}

	public IterationNodeData() {
		super();
		sourceVarName = null;
	}

	public String getSourceVarName() {
		return sourceVarName;
	}

	public int getParallelCount() {
		return parallelCount;
	}

	public void setParallelCount(int parallelCount) {
		this.parallelCount = parallelCount;
	}

	public int getMaxIterationCount() {
		return maxIterationCount;
	}

	public void setMaxIterationCount(int maxIterationCount) {
		this.maxIterationCount = maxIterationCount;
	}

	public int getIndexOffset() {
		return indexOffset;
	}

	public void setIndexOffset(int indexOffset) {
		this.indexOffset = indexOffset;
	}

	public String getItemKey() {
		return itemKey;
	}

	public void setItemKey(String itemKey) {
		this.itemKey = itemKey;
	}

	public String getOutputKey() {
		return outputKey;
	}

	public void setOutputKey(String outputKey) {
		this.outputKey = outputKey;
	}

	public VariableSelector getInputSelector() {
		return inputSelector;
	}

	public void setInputSelector(VariableSelector inputSelector) {
		this.inputSelector = inputSelector;
	}

	public VariableSelector getResultSelector() {
		return resultSelector;
	}

	public void setResultSelector(VariableSelector resultSelector) {
		this.resultSelector = resultSelector;
	}

}
