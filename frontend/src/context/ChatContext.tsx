"use client";

import React, { createContext, useContext, useState, useEffect, useCallback, useRef } from "react";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { useWebSocket } from "@/hooks/useWebSocket";
import { chatService } from "@/services/chat.service";
import { 
  ConversationResponse, 
  MessageResponse, 
  PresenceResponse, 
  TypingResponse,
  ReadReceiptResponse 
} from "@/types";

interface ChatContextType {
  conversations: ConversationResponse[];
  activeConversation: ConversationResponse | null;
  messages: MessageResponse[];
  messagesPage: number;
  isLoadingConversations: boolean;
  isLoadingMessages: boolean;
  hasMoreMessages: boolean;
  typingUsers: string[];
  onlineStatusMap: Record<string, "ONLINE" | "OFFLINE">;
  globalUnreadCount: number;
  isConnected: boolean;
  
  setActiveConversation: (conv: ConversationResponse | null) => void;
  fetchConversations: () => Promise<void>;
  loadMoreMessages: () => Promise<void>;
  sendMessage: (content: string, type?: MessageResponse["type"], cardId?: string) => Promise<void>;
  sendTypingState: (isTyping: boolean) => void;
  startDirectChat: (targetUserId: string) => Promise<string>;
}

const ChatContext = createContext<ChatContextType | undefined>(undefined);

export function ChatProvider({ children }: { children: React.ReactNode }) {
  const { user, isAuthenticated } = useAuth();
  const toast = useToast();

  const { isConnected, subscribe, send } = useWebSocket();

  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [activeConversation, setActiveConversationState] = useState<ConversationResponse | null>(null);
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [messagesPage, setMessagesPage] = useState(0);
  const [hasMoreMessages, setHasMoreMessages] = useState(false);
  const [typingUsers, setTypingUsers] = useState<string[]>([]);
  const [onlineStatusMap, setOnlineStatusMap] = useState<Record<string, "ONLINE" | "OFFLINE">>({});
  
  const [isLoadingConversations, setIsLoadingConversations] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);

  const activeConversationRef = useRef<ConversationResponse | null>(null);
  const conversationsRef = useRef<ConversationResponse[]>([]);
  
  useEffect(() => {
    activeConversationRef.current = activeConversation;
  }, [activeConversation]);

  useEffect(() => {
    conversationsRef.current = conversations;
  }, [conversations]);

  const globalUnreadCount = conversations.reduce((sum, conv) => {
    if (!user) return sum;
    return sum + (conv.unreadCounts?.[user.id] || 0);
  }, 0);

  const fetchConversations = useCallback(async () => {
    if (!isAuthenticated) return;
    setIsLoadingConversations(true);
    try {
      const data = await chatService.getConversations();
      setConversations(data || []);
      
      if (data) {
        const uniqueMemberIds = new Set<string>();
        data.forEach(c => c.memberIds.forEach(id => {
          if (id !== user?.id) uniqueMemberIds.add(id);
        }));
        
        uniqueMemberIds.forEach(async (memberId) => {
          try {
            const presence = await chatService.getUserPresence(memberId);
            setOnlineStatusMap(prev => ({
              ...prev,
              [memberId]: presence.status
            }));
          } catch {
          }
        });
      }
    } catch (err) {
      console.error("Chat Context: Lỗi tải danh sách hội thoại", err);
    } finally {
      setIsLoadingConversations(false);
    }
  }, [isAuthenticated, user]);

  useEffect(() => {
    if (isAuthenticated) {
      fetchConversations();
    } else {
      setConversations([]);
      setActiveConversationState(null);
      setMessages([]);
    }
  }, [isAuthenticated, fetchConversations]);

  const loadMoreMessages = useCallback(async () => {
    if (!activeConversation || isLoadingMessages || !hasMoreMessages) return;
    
    setIsLoadingMessages(true);
    try {
      const nextPage = messagesPage + 1;
      const response = await chatService.getMessages(activeConversation.id, nextPage, 30);
      
      if (response && response.content) {
        setMessages(prev => [...prev, ...response.content]);
        setMessagesPage(nextPage);
        setHasMoreMessages(!response.last);
      }
    } catch (err) {
      console.error("Chat Context: Không thể tải thêm tin nhắn", err);
    } finally {
      setIsLoadingMessages(false);
    }
  }, [activeConversation, isLoadingMessages, hasMoreMessages, messagesPage]);

  const setActiveConversation = useCallback(async (conv: ConversationResponse | null) => {
    setActiveConversationState(conv);
    setMessages([]);
    setMessagesPage(0);
    setHasMoreMessages(false);
    setTypingUsers([]);

    if (!conv) return;

    setIsLoadingMessages(true);
    try {
      const response = await chatService.getMessages(conv.id, 0, 30);
      if (response && response.content) {
        setMessages(response.content);
        setHasMoreMessages(!response.last);
      }

      setConversations(prev => prev.map(c => {
        if (c.id === conv.id && user) {
          const updatedUnreads = { ...(c.unreadCounts || {}) };
          updatedUnreads[user.id] = 0;
          return { ...c, unreadCounts: updatedUnreads };
        }
        return c;
      }));

      if (isConnected) {
        send("/app/chat.seen", { conversationId: conv.id });
      }
    } catch (err) {
      console.error("Chat Context: Lỗi tải tin nhắn chi tiết", err);
    } finally {
      setIsLoadingMessages(false);
    }
  }, [user, isConnected, send]);
  const sendMessage = useCallback(async (
    content: string, 
    type: MessageResponse["type"] = "TEXT",
    cardId?: string
  ) => {
    if (!activeConversation || !isConnected) return;

    const messagePayload: any = {
      conversationId: activeConversation.id,
      content,
      type
    };

    if (type !== "TEXT" && cardId) {
      messagePayload.attachmentMetadata = {
        fileUrl: type === "IMAGE" ? content : undefined,
        fileName: type === "IMAGE" ? "Image_attachment.jpg" : `Card_${cardId}`,
        fileSize: 0,
        mimeType: type === "IMAGE" ? "image/jpeg" : "application/json",
        cardId
      };
    }

    send("/app/chat.sendMessage", messagePayload);
  }, [activeConversation, isConnected, send]);
  const sendTypingState = useCallback((isTyping: boolean) => {
    if (!activeConversation || !isConnected) return;
    send("/app/chat.typing", {
      conversationId: activeConversation.id,
      isTyping
    });
  }, [activeConversation, isConnected, send]);
  const startDirectChat = useCallback(async (targetUserId: string): Promise<string> => {
    try {
      const response = await chatService.createConversation([targetUserId], "DIRECT");
      await fetchConversations();
      setActiveConversation(response);
      return response.id;
    } catch (err: any) {
      toast.error("Không thể kết nối", err?.message || "Không thể tạo cuộc hội thoại.");
      throw err;
    }
  }, [fetchConversations, setActiveConversation, toast]);
  useEffect(() => {
    if (!isConnected) return;
    const unsubscribeMessages = subscribe<MessageResponse>("/user/queue/messages", (message) => {
      setConversations(prev => {
        const index = prev.findIndex(c => c.id === message.conversationId);
        if (index === -1) {
          fetchConversations();
          return prev;
        }

        const updated = [...prev];
        const conv = { ...updated[index] };
        
        conv.lastMessage = {
          messageId: message.id,
          senderId: message.senderId,
          content: message.content,
          type: message.type,
          createdAt: message.createdAt
        };
        conv.updatedAt = message.createdAt;
        if (user && activeConversationRef.current?.id !== message.conversationId) {
          const updatedUnreads = { ...(conv.unreadCounts || {}) };
          updatedUnreads[user.id] = (updatedUnreads[user.id] || 0) + 1;
          conv.unreadCounts = updatedUnreads;
        }
        updated.splice(index, 1);
        return [conv, ...updated];
      });
      if (activeConversationRef.current?.id === message.conversationId) {
        setMessages(prev => {
          if (prev.some(m => m.id === message.id)) return prev;
          return [message, ...prev];
        });
        if (user && message.senderId !== user.id && isConnected) {
          send("/app/chat.seen", { conversationId: message.conversationId });
        }
      }
    });
    const unsubscribeTyping = subscribe<TypingResponse>("/user/queue/typing", (data) => {
      if (activeConversationRef.current?.id === data.conversationId) {
        setTypingUsers(prev => {
          if (data.isTyping) {
            if (prev.includes(data.username)) return prev;
            return [...prev, data.username];
          } else {
            return prev.filter(name => name !== data.username);
          }
        });
      }
    });
    const unsubscribeSeen = subscribe<{ conversationId: string; userId: string; readAt: string }>("/user/queue/seen", (payload) => {
      try {
        if (activeConversationRef.current?.id === payload.conversationId) {
          setMessages(prev => prev.map(msg => {
            const alreadyRead = msg.readBy.some(receipt => receipt.userId === payload.userId);
            if (!alreadyRead && msg.senderId !== payload.userId) {
              return {
                ...msg,
                readBy: [...msg.readBy, { userId: payload.userId, readAt: payload.readAt }]
              };
            }
            return msg;
          }));
        }
      } catch (e) {
        console.error("Lỗi đọc payload seen receipt (personal queue)", e);
      }
    });

    return () => {
      if (unsubscribeMessages) unsubscribeMessages();
      if (unsubscribeTyping) unsubscribeTyping();
      if (unsubscribeSeen) unsubscribeSeen();
    };
  }, [isConnected, subscribe, fetchConversations, user, toast]);
  useEffect(() => {
    if (!isConnected || !activeConversation) return;

    const roomId = activeConversation.id;
    const unsubscribeRoomMessages = subscribe<MessageResponse>(`/topic/chat.${roomId}`, (message) => {
      setMessages(prev => {
        if (prev.some(m => m.id === message.id)) return prev;
        return [message, ...prev];
      });
      setConversations(prev => {
        const index = prev.findIndex(c => c.id === message.conversationId);
        if (index === -1) {
          fetchConversations();
          return prev;
        }

        const updated = [...prev];
        const conv = { ...updated[index] };
        
        conv.lastMessage = {
          messageId: message.id,
          senderId: message.senderId,
          content: message.content,
          type: message.type,
          createdAt: message.createdAt
        };
        conv.updatedAt = message.createdAt;
        updated.splice(index, 1);
        return [conv, ...updated];
      });
      if (user && message.senderId !== user.id && isConnected) {
        send("/app/chat.seen", { conversationId: roomId });
      }
    });
    const unsubscribeRoomTyping = subscribe<TypingResponse>(`/topic/chat.${roomId}/typing`, (data) => {
      if (user && data.username === user.username) return;

      setTypingUsers(prev => {
        if (data.isTyping) {
          if (prev.includes(data.username)) return prev;
          return [...prev, data.username];
        } else {
          return prev.filter(name => name !== data.username);
        }
      });
    });
    const unsubscribeRoomSeen = subscribe<{ conversationId: string; userId: string; readAt: string }>(`/topic/chat.${roomId}/seen`, (payload) => {
      try {
        setMessages(prev => prev.map(msg => {
          const alreadyRead = msg.readBy.some(receipt => receipt.userId === payload.userId);
          if (!alreadyRead && msg.senderId !== payload.userId) {
            return {
              ...msg,
              readBy: [...msg.readBy, { userId: payload.userId, readAt: payload.readAt }]
            };
          }
          return msg;
        }));
      } catch (e) {
        console.error("Lỗi đọc payload seen receipt", e);
      }
    });

    return () => {
      if (unsubscribeRoomMessages) unsubscribeRoomMessages();
      if (unsubscribeRoomTyping) unsubscribeRoomTyping();
      if (unsubscribeRoomSeen) unsubscribeRoomSeen();
    };
  }, [isConnected, activeConversation, subscribe, user]);
  useEffect(() => {
    if (!isConnected || !isAuthenticated) return;
    send("/app/chat.ping", {});

    const interval = setInterval(() => {
      send("/app/chat.ping", {});
    }, 30000);

    return () => clearInterval(interval);
  }, [isConnected, isAuthenticated, send]);

  return (
    <ChatContext.Provider
      value={{
        conversations,
        activeConversation,
        messages,
        messagesPage,
        isLoadingConversations,
        isLoadingMessages,
        hasMoreMessages,
        typingUsers,
        onlineStatusMap,
        globalUnreadCount,
        isConnected,
        setActiveConversation,
        fetchConversations,
        loadMoreMessages,
        sendMessage,
        sendTypingState,
        startDirectChat
      }}
    >
      {children}
    </ChatContext.Provider>
  );
}

export function useChat() {
  const context = useContext(ChatContext);
  if (context === undefined) {
    throw new Error("useChat must be used within a ChatProvider");
  }
  return context;
}
