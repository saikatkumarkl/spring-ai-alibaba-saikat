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

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.alibaba.cloud.ai.studio.admin.builder.generator.model.VariableType;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.Case;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.Edge;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.Node;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.NodeType;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.nodedata.BranchNodeData;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.VariableSelector;
import com.alibaba.cloud.ai.studio.admin.builder.generator.service.generator.workflow.NodeSection;

import org.springframework.stereotype.Component;

@Component
public class BranchNodeSection implements NodeSection<BranchNodeData> {

	@Override
	public boolean support(NodeType nodeType) {
		return NodeType.BRANCH.equals(nodeType);
	}

	@Override
	public String render(Node node, String varName) {
		String id = node.getId();

		StringBuilder sb = new StringBuilder();
		sb.append(String.format("// —— BranchNode [%s] ——%n", id));
		//The conditional judgment is on the conditional edge, and this node is an empty node.
		sb.append(String.format("stateGraph.addNode(\"%s\", AsyncNodeAction.node_async(state -> Map.of()));%n%n",
				varName));

		return sb.toString();
	}

	@Override
	public String renderEdges(BranchNodeData branchNodeData, List<Edge> edges) {
		//It is stipulated here that the sourceHandle of Edge is caseId. The previous conversion needs to comply with this rule.
		String srcVar = branchNodeData.getVarName();
		StringBuilder sb = new StringBuilder();
		List<Case> cases = branchNodeData.getCases();

		//Maintain a mapping from caseId to caseName
		AtomicInteger count = new AtomicInteger(1);
		Map<String, String> caseIdToName = cases.stream()
			.map(Case::getId)
			.collect(Collectors.toUnmodifiableMap(id -> id, id -> {
				//If the caseId of some nodes itself has meaning, use it directly
				if (id.equalsIgnoreCase("default") || id.equalsIgnoreCase("true") || id.equalsIgnoreCase("false")) {
					return id;
				}
				return "case_" + (count.getAndIncrement());
			}));

		//Construct the EdgeAction.apply function
		StringBuilder conditionsBuffer = new StringBuilder();
		for (Case c : cases) {
			String logicalOperator = " " + c.getLogicalOperator().getCodeValue() + " ";
			List<String> expressions = c.getConditions().stream().map(condition -> {
				String constValue = condition.getValue();
				if (condition.getReferenceValue() != null && (VariableType.STRING.equals(condition.getVarType())
						|| VariableType.FILE.equals(condition.getVarType()))) {
					constValue = "\"" + constValue + "\"";
				}

				//Generate secure access codes based on variable type
				String objName = generateSafeVariableAccess(condition);
				return condition.getComparisonOperator().convert(objName, constValue);
			}).toList();
			conditionsBuffer.append("if(");
			//Combining compound conditions
			conditionsBuffer.append(String.join(logicalOperator, expressions));
			conditionsBuffer.append(") {\n");
			conditionsBuffer.append(String.format("return \"%s\";", caseIdToName.get(c.getId())));
			conditionsBuffer.append("}\n");
		}
		//Finally, you need to add the result of else
		conditionsBuffer.append(String.format("return \"%s\";", branchNodeData.getDefaultCase()));

		//Build Map
		Map<String, String> edgeCaseMap = edges.stream()
			.collect(Collectors.toMap(e -> caseIdToName.getOrDefault(e.getSourceHandle(), e.getSourceHandle()),
					Edge::getTarget));
		String edgeCaseMapStr = "Map.of(" + edgeCaseMap.entrySet()
			.stream()
			.flatMap(e -> Stream.of(e.getKey(), e.getValue()))
			.map(v -> String.format("\"%s\"", v))
			.collect(Collectors.joining(", ")) + ")";

		//Build final code
		sb.append("stateGraph.addConditionalEdges(\"")
			.append(srcVar)
			.append("\", edge_async(state -> {\n")
			.append(conditionsBuffer)
			.append("}), ")
			.append(edgeCaseMapStr)
			.append(");\n\n");

		return sb.toString();
	}

	private String generateSafeVariableAccess(Case.Condition condition) {
		VariableType varType = condition.getVarType();
		String variablePath = buildVariablePath(condition);

		switch (varType) {
			case FILE:
				//Support getting property path from VariableSelector
				VariableSelector selector = condition.getTargetSelector();
				boolean accessExtension = selector != null
						&& (selector.getLabel() != null && selector.getLabel().contains("extension")
								|| selector.getName() != null && selector.getName().contains("extension"));

				if (accessExtension) {
					//If you are accessing the extension attribute, access the extension field directly.
					return String.format("state.value(\"%s\", String.class).orElse(null)", variablePath);
				}
				else {
					//Extract extension from file object
					return String.format(
							"state.value(\"%s\", java.io.File.class).map(file -> { " + "String name = file.getName(); "
									+ "int dotIndex = name.lastIndexOf('.'); "
									+ "return dotIndex > 0 ? name.substring(dotIndex) : \"\"; " + "}).orElse(null)",
							variablePath);
				}
				//Returns null by default to avoid isNull judgment being always false.
			case STRING:
				return String.format("state.value(\"%s\", String.class).orElse(null)", variablePath);
			case NUMBER:
				return String.format("state.value(\"%s\", Number.class).orElse(null)", variablePath);
			case BOOLEAN:
				return String.format("state.value(\"%s\", Boolean.class).orElse(null)", variablePath);
			case ARRAY_FILE:
			case ARRAY_NUMBER:
			case ARRAY_STRING:
			case ARRAY_OBJECT:
			case ARRAY_BOOLEAN:
			case ARRAY:
				return String.format("state.value(\"%s\", List.class).orElse(null)", variablePath);
			case OBJECT:
				return String.format("state.value(\"%s\", Object.class).orElse(null)", variablePath);
			default:
				//Use default type
				return String.format("state.value(\"%s\", Object.class).orElse(null)", variablePath);
		}
	}

	private String buildVariablePath(Case.Condition condition) {
		VariableSelector variableSelector = condition.getTargetSelector();
		if (variableSelector == null) {
			return "unknown";
		}
		return Optional.ofNullable(variableSelector.getNameInCode()).orElse("unknown");
	}

	@Override
	public List<String> getImports() {
		return List.of("static com.alibaba.cloud.ai.graph.action.AsyncEdgeAction.edge_async");
	}

}
