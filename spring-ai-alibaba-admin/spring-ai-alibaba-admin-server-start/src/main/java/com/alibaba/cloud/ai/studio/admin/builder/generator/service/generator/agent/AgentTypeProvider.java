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

import java.util.List;
import java.util.Map;

/**
 * @author yHong
 * @version 1.0
 * @since 2025/8/28 17:52
 */
public interface AgentTypeProvider {

	//Type identifier, aligned with agent.type in schema, such as "ReactAgent", "SequentialAgent"
	String type();

	//version number of handle (used for migration)
	String handleVersion();

	//Returns the JSON Schema of the handle of this type (front-end form rendering, verification)
	String jsonSchema();

	//Returns the handle default value of this type (the initial value when the front end is created)
	Map<String, Object> defaultHandle();

	//Version migration (upgrade from old version handle to current handleVersion)
	Map<String, Object> migrate(Map<String, Object> oldHandle, String fromVersion);

	//Rendering code segmentation: output code and import based on shell + handle + sub-Agent variable name (passed in when the parent node is called)
	CodeSections render(AgentShell shell, Map<String, Object> handle, RenderContext ctx, List<String> childVarNames);

	//Verify validity of DSL data
	void validateDSL(Map<String, Object> root);

}
