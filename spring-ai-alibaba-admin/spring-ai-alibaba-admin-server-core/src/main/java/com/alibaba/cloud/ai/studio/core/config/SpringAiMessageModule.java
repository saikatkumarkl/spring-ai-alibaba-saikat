/*
 * Copyright 2025-2026 the original author or authors.
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

package com.alibaba.cloud.ai.studio.core.config;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.io.IOException;

/**
 * Jackson module for proper serialization/deserialization of Spring AI Message types.
 * <p>
 * Resolves the issue where Redisson's JsonJacksonCodec (which uses DefaultTyping
 * with {@code @class} annotations) fails to deserialize UserMessage, AssistantMessage,
 * and SystemMessage because they lack default constructors.
 * <p>
 * IMPORTANT: Uses raw JsonParser token traversal instead of {@code readTree()} because
 * readTree() goes through the ObjectMapper which has DefaultTyping active, causing
 * {@code InvalidTypeIdException: missing type id property '@class'} on nested objects
 * like metadata maps.
 *
 * @since 1.0.0.3
 */
public class SpringAiMessageModule extends SimpleModule {

	public SpringAiMessageModule() {
		super("SpringAiMessageModule");
		addDeserializer(UserMessage.class, new UserMessageDeserializer());
		addDeserializer(AssistantMessage.class, new AssistantMessageDeserializer());
		addDeserializer(SystemMessage.class, new SystemMessageDeserializer());
		addDeserializer(Message.class, new MessageInterfaceDeserializer());
	}

	/**
	 * Extract content and messageType from the current JSON object using raw token
	 * traversal. Skips all other fields to avoid DefaultTyping issues.
	 * @return String array: [0] = content, [1] = messageType (may be null)
	 */
	private static String[] extractFields(JsonParser p) throws IOException {
		String content = null;
		String messageType = null;

		// Handle case where parser is at START_OBJECT or already at FIELD_NAME
		if (p.currentToken() == JsonToken.START_OBJECT) {
			p.nextToken();
		}

		while (p.currentToken() != JsonToken.END_OBJECT && p.currentToken() != null) {
			if (p.currentToken() == JsonToken.FIELD_NAME) {
				String fieldName = p.currentName();
				p.nextToken(); // move to value

				if ("content".equals(fieldName) || "text".equals(fieldName) || "textContent".equals(fieldName)) {
					if (p.currentToken() == JsonToken.VALUE_STRING) {
						content = p.getText();
					}
					else {
						p.skipChildren();
					}
				}
				else if ("messageType".equals(fieldName)) {
					// messageType can be a simple string or typed array like
					// ["org.springframework.ai.chat.messages.MessageType", "USER"]
					if (p.currentToken() == JsonToken.VALUE_STRING) {
						messageType = p.getText();
					}
					else if (p.currentToken() == JsonToken.START_ARRAY) {
						// Typed enum: ["className", "VALUE"] — read second element
						p.nextToken(); // class name
						p.nextToken(); // actual value
						if (p.currentToken() == JsonToken.VALUE_STRING) {
							messageType = p.getText();
						}
						// Skip to end of array
						while (p.currentToken() != JsonToken.END_ARRAY) {
							p.nextToken();
						}
					}
					else {
						p.skipChildren();
					}
				}
				else {
					// Skip all other fields (metadata, media, @class, etc.)
					p.skipChildren();
				}
			}
			p.nextToken();
		}

		return new String[] { content != null ? content : "", messageType };
	}

	static class UserMessageDeserializer extends JsonDeserializer<UserMessage> {

		@Override
		public UserMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			String[] fields = extractFields(p);
			return new UserMessage(fields[0]);
		}

	}

	static class AssistantMessageDeserializer extends JsonDeserializer<AssistantMessage> {

		@Override
		public AssistantMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			String[] fields = extractFields(p);
			return new AssistantMessage(fields[0]);
		}

	}

	static class SystemMessageDeserializer extends JsonDeserializer<SystemMessage> {

		@Override
		public SystemMessage deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			String[] fields = extractFields(p);
			return new SystemMessage(fields[0]);
		}

	}

	static class MessageInterfaceDeserializer extends JsonDeserializer<Message> {

		@Override
		public Message deserialize(JsonParser p, DeserializationContext ctxt) throws IOException {
			String[] fields = extractFields(p);
			String content = fields[0];
			String messageType = fields[1];

			if (messageType != null) {
				return switch (messageType.toUpperCase()) {
					case "USER" -> new UserMessage(content);
					case "ASSISTANT" -> new AssistantMessage(content);
					case "SYSTEM" -> new SystemMessage(content);
					default -> new UserMessage(content);
				};
			}

			return new UserMessage(content);
		}

	}

}
