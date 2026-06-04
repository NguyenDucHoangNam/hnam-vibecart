package com.vibecart.api.modules.chat.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vibecart.api.modules.chat.model.ChatEvent;
import com.vibecart.api.modules.chat.dto.response.MessageResponse;
import com.vibecart.api.modules.chat.dto.response.TypingResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.io.IOException;

@Configuration
@Slf4j
public class RedisChatConfig {

    @Bean
    @ConditionalOnMissingBean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }

    @Bean
    public RedisTemplate<String, ChatEvent> chatRedisTemplate(RedisConnectionFactory connectionFactory, ObjectMapper objectMapper) {
        RedisTemplate<String, ChatEvent> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());

        Jackson2JsonRedisSerializer<ChatEvent> serializer = new Jackson2JsonRedisSerializer<>(objectMapper, ChatEvent.class);
        template.setValueSerializer(serializer);
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(serializer);
        return template;
    }

    /**
     * RedisMessageListenerContainer khởi tạo rỗng (không có listener mặc định).
     * DynamicRedisSubscriptionManager sẽ thêm/xoá listener tại runtime
     * dựa trên sự kiện WebSocket connect/disconnect của từng user.
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }

    @Bean
    public MessageListenerAdapter listenerAdapter(RedisMessageSubscriber subscriber) {
        return new MessageListenerAdapter(subscriber);
    }

    @Component
    @RequiredArgsConstructor
    public static class RedisMessageSubscriber implements MessageListener {
        private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RedisMessageSubscriber.class);

        private final SimpMessagingTemplate messagingTemplate;
        private final ObjectMapper objectMapper;

        private static final String CHANNEL_PREFIX = "chat:user:";

        @Override
        public void onMessage(Message message, byte[] pattern) {
            try {
                // Extract target username from the dynamic channel name: "chat:user:{username}"
                String channel = new String(message.getChannel());
                String targetUsername = channel.startsWith(CHANNEL_PREFIX)
                        ? channel.substring(CHANNEL_PREFIX.length())
                        : null;

                ChatEvent event = objectMapper.readValue(message.getBody(), ChatEvent.class);
                log.info("Redis Dynamic Sub received event type: {} for conversation: {} on channel: {}",
                        event.getType(), event.getConversationId(), channel);

                if ("MESSAGE".equals(event.getType())) {
                    MessageResponse messageResponse = objectMapper.readValue(event.getPayloadJson(), MessageResponse.class);

                    // Route to the specific user's personal queue
                    if (targetUsername != null) {
                        messagingTemplate.convertAndSendToUser(targetUsername, "/queue/messages", messageResponse);
                    }

                    // Also route to group topic for group chat subscribers
                    messagingTemplate.convertAndSend("/topic/chat." + event.getConversationId(), messageResponse);

                } else if ("TYPING".equals(event.getType())) {
                    TypingResponse typingResponse = objectMapper.readValue(event.getPayloadJson(), TypingResponse.class);

                    // Route to the specific user's personal queue
                    if (targetUsername != null) {
                        messagingTemplate.convertAndSendToUser(targetUsername, "/queue/typing", typingResponse);
                    }

                    // Also route to group topic
                    messagingTemplate.convertAndSend("/topic/chat." + event.getConversationId() + "/typing", typingResponse);

                } else if ("READ_RECEIPT".equals(event.getType())) {
                    // Route to the specific user's personal queue
                    if (targetUsername != null) {
                        messagingTemplate.convertAndSendToUser(targetUsername, "/queue/seen", event.getPayloadJson());
                    }

                    // Also route to group topic for all subscribers
                    messagingTemplate.convertAndSend("/topic/chat." + event.getConversationId() + "/seen", event.getPayloadJson());
                }
            } catch (IOException e) {
                log.error("Failed to parse Redis message: {}", e.getMessage(), e);
            }
        }
    }
}
