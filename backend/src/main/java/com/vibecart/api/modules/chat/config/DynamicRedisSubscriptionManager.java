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
 * Quản lý đăng ký/hủy đăng ký (subscribe/unsubscribe) các kênh Redis động theo
 * người dùng.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DynamicRedisSubscriptionManager {

    private final RedisMessageListenerContainer listenerContainer;
    private final MessageListenerAdapter listenerAdapter;
    private final ConcurrentHashMap<String, AtomicInteger> activeSubscriptions = new ConcurrentHashMap<>();

    private static final String CHANNEL_PREFIX = "chat:user:";

    /**
     * Đăng ký kênh Redis cho người dùng khi kết nối WebSocket (hỗ trợ đếm lượt kết
     * nối từ nhiều tab).
     */
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

    /**
     * Hủy đăng ký kênh Redis của người dùng khi ngắt kết nối WebSocket (chỉ hủy hẳn
     * khi tab cuối cùng đóng).
     */
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

    /**
     * Kiểm tra xem người dùng có đang đăng ký kênh hoạt động trên instance này
     * không.
     */
    public boolean isSubscribed(String username) {
        AtomicInteger refCount = activeSubscriptions.get(username);
        return refCount != null && refCount.get() > 0;
    }

    /**
     * Lấy tổng số lượng người dùng đang đăng ký kênh hoạt động trên instance này.
     */
    public int getActiveSubscriptionCount() {
        return activeSubscriptions.size();
    }
}
