package com.alibaba.cloud.ai.studio.admin.utils;

import com.alibaba.cloud.ai.studio.admin.dto.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;


@Slf4j
public class SessionUtils {

    /**
     * Convert chat history to Spring AI message format
     *
     * @param messages message list
     * @return Spring AI message list
     */
    public static List<Message> convertChatMessages(List<ChatMessage> messages) {
        List<Message> convertedMessages = new ArrayList<>();
        for (ChatMessage message : messages) {
            if ("user".equals(message.getRole())) {
                convertedMessages.add(new UserMessage(message.getContent()));
            } else if ("assistant".equals(message.getRole())) {
                convertedMessages.add(new AssistantMessage(message.getContent()));
            }
            //Ignore other roles (such as system, etc.) and can be expanded as needed
        }
        return convertedMessages;
    }
}
