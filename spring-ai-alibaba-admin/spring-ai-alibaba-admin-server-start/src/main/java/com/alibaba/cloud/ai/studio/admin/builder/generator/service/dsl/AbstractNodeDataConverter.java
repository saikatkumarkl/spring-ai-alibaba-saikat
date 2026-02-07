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
package com.alibaba.cloud.ai.studio.admin.builder.generator.service.dsl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.alibaba.cloud.ai.studio.admin.builder.generator.model.VariableSelector;
import com.alibaba.cloud.ai.studio.admin.builder.generator.model.workflow.NodeData;
import com.alibaba.cloud.ai.studio.admin.builder.generator.utils.MapReadUtil;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.google.common.base.Strings;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.util.StringUtils;

/**
 * AbstractNodeDataConverter defines the interface to convert node data using a
 * combination of dsl dialect and node types
 */
public abstract class AbstractNodeDataConverter<T extends NodeData> implements NodeDataConverter<T> {

	@Override
	public T parseMapData(Map<String, Object> data, DSLDialectType dialectType) {
		DialectConverter<T> converter = getDialectConverters().stream()
			.filter(c -> c.supportDialect(dialectType))
			.findFirst()
			.orElseThrow(() -> new NotImplementedException("Unsupported dialect type: " + dialectType.value()));
		try {
			return converter.parse(data);
		}
		catch (JsonProcessingException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public Map<String, Object> dumpMapData(T nodeData, DSLDialectType dialectType) {
		DialectConverter<T> converter = getDialectConverters().stream()
			.filter(c -> c.supportDialect(dialectType))
			.findFirst()
			.orElseThrow(() -> new NotImplementedException("Unsupported dialect type: " + dialectType.value()));
		return converter.dump(nodeData);
	}

	/**
	 * DialectConverter defines the interface to convert node data in different dsl
	 * dialects.
	 */
	public interface DialectConverter<T> {

		Boolean supportDialect(DSLDialectType dialectType);

		T parse(Map<String, Object> data) throws JsonProcessingException;

		Map<String, Object> dump(T nodeData);

		/**
		 * Convert template string to variable selector
		 * @param dialectType dsl language
		 * @param template template string
		 * @return variable selector
		 */
		default VariableSelector varTemplateToSelector(DSLDialectType dialectType, String template) {
			if (template == null) {
				throw new NullPointerException("Template string is null");
			}
			Pattern pattern = switch (dialectType) {
				case DIFY -> DIFY_VAR_TEMPLATE_PATTERN;
				case STUDIO -> STUDIO_VAR_TEMPLATE_PATTERN;
				default -> throw new UnsupportedOperationException();
			};
			Matcher matcher = pattern.matcher(template);
			MatchResult result = matcher.results()
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Invalid template string"));
			return new VariableSelector(result.group(1), result.group(2));
		}

		/**
		 * Get the model name from data (commonly used by LLMNode, ClassifierNode, etc.)
		 * @param dialectType dsl language
		 * @param data node data
		 * @return model name
		 */
		default String exactChatModelName(DSLDialectType dialectType, Map<String, Object> data) {
			return switch (dialectType) {
				case DIFY -> MapReadUtil.getMapDeepValue(data, String.class, "model", "name");
				case STUDIO -> {
					Map<String, Object> modeConfigMap = MapReadUtil.safeCastToMapWithStringKey(
							MapReadUtil.getMapDeepValue(data, Map.class, "config", "node_param", "model_config"));
					yield MapReadUtil.getMapDeepValue(modeConfigMap, String.class, "model_id");
				}
				default -> throw new UnsupportedOperationException();
			};
		}

		/**
		 * Obtain model parameters from data (commonly used by LLMNode, ClassifierNode, etc.)
		 * @param dialectType dsl language
		 * @param data node data
		 * @return model parameters
		 */
		default Map<String, Object> exactChatModelParam(DSLDialectType dialectType, Map<String, Object> data) {
			return switch (dialectType) {
				case DIFY -> MapReadUtil.safeCastToMapWithStringKey(
						MapReadUtil.getMapDeepValue(data, Map.class, "model", "completion_params"));
				case STUDIO -> {
					Map<String, Object> modeConfigMap = MapReadUtil.safeCastToMapWithStringKey(
							MapReadUtil.getMapDeepValue(data, Map.class, "config", "node_param", "model_config"));
					yield Optional
						.ofNullable(MapReadUtil
							.safeCastToListWithMap(MapReadUtil.getMapDeepValue(modeConfigMap, List.class, "params")))
						.orElse(List.of())
						.stream()
						.filter(map -> Boolean.TRUE.equals(map.get("enable")))
						.filter(map -> map.containsKey("key") && map.containsKey("value"))
						.map(map -> Map.entry(map.get("key").toString(), map.get("value")))
						.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (a, b) -> b));
				}
				default -> throw new UnsupportedOperationException();
			};
		}

	}

	public static <R> DialectConverter<R> defaultCustomDialectConverter(Class<R> clazz) {
		return new DialectConverter<>() {
			@Override
			public Boolean supportDialect(DSLDialectType dialectType) {
				return DSLDialectType.CUSTOM.equals(dialectType);
			}

			@Override
			public R parse(Map<String, Object> data) {
				ObjectMapper objectMapper = new ObjectMapper();
				objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
				return objectMapper.convertValue(data, clazz);
			}

			@Override
			public Map<String, Object> dump(R nodeData) {
				ObjectMapper objectMapper = new ObjectMapper();
				objectMapper.setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE);
				return objectMapper.convertValue(nodeData, new TypeReference<>() {
				});
			}
		};
	}

	protected abstract List<DialectConverter<T>> getDialectConverters();

	private static final Pattern DIFY_VAR_TEMPLATE_PATTERN = Pattern.compile("\\{\\{#(\\w+)\\.(\\w+)#}}");

	private static final Pattern STUDIO_VAR_TEMPLATE_PATTERN = Pattern.compile("\\$\\{(\\w+)\\.\\[?(\\w+)]?}");

	private static final Pattern VAR_TEMPLATE_PATTERN = Pattern.compile("\\{(\\w+)}");

	/**
	 * Convert variable placeholders in the text, such as Dify DSL's "Hello, {{#123.query#}}" into "Hello, {nodeName1_query}"
	 * @param dialectType dsl language
	 * @param templateString template string
	 * @param idToVarName nodeId to nodeVarName mapping
	 * @return conversion result
	 */
	protected String convertVarTemplate(DSLDialectType dialectType, String templateString,
			Map<String, String> idToVarName) {
		BiFunction<String, Map<String, String>, String> func = switch (dialectType) {
			case DIFY -> (str, map) -> {
				//todo: template supports context
				if (Strings.isNullOrEmpty(str)) {
					return str;
				}
				StringBuilder result = new StringBuilder();
				Matcher matcher = DIFY_VAR_TEMPLATE_PATTERN.matcher(str);
				while (matcher.find()) {
					String nodeId = matcher.group(1);
					String varName = matcher.group(2);
					String res = "{" + map.getOrDefault(nodeId, StringUtils.hasText(nodeId) ? nodeId : "unknown") + "_"
							+ varName + "}";
					matcher.appendReplacement(result, Matcher.quoteReplacement(res));
				}
				matcher.appendTail(result);
				return result.toString();
			};
			case STUDIO -> (str, map) -> {
				if (Strings.isNullOrEmpty(str)) {
					return str;
				}
				StringBuilder result = new StringBuilder();

				Matcher matcher = STUDIO_VAR_TEMPLATE_PATTERN.matcher(str);
				while (matcher.find()) {
					String nodeId = matcher.group(1);
					String varName = matcher.group(2);
					String res = "{" + map.getOrDefault(nodeId, StringUtils.hasText(nodeId) ? nodeId : "unknown") + "_"
							+ varName + "}";
					matcher.appendReplacement(result, Matcher.quoteReplacement(res));
				}
				matcher.appendTail(result);
				return result.toString();
			};
			default -> (str, map) -> str;
		};
		return func.apply(templateString, idToVarName);
	}

	/**
	 * Get the variable placeholder in the template, such as "Hello {var1}, {var2}" returns "[var1, var2]"
	 * @param template template string
	 * @return variable placeholder list
	 */
	protected List<String> getVarTemplateKeys(String template) {
		Matcher matcher = VAR_TEMPLATE_PATTERN.matcher(template);
		return matcher.results().map(m -> m.group(1)).toList();
	}

	/**
	 * Create an empty processing Consumer for ease of use.andThen programming
	 * @return BiConsumer
	 */
	protected BiConsumer<T, Map<String, String>> emptyProcessConsumer() {
		return (nodeData, varNameMap) -> {
		};
	}

}
