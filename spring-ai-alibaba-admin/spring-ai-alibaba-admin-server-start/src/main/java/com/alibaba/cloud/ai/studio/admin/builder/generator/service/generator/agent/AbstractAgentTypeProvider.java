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
package com.alibaba.cloud.ai.studio.admin.builder.generator.service.generator.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.alibaba.cloud.ai.studio.admin.builder.generator.utils.CodeGenUtils.*;

/**
 * The abstract base class of AgentTypeProvider provides common verification logic and rendering tools.
 *
 * @author yHong
 * @version 1.0
 * @since 2025/9/8 18:31
 */
public abstract class AbstractAgentTypeProvider implements AgentTypeProvider {

	/**
	 * Provides a default verification implementation, which can be overridden by subclasses to add specific verification logic.
	 */
	@Override
	public void validateDSL(Map<String, Object> root) {
		//Basic validation: Check required fields
		if (root == null) {
			throw new IllegalArgumentException(type() + " requires valid configuration");
		}

		String name = (String) root.get("name");
		if (isBlank(name)) {
			throw new IllegalArgumentException(type() + " requires 'name' field");
		}

		//Call subclass-specific verification logic
		validateSpecific(root);
	}

	/**
	 * Subclasses implement specific verification logic
	 * @param root DSL root object
	 */
	protected abstract void validateSpecific(Map<String, Object> root);

	/**
	 * Verify whether handle exists
	 * @param root DSL root object
	 * @return handle Map
	 */
	@SuppressWarnings("unchecked")
	protected Map<String, Object> requireHandle(Map<String, Object> root) {
		Map<String, Object> handle = (Map<String, Object>) root.get("handle");
//		if (handle == null) {
//			throw new IllegalArgumentException(type() + " requires 'handle' configuration");
//		}
		if (handle == null) {
			handle = new HashMap<>();
		}
		return handle;
	}

	/**
	 * Verification must have subagent
	 * @param root DSL root object
	 * @param minCount minimum quantity
	 */
	@SuppressWarnings("unchecked")
	protected List<Map<String, Object>> requireSubAgents(Map<String, Object> root, int minCount) {
		Object subs = root.get("sub_agents");
		if (!(subs instanceof List)) {
			throw new IllegalArgumentException(type() + " requires 'sub_agents' (array)");
		}
		List<Map<String, Object>> subAgents = (List<Map<String, Object>>) subs;
		if (subAgents.size() < minCount) {
			throw new IllegalArgumentException(
					type() + " requires at least " + minCount + " sub-agent(s), got: " + subAgents.size());
		}
		return subAgents;
	}

	/**
	 * Check numeric field
	 * @param value field value
	 * @param fieldName field name
	 * @param minValue minimum value (inclusive)
	 * @return value
	 */
	protected int requirePositiveNumber(Object value, String fieldName, int minValue) {
		if (value == null) {
			throw new IllegalArgumentException(type() + " requires '" + fieldName + "'");
		}
		if (!(value instanceof Number)) {
			throw new IllegalArgumentException(fieldName + " must be a number");
		}
		int num = ((Number) value).intValue();
		if (num < minValue) {
			throw new IllegalArgumentException(fieldName + " must be at least " + minValue + ", got: " + num);
		}
		return num;
	}

	/**
	 * Check if string is empty
	 */
	protected boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

	/**
	 * Check if there are valid input keys
	 */
	protected boolean hasValidInputKey(Map<String, Object> root) {
		String inputKey = (String) root.get("input_key");
		List<?> inputKeys = (List<?>) root.get("input_keys");
		return !isBlank(inputKey) || (inputKeys != null && !inputKeys.isEmpty());
	}

	/**
	 * Generate basic builder code (name, description, outputKey)
	 * @param builderName builder class name (such as "ReactAgent", "SequentialAgent")
	 * @param varName variable name
	 * @param shell Agent basic information
	 * @return generated code
	 */
	protected StringBuilder generateBasicBuilderCode(String builderName, String varName, AgentShell shell) {
		StringBuilder code = new StringBuilder();
		code.append(builderName)
			.append(" ")
			.append(varName)
			.append(" = ")
			.append(builderName)
			.append(".builder()\n")
			.append(".name(\"")
			.append(esc(shell.name()))
			.append("\")\n")
			.append(".description(\"")
			.append(esc(nvl(shell.description())))
			.append("\")\n");

		if (shell.outputKey() != null) {
			code.append(".outputKey(\"").append(esc(shell.outputKey())).append("\")\n");
		}

		return code;
	}

	/**
	 * Generate state strategy code todo: Each sub-agent currently rendered has its own state registration. You need to confirm whether the flowAgent's state is globally unified or sub-agent isolated.
	 * @param handle Agent handle configuration
	 * @param defaultMessagesStrategy The default value when the messages strategy is not defined (null means no default value is added)
	 * @return The generated status policy code and whether there is a message policy flag
	 */
	protected StateStrategyResult generateStateStrategyCode(Map<String, Object> handle,
			String defaultMessagesStrategy) {
		StringBuilder code = new StringBuilder();
		code.append(".state(() -> {\n").append("Map<String, KeyStrategy> strategies = new HashMap<>();\n");

		boolean hasMessagesStrategy = false;
		Object stateObj = handle.get("state");
		if (stateObj instanceof Map<?, ?> stateMap) {
			Object strategiesObj = stateMap.get("strategies");
			if (strategiesObj instanceof Map<?, ?> strategiesMap) {
				for (Map.Entry<?, ?> e : strategiesMap.entrySet()) {
					String k = String.valueOf(e.getKey());
					String v = String.valueOf(e.getValue());
					String strategyNew = (v != null && v.equalsIgnoreCase("append")) ? "new AppendStrategy()"
							: "new ReplaceStrategy()";
					code.append("strategies.put(\"").append(esc(k)).append("\", ").append(strategyNew).append(");\n");

					if ("messages".equals(k)) {
						hasMessagesStrategy = true;
					}
				}
			}
		}

		//Add default messages policy if needed
		if (!hasMessagesStrategy && defaultMessagesStrategy != null) {
			code.append("strategies.put(\"messages\", ").append(defaultMessagesStrategy).append(");\n");
		}

		code.append("return strategies;\n").append("})\n");

		return new StateStrategyResult(code.toString(), hasMessagesStrategy);
	}

	/**
	 * State strategy generation results
	 */
	protected static class StateStrategyResult {

		public final String code;

		public final boolean hasMessagesStrategy;

		public StateStrategyResult(String code, boolean hasMessagesStrategy) {
			this.code = code;
			this.hasMessagesStrategy = hasMessagesStrategy;
		}

	}

	/**
	 * Add subagent list
	 */
	protected void appendSubAgents(StringBuilder code, List<String> childVarNames) {
		if (childVarNames != null && !childVarNames.isEmpty()) {
			code.append(".subAgents(List.of(").append(String.join(", ", childVarNames)).append("))\n");
		}
	}

}
