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

/**
 * Quản lý việc subscribe/unsubscribe các Redis channel động theo user.
 * <p>
 * Mỗi user đang kết nối WebSocket trên instance này sẽ có một channel riêng:
 * {@code chat:user:{username}}
 * <p>
 * Hỗ trợ multi-tab: Nếu cùng một user mở nhiều tab trên cùng instance,
 * sử dụng reference counter để chỉ unsubscribe khi tab cuối cùng đóng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicRedisSubscriptionManager {

    private final RedisMessageListenerContainer listenerContainer;
    private final MessageListenerAdapter listenerAdapter;

    /**
     * Tracks active subscriptions: username -> reference count.
     * Reference count > 0 means the channel is subscribed.
     */
    private final ConcurrentHashMap<String, AtomicInteger> activeSubscriptions = new ConcurrentHashMap<>();

    private static final String CHANNEL_PREFIX = "chat:user:";

    /**
     * Subscribe to the user's Redis channel when they connect via WebSocket.
     * If the user already has an active subscription (multi-tab), just increment the counter.
     *
     * @param username the authenticated username
     */
    public void subscribeUser(String username) {
        activeSubscriptions.compute(username, (key, refCount) -> {
            if (refCount == null) {
                // First connection for this user on this instance → subscribe
                ChannelTopic topic = new ChannelTopic(CHANNEL_PREFIX + username);
                listenerContainer.addMessageListener(listenerAdapter, topic);
                log.info("Redis Dynamic Sub: Subscribed to channel '{}' for user '{}'", topic.getTopic(), username);
                return new AtomicInteger(1);
            } else {
                // Multi-tab: user already subscribed, just increment ref count
                int newCount = refCount.incrementAndGet();
                log.debug("Redis Dynamic Sub: Incremented ref count for user '{}' to {}", username, newCount);
                return refCount;
            }
        });
    }

    /**
     * Unsubscribe from the user's Redis channel when they disconnect from WebSocket.
     * Only actually unsubscribes when the last session (tab) disconnects.
     *
     * @param username the authenticated username
     */
    public void unsubscribeUser(String username) {
        activeSubscriptions.computeIfPresent(username, (key, refCount) -> {
            int newCount = refCount.decrementAndGet();
            if (newCount <= 0) {
                // Last session disconnected → unsubscribe from Redis channel
                ChannelTopic topic = new ChannelTopic(CHANNEL_PREFIX + username);
                listenerContainer.removeMessageListener(listenerAdapter, topic);
                log.info("Redis Dynamic Sub: Unsubscribed from channel '{}' for user '{}'", topic.getTopic(), username);
                return null; // Remove entry from map
            } else {
                log.debug("Redis Dynamic Sub: Decremented ref count for user '{}' to {}", username, newCount);
                return refCount;
            }
        });
    }

    /**
     * Check if a user currently has an active subscription on this instance.
     *
     * @param username the username to check
     * @return true if subscribed
     */
    public boolean isSubscribed(String username) {
        AtomicInteger refCount = activeSubscriptions.get(username);
        return refCount != null && refCount.get() > 0;
    }

    /**
     * Get the number of active user subscriptions on this instance.
     *
     * @return count of subscribed users
     */
    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }
}
