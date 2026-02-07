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

package com.alibaba.cloud.ai.studio.admin.builder.generator.utils;

import java.lang.reflect.Constructor;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public final class MapReadUtil {

	private MapReadUtil() {

	}

	/**
	 * Convert an object to {@code List<T>}.
	 * @param obj Object
	 * @return {@code List<T>}; returns null if obj is not a List or elements are not T.
	 */
	public static <T> List<T> safeCastToList(Object obj, Class<T> clazz) {
		if (!(obj instanceof List<?> list)) {
			return null;
		}
		for (Object object : list) {
			if (!clazz.isInstance(object)) {
				return null;
			}
		}
		@SuppressWarnings("unchecked")
		List<T> tList = (List<T>) list;
		return tList;
	}

	/**
	 * Convert an object to {@code List<Map<String,Object>>}.
	 * @param obj Object
	 * @return {@code List<Map<String,Object>>}
	 */
	public static List<Map<String, Object>> safeCastToListWithMap(Object obj) {
		if (!(obj instanceof List<?> list)) {
			return null;
		}
		return list.stream().map(MapReadUtil::safeCastToMapWithStringKey).toList();
	}

	/**
	 * Convert an object to {@code Map<String,Object>}.
	 * @param obj Object
	 * @return {@code Map<String,Object>}
	 */
	public static Map<String, Object> safeCastToMapWithStringKey(Object obj) {
		if (obj instanceof Map<?, ?> map) {
			// Ensure all keys are String
			for (Object key : map.keySet()) {
				if (!(key instanceof String)) {
					return null;
				}
			}
			@SuppressWarnings("unchecked")
			Map<String, Object> typedMap = (Map<String, Object>) map;
			return typedMap;
		}
		else {
			return null;
		}
	}

	/**
	 * Read a nested value from a map.
	 * @param map map
	 * @param clazz value type
	 * @param keys nested keys
	 * @return value, or null if not found or incompatible
	 */
	public static <T> T getMapDeepValue(Map<String, Object> map, Class<T> clazz, String... keys) {
		for (int i = 0; i < keys.length; i++) {
			String key = keys[i];
			if (!map.containsKey(key)) {
				return null;
			}
			Object object = map.get(key);
			if (i == keys.length - 1) {
				// Last level: verify target type
				try {
					return clazz.cast(object);
				}
				catch (ClassCastException e) {
					return null;
				}
			}
			// Intermediate level: verify map
			map = safeCastToMapWithStringKey(object);
			if (map == null) {
				return null;
			}
		}
		return null;
	}

	private static String camelToSnakeCase(String camelCaseStr) {
		if (camelCaseStr == null || camelCaseStr.isEmpty()) {
			return camelCaseStr;
		}

		StringBuilder result = new StringBuilder();
		result.append(Character.toLowerCase(camelCaseStr.charAt(0)));

		for (int i = 1; i < camelCaseStr.length(); i++) {
			char ch = camelCaseStr.charAt(i);
			if (Character.isUpperCase(ch)) {
				result.append('_').append(Character.toLowerCase(ch));
			}
			else {
				result.append(ch);
			}
		}

		return result.toString();
	}

	/**
	 * Map fields into a target record type.
	 * @param map map
	 * @param clazz record type
	 * @return record instance
	 */
	public static <T extends Record> T castMapToRecord(Map<String, Object> map, Class<T> clazz) {
		if (!clazz.isRecord()) {
			return null;
		}
		Constructor<?> constructor = clazz.getDeclaredConstructors()[0];
		Object[] params = Arrays.stream(constructor.getParameters()).map(p -> {
			Object object = map.get(p.getName());
			if (object == null) {
				// Try snake_case field name
				String name = camelToSnakeCase(p.getName());
				object = map.get(name);
			}
			// Verify target type
			if (object == null || !p.getType().isInstance(object)) {
				return null;
			}
			return p.getType().cast(object);
		}).toArray();
		try {
			Object newInstance = constructor.newInstance(params);
			return clazz.cast(newInstance);
		}
		catch (Exception e) {
			return null;
		}
	}

}
