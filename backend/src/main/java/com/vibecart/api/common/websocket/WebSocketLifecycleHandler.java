package com.vibecart.api.common.websocket;

public interface WebSocketLifecycleHandler {
    void onConnect(String username);
    void onDisconnect(String username);
}
