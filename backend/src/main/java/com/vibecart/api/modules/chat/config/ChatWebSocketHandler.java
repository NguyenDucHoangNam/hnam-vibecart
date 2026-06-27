package com.vibecart.api.modules.chat.config;

import com.vibecart.api.common.websocket.DynamicRedisSubscriptionManager;
import com.vibecart.api.common.websocket.WebSocketLifecycleHandler;
import com.vibecart.api.modules.chat.service.PresenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ChatWebSocketHandler implements WebSocketLifecycleHandler {

    private final PresenceService presenceService;
    private final DynamicRedisSubscriptionManager subscriptionManager;
    private final ChatRedisMessageRouter chatRedisMessageRouter;

    private static final String CHAT_CHANNEL_PREFIX = "chat:user:";

    @Override
    public void onConnect(String username) {
        presenceService.setOnline(username);
        subscriptionManager.subscribeUser(username, CHAT_CHANNEL_PREFIX, chatRedisMessageRouter);
        log.info("Chat: User {} connected — presence set ONLINE, Redis channel subscribed", username);
    }

    @Override
    public void onDisconnect(String username) {
        presenceService.setOffline(username);
        subscriptionManager.unsubscribeUser(username, CHAT_CHANNEL_PREFIX, chatRedisMessageRouter);
        log.info("Chat: User {} disconnected — presence set OFFLINE, Redis channel unsubscribed", username);
    }
}
