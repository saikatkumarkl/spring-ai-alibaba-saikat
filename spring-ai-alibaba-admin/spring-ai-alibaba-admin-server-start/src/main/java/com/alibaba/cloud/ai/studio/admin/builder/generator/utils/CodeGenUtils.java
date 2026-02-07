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

/**
 * Code generation helpers for common string handling and type conversion.
 *
 * @author yHong
 * @version 1.0
 * @since 2025/9/10 21:30
 */
public final class CodeGenUtils {

	private CodeGenUtils() {
	}

	/**
	 * Convert null to empty string.
	 * @param s input string
	 * @return non-null string
	 */
	public static String nvl(String s) {
		return s == null ? "" : s;
	}

	/**
	 * Escape strings for Java code generation.
	 * Escapes backslashes and double quotes for Java string literals.
	 *
	 * @param s input string
	 * @return escaped string
	 */
	public static String esc(String s) {
		return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
	}

	/**
	 * Convert object to string.
	 * @param o input object
	 * @return string representation; null if input is null
	 */
	public static String str(Object o) {
		return o == null ? null : String.valueOf(o);
	}

	/**
	 * Convert object to integer (safe conversion).
	 *
	 * @param v input value
	 * @return integer value, or null if conversion fails
	 */
	public static Integer toInt(Object v) {
		if (v instanceof Integer i) {
			return i;
		}
		if (v instanceof Number n) {
			return n.intValue();
		}
		if (v instanceof String s) {
			try {
				return Integer.parseInt(s.trim());
			}
			catch (Exception ignore) {
				// Ignore parse exceptions and return null
			}
		}
		return null;
	}

	/**
	 * Check if a string is blank (null or whitespace only).
	 * @param s input string
	 * @return true if string is null or whitespace only
	 */
	public static boolean isBlank(String s) {
		return s == null || s.trim().isEmpty();
	}

}
