/*
 * Copyright 2025 the original author or authors.
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

package com.alibaba.cloud.ai.studio.core.config;

import com.google.common.collect.Sets;
import lombok.Data;
import org.springframework.context.annotation.Configuration;

import java.util.Set;

import static com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeTypeEnum.API;
import static com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeTypeEnum.MCP;
import static com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeTypeEnum.PLUGIN;
import static com.alibaba.cloud.ai.studio.runtime.domain.workflow.NodeTypeEnum.SCRIPT;

/**
 * Common configuration class for Studio service. Contains various configuration
 * parameters for conversation, workflow, and system behavior.
 *
 * @since 1.0.0.3
 */

@Configuration
// @NacosPropertySource(dataId = "cordondata.studio.config", groupId =
// "cordondata-studio", autoRefreshed = true)
@Data
public class CommonConfig {

	// TTL for conversation memory in cache (in seconds)
	private Long conversationMemoryTtlInCache = 3600L;

	// Maximum number of conversation rounds to store in cache
	private Integer maxConversationRoundInCache = 50;

	// Timeout for agent read operations (in milliseconds)
	private Integer agentReadTimeout = 180000;

	// Input timeout duration (in milliseconds)
	private Long inputTimeout = 5 * 60 * 1000L;

	// Workflow awaiting time between operations (in milliseconds)
	private Long workflowAwaitingTime = 100L;

	// Template for file search prompt
	private String fileSearchPrompt = """
			# Knowledge Base
			Please remember the following materials, they may be helpful in answering questions.
			${documents}
			""";

	private String citationPrompt = """
			Instruction: You need to write a high-quality answer for the given question using only the provided search documents, and correctly cite them. When citing multiple search results, please use formats like <ref>[1]</ref> or <ref>[1][3]</ref>. Note that each sentence must cite at least one document. In other words, you are prohibited from writing a sentence without citing any reference. Additionally, you should add citation marks in each sentence, especially before the period (punct.).

			$$Materials:
			[1] [Document] Photosynthesis in Plants.pdf
			[Title] Location of Photosynthesis
			[Content] Photosynthesis mainly takes place in chloroplasts, involving the conversion of light energy to chemical energy.
			[2] [Document] Photosynthesis.pdf
			[Title] Photosynthesis Conversion
			[Content] Photosynthesis is the process of using sunlight to convert CO2 and H2O into oxygen and glucose.

			Question: What is the basic process of photosynthesis?

			Reasoning steps:

			Step 1: I determine that documents [1] and [2] are relevant to the question.

			Step 2: Based on document [1], I wrote an answer statement and cited the document: "This process mainly takes place in chloroplasts, where light energy is absorbed by chlorophyll and converted into chemical energy through a series of chemical reactions, which is stored in the produced glucose<ref>[1]</ref>."

			Step 3: Based on document [2], I write an answer statement and cite the document: "Photosynthesis is the process by which plants, algae, and certain bacteria use sunlight to convert carbon dioxide and water into oxygen and glucose<ref>[2]</ref>.""

			Step 4: I merge, sort, and concatenate the above two answer statements to obtain a fluent and coherent answer.

			Answer: Photosynthesis is the process by which plants, algae, and certain bacteria use sunlight to convert carbon dioxide and water into oxygen and glucose<ref>[2]</ref>. This process mainly takes place in chloroplasts, where light energy is absorbed by chlorophyll and converted into chemical energy through a series of chemical reactions, stored in the produced glucose<ref>[1]</ref>.

			$$Materials:
			""";

	/**
	 * Node types that support retry on exception
	 */
	private Set<String> retrySupportNodeTypeSet = Sets.newHashSet(SCRIPT.getCode(), API.getCode(), PLUGIN.getCode(),
			MCP.getCode());

	/**
	 * Node types that support try-catch exception handling
	 */
	private Set<String> tryCatchSupportNodeTypeSet = Sets.newHashSet(SCRIPT.getCode(), API.getCode(), PLUGIN.getCode(),
			MCP.getCode());

	private String workflowRefreshInterval = "{\"console\":3000,\"async\":5000}}";

}
