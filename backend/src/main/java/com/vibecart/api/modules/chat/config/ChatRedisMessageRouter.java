package com.vibecart.api.modules.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.modules.chat.dto.event.ChatEvent;
import com.vibecart.api.modules.chat.dto.response.MessageResponse;
import com.vibecart.api.modules.chat.dto.response.TypingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatRedisMessageRouter implements MessageListener {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    private static final String CHANNEL_PREFIX = "chat:user:";

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String channel = new String(message.getChannel());
            String targetUsername = channel.startsWith(CHANNEL_PREFIX)
                    ? channel.substring(CHANNEL_PREFIX.length())
                    : null;

            ChatEvent event = objectMapper.readValue(message.getBody(), ChatEvent.class);
            log.info("Chat Redis received event type: {} for conversation: {} on channel: {}",
                    event.getType(), event.getConversationId(), channel);

            if ("MESSAGE".equals(event.getType())) {
                MessageResponse messageResponse = objectMapper.readValue(event.getPayloadJson(), MessageResponse.class);

                if (targetUsername != null) {
                    messagingTemplate.convertAndSendToUser(targetUsername, "/queue/messages", messageResponse);
                }

                messagingTemplate.convertAndSend("/topic/chat." + event.getConversationId(), messageResponse);

            } else if ("TYPING".equals(event.getType())) {
                TypingResponse typingResponse = objectMapper.readValue(event.getPayloadJson(), TypingResponse.class);

                if (targetUsername != null) {
                    messagingTemplate.convertAndSendToUser(targetUsername, "/queue/typing", typingResponse);
                }

                messagingTemplate.convertAndSend("/topic/chat." + event.getConversationId() + "/typing", typingResponse);

            } else if ("READ_RECEIPT".equals(event.getType())) {
                if (targetUsername != null) {
                    messagingTemplate.convertAndSendToUser(targetUsername, "/queue/seen", event.getPayloadJson());
                }

                messagingTemplate.convertAndSend("/topic/chat." + event.getConversationId() + "/seen", event.getPayloadJson());
            }
        } catch (IOException e) {
            log.error("Failed to parse Redis chat message: {}", e.getMessage(), e);
        }
    }
}
