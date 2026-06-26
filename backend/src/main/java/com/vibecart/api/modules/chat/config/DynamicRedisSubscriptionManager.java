package com.vibecart.api.modules.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicRedisSubscriptionManager {

    private final RedisMessageListenerContainer listenerContainer;
    private final MessageListenerAdapter listenerAdapter;
    private final ConcurrentHashMap<String, AtomicInteger> activeSubscriptions = new ConcurrentHashMap<>();

    private static final String CHANNEL_PREFIX = "chat:user:";
    public void subscribeUser(String username) {
        activeSubscriptions.compute(username, (key, refCount) -> {
            if (refCount == null) {
                ChannelTopic topic = new ChannelTopic(CHANNEL_PREFIX + username);
                listenerContainer.addMessageListener(listenerAdapter, topic);
                log.info("Redis Dynamic Sub: Subscribed to channel '{}' for user '{}'", topic.getTopic(), username);
                return new AtomicInteger(1);
            } else {
                int newCount = refCount.incrementAndGet();
                log.debug("Redis Dynamic Sub: Incremented ref count for user '{}' to {}", username, newCount);
                return refCount;
            }
        });
    }
    public void unsubscribeUser(String username) {
        activeSubscriptions.computeIfPresent(username, (key, refCount) -> {
            int newCount = refCount.decrementAndGet();
            if (newCount <= 0) {
                ChannelTopic topic = new ChannelTopic(CHANNEL_PREFIX + username);
                listenerContainer.removeMessageListener(listenerAdapter, topic);
                log.info("Redis Dynamic Sub: Unsubscribed from channel '{}' for user '{}'", topic.getTopic(), username);
                return null;
            } else {
                log.debug("Redis Dynamic Sub: Decremented ref count for user '{}' to {}", username, newCount);
                return refCount;
            }
        });
    }
    public boolean isSubscribed(String username) {
        AtomicInteger refCount = activeSubscriptions.get(username);
        return refCount != null && refCount.get() > 0;
    }
    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }
}
