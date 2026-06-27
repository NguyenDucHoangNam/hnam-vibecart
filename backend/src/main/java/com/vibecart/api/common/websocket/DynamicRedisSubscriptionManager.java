package com.vibecart.api.common.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicRedisSubscriptionManager {

    private final RedisMessageListenerContainer listenerContainer;

    private final ConcurrentHashMap<String, SubscriptionEntry> activeSubscriptions = new ConcurrentHashMap<>();

    private record SubscriptionEntry(AtomicInteger refCount, MessageListener listener) {
    }

    public void subscribeUser(String username, String channelPrefix, MessageListener listener) {
        String channelName = channelPrefix + username;
        activeSubscriptions.compute(channelName, (key, entry) -> {
            if (entry == null) {
                ChannelTopic topic = new ChannelTopic(channelName);
                listenerContainer.addMessageListener(listener, topic);
                log.info("Redis Dynamic Sub: Subscribed to channel '{}' for user '{}'", channelName, username);
                return new SubscriptionEntry(new AtomicInteger(1), listener);
            } else {
                int newCount = entry.refCount().incrementAndGet();
                log.debug("Redis Dynamic Sub: Incremented ref count for channel '{}' to {}", channelName, newCount);
                return entry;
            }
        });
    }

    public void unsubscribeUser(String username, String channelPrefix, MessageListener listener) {
        String channelName = channelPrefix + username;
        activeSubscriptions.computeIfPresent(channelName, (key, entry) -> {
            int newCount = entry.refCount().decrementAndGet();
            if (newCount <= 0) {
                ChannelTopic topic = new ChannelTopic(channelName);
                listenerContainer.removeMessageListener(entry.listener(), topic);
                log.info("Redis Dynamic Sub: Unsubscribed from channel '{}' for user '{}'", channelName, username);
                return null;
            } else {
                log.debug("Redis Dynamic Sub: Decremented ref count for channel '{}' to {}", channelName, newCount);
                return entry;
            }
        });
    }

    public boolean isSubscribed(String username, String channelPrefix) {
        String channelName = channelPrefix + username;
        SubscriptionEntry entry = activeSubscriptions.get(channelName);
        return entry != null && entry.refCount().get() > 0;
    }

    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }
}
