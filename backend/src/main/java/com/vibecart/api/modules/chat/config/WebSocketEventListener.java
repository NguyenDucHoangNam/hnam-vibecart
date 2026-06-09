package com.vibecart.api.modules.chat.config;

import com.vibecart.api.modules.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;

/**
 * Lắng nghe các sự kiện kết nối/ngắt kết nối WebSocket để quản lý trạng thái Online/Offline và Subscription.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final PresenceService presenceService;
    private final DynamicRedisSubscriptionManager subscriptionManager;

    /**
     * Xử lý sự kiện khi Client kết nối thành công tới WebSocket: cập nhật online và đăng ký kênh Redis.
     */
    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        if (principal != null) {
            String username = principal.getName();
            presenceService.setOnline(username);
            subscriptionManager.subscribeUser(username);
            log.info("User {} connected to WebSocket — Redis channel subscribed", username);
        }
    }

    /**
     * Xử lý sự kiện khi Client ngắt kết nối WebSocket: cập nhật offline và hủy đăng ký kênh Redis.
     */
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        Principal principal = headerAccessor.getUser();
        if (principal != null) {
            String username = principal.getName();
            presenceService.setOffline(username);
            subscriptionManager.unsubscribeUser(username);
            log.info("User {} disconnected from WebSocket — Redis channel unsubscribed", username);
        }
    }
}

