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
package com.alibaba.cloud.ai.examples.chatbot;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OllamaConfiguration {

	@Bean
	public ChatModel chatModel(
			@Value("${spring.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
			@Value("${spring.ai.ollama.chat.model:llama3.3}") String model) {
		
		var ollamaApi = OllamaApi.builder()
				.baseUrl(baseUrl)
				.build();
		
		var options = OllamaChatOptions.builder()
				.model(model)
				.temperature(0.7)
				.build();
		
		return OllamaChatModel.builder()
				.ollamaApi(ollamaApi)
				.defaultOptions(options)
				.build();
	}
}
