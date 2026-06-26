"use client";

import { useEffect, useRef, useState, useCallback } from "react";
import { Client, Message as StompMessage } from "@stomp/stompjs";
import { WS_BASE_URL } from "@/constants/api";
import { useAuth } from "./useAuth";

interface UseWebSocketOptions {
  onConnect?: () => void;
  onDisconnect?: () => void;
  onError?: (err: any) => void;
}

export function useWebSocket(options: UseWebSocketOptions = {}) {
  const { user } = useAuth();
  const [isConnected, setIsConnected] = useState(false);
  const clientRef = useRef<Client | null>(null);
  const subscriptionsRef = useRef<Map<string, any>>(new Map());

  const onConnectRef = useRef(options.onConnect);
  const onDisconnectRef = useRef(options.onDisconnect);
  const onErrorRef = useRef(options.onError);

  useEffect(() => {
    onConnectRef.current = options.onConnect;
    onDisconnectRef.current = options.onDisconnect;
    onErrorRef.current = options.onError;
  }, [options.onConnect, options.onDisconnect, options.onError]);

  const disconnect = useCallback(() => {
    if (clientRef.current) {
      console.log("Disconnecting WebSocket client...");
      subscriptionsRef.current.forEach((sub) => sub.unsubscribe());
      subscriptionsRef.current.clear();
      clientRef.current.deactivate();
      clientRef.current = null;
      setIsConnected(false);
    }
  }, []);

  const connect = useCallback(() => {
    if (clientRef.current?.active) return;
    
    const token = typeof window !== "undefined" ? localStorage.getItem("access_token") : null;
    
    const stompClient = new Client({
      brokerURL: WS_BASE_URL,
      connectHeaders: token ? { Authorization: `Bearer ${token}` } : {},
      beforeConnect: () => {
        const freshToken = localStorage.getItem("access_token");
        stompClient.connectHeaders = freshToken ? { Authorization: `Bearer ${freshToken}` } : {};
      },
      debug: (str) => {
        if (process.env.NODE_ENV !== "production") {
          console.log("[STOMP Debug] ", str);
        }
      },
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    stompClient.onConnect = (frame) => {
      console.log("WebSocket STOMP connected: " + frame);
      setIsConnected(true);
      if (onConnectRef.current) onConnectRef.current();
    };

    stompClient.onStompError = (frame) => {
      console.error("STOMP error occurred:", frame.headers["message"]);
      if (onErrorRef.current) onErrorRef.current(frame);
    };

    stompClient.onDisconnect = () => {
      console.log("WebSocket STOMP disconnected");
      setIsConnected(false);
      if (onDisconnectRef.current) onDisconnectRef.current();
    };

    stompClient.onWebSocketClose = () => {
      console.log("WebSocket connection closed");
      setIsConnected(false);
    };

    stompClient.activate();
    clientRef.current = stompClient;
  }, []);

  useEffect(() => {
    if (user) {
      connect();
    } else {
      disconnect();
    }

    return () => {
      disconnect();
    };
  }, [user, connect, disconnect]);

  const subscribe = useCallback(
    <T>(destination: string, callback: (message: T) => void) => {
      try {
        if (!clientRef.current || !clientRef.current.connected) {
          console.warn("Cannot subscribe, client not connected or active. Destination: ", destination);
          return null;
        }

        if (subscriptionsRef.current.has(destination)) {
          const existingSub = subscriptionsRef.current.get(destination);
          return () => {
            try {
              existingSub.unsubscribe();
            } catch (err) {
              console.warn("Failed to unsubscribe gracefully:", err);
            }
            subscriptionsRef.current.delete(destination);
          };
        }

        const subscription = clientRef.current.subscribe(destination, (message: StompMessage) => {
          try {
            const parsedBody = JSON.parse(message.body) as T;
            callback(parsedBody);
          } catch (e) {
            console.error("Failed to parse websocket message body: ", e);
          }
        });

        subscriptionsRef.current.set(destination, subscription);

        return () => {
          try {
            subscription.unsubscribe();
          } catch (err) {
            console.warn("Failed to unsubscribe gracefully:", err);
          }
          subscriptionsRef.current.delete(destination);
        };
      } catch (err) {
        console.error("Error subscribing to destination " + destination + ":", err);
        return null;
      }
    },
    []
  );
  const send = useCallback(
    (destination: string, body: any) => {
      try {
        if (!clientRef.current || !clientRef.current.connected) {
          console.error("Cannot send message, client not connected. Destination: ", destination);
          return false;
        }

        clientRef.current.publish({
          destination,
          body: JSON.stringify(body),
        });
        return true;
      } catch (err) {
        console.error("Error sending message to " + destination + ":", err);
        return false;
      }
    },
    []
  );

  return {
    isConnected,
    subscribe,
    send,
    reconnect: connect,
    disconnect,
  };
}
