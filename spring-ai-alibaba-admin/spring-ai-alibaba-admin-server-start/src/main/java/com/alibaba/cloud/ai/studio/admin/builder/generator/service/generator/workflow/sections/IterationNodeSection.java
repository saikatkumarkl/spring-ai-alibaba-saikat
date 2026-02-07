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

package com.alibaba.cloud.ai.studio.admin.builder.generator.service.generator.workflow.sections;

import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.Edge;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.Node;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.NodeType;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.nodedata.IterationNodeData;
import com.alibaba.cloud.ai.studio.admin.builder.generator.service.dsl.DSLDialectType;
import com.alibaba.cloud.ai.studio.admin.builder.generator.service.generator.workflow.NodeSection;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * @author vlsmb
 * @since 2025/7/23
 */
//TODO: Support parallel mode, error handling, support Studio’s default input value, and support Studio’s multiple input/multiple output
@Component
public class IterationNodeSection implements NodeSection<IterationNodeData> {

	@Override
	public boolean support(NodeType nodeType) {
		return NodeType.ITERATION.equals(nodeType);
	}

	@Override
	public String render(Node node, String varName) {
		//The iteration node has been split into multiple nodes when converted to Workflow, so this method returns empty
		return "";
	}

	@Override
	public String renderEdges(IterationNodeData nodeData, List<Edge> edges) {
		return "";
	}

	@Override
	public List<String> getImports() {
		return List.of("java.util.ArrayList", "java.util.Arrays");
	}

	//It is specified that the start of the iteration node is iterationVarName_start and the end is iterationVarName_end.

	@Component
	public static class IterationStartNodeSection implements NodeSection<IterationNodeData> {

		@Override
		public boolean support(NodeType nodeType) {
			return NodeType.ITERATION_START.equals(nodeType);
		}

		@Override
		public String render(Node node, String varName) {
			IterationNodeData nodeData = ((IterationNodeData) node.getData());
			return String.format("""
					// Iteration [%s] Start Node
					stateGraph.addNode("%s", AsyncNodeAction.node_async(
					    createIterationStartAction("%s", "%s", "%s", "%s", "%s", %d)
					));

					""", node.getId(), varName, nodeData.getInputSelector().getNameInCode(),
					nodeData.getSourceVarName() + "_state", nodeData.getItemKey(),
					nodeData.getSourceVarName() + "_index", nodeData.getSourceVarName() + "_isFinished",
					nodeData.getIndexOffset());
		}

		//TODO: Add auxiliary nodes to support iteration start node parallelism
		@Override
		public String renderEdges(IterationNodeData nodeData, List<Edge> edges) {
			Edge edge = edges.get(0);
			return String.format("""
					// Iteration [%s] Start Edge
					stateGraph.addConditionalEdges("%s", AsyncEdgeAction.edge_async(
					                state -> {
					                    Boolean b = state.value("%s", false);
					                    return b ? "end" : "iteration";
					                }
					        ), Map.of("end", "%s", "iteration", "%s"));

					""", nodeData.getSourceVarName(), nodeData.getSourceVarName() + "_start",
					nodeData.getSourceVarName() + "_isFinished", nodeData.getSourceVarName() + "_end",
					edge.getTarget());
		}

		@Override
		public String assistMethodCode(DSLDialectType dialectType) {
			return """
					private NodeAction createIterationStartAction(
					        String arrayKey, String stateKey,
					        String itemKey, String indexKey, String flagKey,
					        int indexOffset) {
					    return state -> {
					        Object arrayObj = state.value(arrayKey).orElse(List.of());
					        List<Integer> stateList = state.value(stateKey, List.class).orElse(null);

					        List<?> arrayList;
					        if (stateList == null) {
					            // the first time in iteration
					            if (arrayObj instanceof List<?>) {
					                arrayList = new ArrayList<>((List<?>) arrayObj);
					            } else if (arrayObj.getClass().isArray()) {
					                arrayList = new ArrayList<>(Arrays.stream((Object[])arrayObj).toList());
					            } else {
					                throw new IllegalStateException("value {" + arrayKey + "} is not an array!");
					            }
					            int len = arrayList.size();
					            stateList = new ArrayList<>();
					            for (int i = 0; i < len; i++) {
					                stateList.add(i);
					            }
					        } else {
					            arrayList = (List<?>) arrayObj;
					        }

					        if(stateList.isEmpty()) {
					            return Map.of(flagKey, true);
					        }
					        int index = stateList.get(0);
					        Object item = arrayList.get(index);
					        stateList.remove(0);
					        return Map.of(arrayKey, arrayList, stateKey, stateList, itemKey, item,
					                indexKey, index + indexOffset, flagKey, false);
					    };
					}
					""";
		}

		@Override
		public List<String> getImports() {
			return List.of("java.util.ArrayList", "java.util.Arrays");
		}

	}

	@Component
	public static class IterationEndNodeSection implements NodeSection<IterationNodeData> {

		@Override
		public boolean support(NodeType nodeType) {
			return NodeType.ITERATION_END.equals(nodeType);
		}

		@Override
		public String render(Node node, String varName) {
			IterationNodeData nodeData = ((IterationNodeData) node.getData());
			return String.format("""
					// Iteration [%s] End Node
					stateGraph.addNode("%s", AsyncNodeAction.node_async(
					    createIterationEndAction("%s", "%s", "%s")
					));

					""", nodeData.getSourceVarName(), varName, nodeData.getSourceVarName() + "_isFinished",
					nodeData.getResultSelector().getNameInCode(), nodeData.getOutputKey());
		}

		//TODO: Add auxiliary nodes to support iteration termination node parallelism
		@Override
		public String renderEdges(IterationNodeData nodeData, List<Edge> edges) {
			Edge edge = edges.get(0);
			return String.format("""
					// Iteration [%s] End Edge
					stateGraph.addConditionalEdges("%s", AsyncEdgeAction.edge_async(
					                state -> {
					                    Boolean b = state.value("%s", false);
					                    return b ? "finish" : "start";
					                }
					        ), Map.of("finish", "%s", "start", "%s"));

					""", nodeData.getSourceVarName(), nodeData.getVarName(),
					nodeData.getSourceVarName() + "_isFinished", edge.getTarget(),
					nodeData.getSourceVarName() + "_start");
		}

		@Override
		public String assistMethodCode(DSLDialectType dialectType) {
			return """
					 private NodeAction createIterationEndAction(String flagKey, String resultKey, String outputKey) {
					     return state -> {
					         boolean flag = state.value(flagKey, Boolean.class).orElse(true);
					         List<Object> outputList = state.value(outputKey, List.class).orElse(new ArrayList<>());
					         if(flag) {
					             return Map.of(outputKey, outputList);
					         }
					         outputList.add(state.value(resultKey).orElseThrow());
					         return Map.of(outputKey, outputList);
					     };
					 }
					""";
		}

		@Override
		public List<String> getImports() {
			return List.of("java.util.ArrayList", "java.util.Arrays");
		}

	}

}
