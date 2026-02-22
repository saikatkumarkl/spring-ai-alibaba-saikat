/*
 * Copyright 2024-2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.alibaba.cloud.ai.examples.documentation.framework.advanced.a2a;

import com.alibaba.cloud.ai.graph.agent.ReactAgent;
import com.alibaba.cloud.ai.agent.studio.loader.AgentLoader;

import org.springframework.ai.chat.model.ChatModel;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.Nonnull;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Defines and exposes local ReactAgent beans
 */
@Configuration
public class A2AAgentConfig {

	@Bean(name = "dataAnalysisAgent")
	public ReactAgent dataAnalysisAgent(@Qualifier("dashScopeChatModel") ChatModel chatModel) {
		return ReactAgent.builder()
				.name("data_analysis_agent")
				.model(chatModel)
				.description("A local agent specialized in data analysis and statistical computation")
				.instruction("You are a professional data analysis expert, skilled at handling various data statistics and analysis tasks. " +
						"You can understand the user's data analysis needs, provide accurate statistical results and professional analysis advice.")
				.outputKey("messages")
				.build();
	}

	@Bean
	public AgentLoader agentLoader(@Qualifier("dataAnalysisAgent") ReactAgent dataAnalysisAgent) {
		return new AgentLoader() {
			@Override
			@Nonnull
			public List<String> listAgents() {
				return List.of(dataAnalysisAgent.name());
			}

			@Override
			public com.alibaba.cloud.ai.graph.agent.Agent loadAgent(String name) {
				if (dataAnalysisAgent.name().equals(name)) {
					return dataAnalysisAgent;
				}
				throw new NoSuchElementException("Agent not found: " + name);
			}
		};
	}
}
