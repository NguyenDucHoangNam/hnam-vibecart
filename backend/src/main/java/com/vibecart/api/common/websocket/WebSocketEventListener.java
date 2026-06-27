package com.vibecart.api.common.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketEventListener {

    private final List<WebSocketLifecycleHandler> handlers;

    @EventListener
    public void handleWebSocketConnectedListener(SessionConnectedEvent event) {
        String username = extractUsername(event.getMessage());
        if (username != null) {
            log.info("WebSocket connected — dispatching onConnect to {} handler(s) for user '{}'",
                    handlers.size(), username);
            handlers.forEach(handler -> {
                try {
                    handler.onConnect(username);
                } catch (Exception e) {
                    log.error("Error in WebSocket onConnect handler [{}] for user '{}': {}",
                            handler.getClass().getSimpleName(), username, e.getMessage(), e);
                }
            });
        }
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        String username = extractUsername(event.getMessage());
        if (username != null) {
            log.info("WebSocket disconnected — dispatching onDisconnect to {} handler(s) for user '{}'",
                    handlers.size(), username);
            handlers.forEach(handler -> {
                try {
                    handler.onDisconnect(username);
                } catch (Exception e) {
                    log.error("Error in WebSocket onDisconnect handler [{}] for user '{}': {}",
                            handler.getClass().getSimpleName(), username, e.getMessage(), e);
                }
            });
        }
    }

    private String extractUsername(org.springframework.messaging.Message<?> message) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(message);
        Principal principal = headerAccessor.getUser();
        return principal != null ? principal.getName() : null;
    }
}
