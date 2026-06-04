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
  typingUsers: string[]; // Tên người dùng đang soạn thảo trong phòng hiện tại
  onlineStatusMap: Record<string, "ONLINE" | "OFFLINE">; // userId -> status
  globalUnreadCount: number;
  isConnected: boolean; // WebSocket connection status
  
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

  // STOMP WebSocket hook
  const { isConnected, subscribe, send } = useWebSocket();

  // States
  const [conversations, setConversations] = useState<ConversationResponse[]>([]);
  const [activeConversation, setActiveConversationState] = useState<ConversationResponse | null>(null);
  const [messages, setMessages] = useState<MessageResponse[]>([]);
  const [messagesPage, setMessagesPage] = useState(0);
  const [hasMoreMessages, setHasMoreMessages] = useState(false);
  const [typingUsers, setTypingUsers] = useState<string[]>([]);
  const [onlineStatusMap, setOnlineStatusMap] = useState<Record<string, "ONLINE" | "OFFLINE">>({});
  
  const [isLoadingConversations, setIsLoadingConversations] = useState(false);
  const [isLoadingMessages, setIsLoadingMessages] = useState(false);

  // Refs for real-time state tracking inside callbacks
  const activeConversationRef = useRef<ConversationResponse | null>(null);
  const conversationsRef = useRef<ConversationResponse[]>([]);
  
  useEffect(() => {
    activeConversationRef.current = activeConversation;
  }, [activeConversation]);

  useEffect(() => {
    conversationsRef.current = conversations;
  }, [conversations]);

  // Derived: Tổng tin nhắn chưa đọc
  const globalUnreadCount = conversations.reduce((sum, conv) => {
    if (!user) return sum;
    return sum + (conv.unreadCounts?.[user.id] || 0);
  }, 0);

  // Lấy danh sách cuộc trò chuyện từ REST API
  const fetchConversations = useCallback(async () => {
    if (!isAuthenticated) return;
    setIsLoadingConversations(true);
    try {
      const data = await chatService.getConversations();
      setConversations(data || []);
      
      // Đồng bộ trạng thái trực tuyến cho các thành viên trong các phòng chat
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
            // Lỗi nhẹ bỏ qua
          }
        });
      }
    } catch (err) {
      console.error("Chat Context: Lỗi tải danh sách hội thoại", err);
    } finally {
      setIsLoadingConversations(false);
    }
  }, [isAuthenticated, user]);

  // Khởi chạy khi đăng nhập
  useEffect(() => {
    if (isAuthenticated) {
      fetchConversations();
    } else {
      setConversations([]);
      setActiveConversationState(null);
      setMessages([]);
    }
  }, [isAuthenticated, fetchConversations]);

  // Tải thêm tin nhắn cũ (Infinite scroll)
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

  // Thiết lập cuộc trò chuyện đang mở
  const setActiveConversation = useCallback(async (conv: ConversationResponse | null) => {
    setActiveConversationState(conv);
    setMessages([]);
    setMessagesPage(0);
    setHasMoreMessages(false);
    setTypingUsers([]);

    if (!conv) return;

    setIsLoadingMessages(true);
    try {
      // 1. Tải 30 tin nhắn mới nhất
      const response = await chatService.getMessages(conv.id, 0, 30);
      if (response && response.content) {
        setMessages(response.content);
        setHasMoreMessages(!response.last);
      }

      // 2. Reset unread count cục bộ
      setConversations(prev => prev.map(c => {
        if (c.id === conv.id && user) {
          const updatedUnreads = { ...(c.unreadCounts || {}) };
          updatedUnreads[user.id] = 0;
          return { ...c, unreadCounts: updatedUnreads };
        }
        return c;
      }));

      // Đồng bộ trạng thái đã xem qua STOMP WebSocket
      if (isConnected) {
        send("/app/chat.seen", { conversationId: conv.id });
      }
    } catch (err) {
      console.error("Chat Context: Lỗi tải tin nhắn chi tiết", err);
    } finally {
      setIsLoadingMessages(false);
    }
  }, [user, isConnected, send]);

  // Gửi tin nhắn qua STOMP WebSocket
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
      // Image hoặc Product/Order card
      messagePayload.attachmentMetadata = {
        fileUrl: type === "IMAGE" ? content : undefined, // Nếu là ảnh thì content là url
        fileName: type === "IMAGE" ? "Image_attachment.jpg" : `Card_${cardId}`,
        fileSize: 0,
        mimeType: type === "IMAGE" ? "image/jpeg" : "application/json",
        cardId
      };
    }

    send("/app/chat.sendMessage", messagePayload);
  }, [activeConversation, isConnected, send]);

  // Gửi trạng thái gõ chữ (Typing state)
  const sendTypingState = useCallback((isTyping: boolean) => {
    if (!activeConversation || !isConnected) return;
    send("/app/chat.typing", {
      conversationId: activeConversation.id,
      isTyping
    });
  }, [activeConversation, isConnected, send]);

  // Khởi tạo Chat trực tiếp với đối phương
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

  // =========================================================================
  // REAL-TIME WEBSOCKET SUBSCRIPTIONS & EVENT HANDLERS
  // =========================================================================

  // 1. Lắng nghe tin nhắn mới toàn cục (Chung cho tất cả các phòng)
  useEffect(() => {
    if (!isConnected) return;

    // Lắng nghe tin nhắn mới gửi đến hàng đợi cá nhân
    const unsubscribeMessages = subscribe<MessageResponse>("/user/queue/messages", (message) => {
      // 1. Cập nhật preview lastMessage trên Sidebar
      setConversations(prev => {
        const index = prev.findIndex(c => c.id === message.conversationId);
        if (index === -1) {
          // Phòng chat mới chưa có trong list, kéo lại hội thoại
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

        // Nếu tin nhắn thuộc phòng KHÔNG hoạt động, cộng unread count
        if (user && activeConversationRef.current?.id !== message.conversationId) {
          const updatedUnreads = { ...(conv.unreadCounts || {}) };
          updatedUnreads[user.id] = (updatedUnreads[user.id] || 0) + 1;
          conv.unreadCounts = updatedUnreads;
        }

        // Đưa cuộc hội thoại lên đầu danh sách
        updated.splice(index, 1);
        return [conv, ...updated];
      });

      // 2. Nếu tin nhắn thuộc phòng ĐANG mở, thêm vào khung chat
      if (activeConversationRef.current?.id === message.conversationId) {
        setMessages(prev => {
          if (prev.some(m => m.id === message.id)) return prev; // Chống trùng lặp
          return [message, ...prev];
        });

        // Tự động báo nhận đã xem tin nhắn nếu tin nhắn từ người khác gửi đến
        if (user && message.senderId !== user.id && isConnected) {
          send("/app/chat.seen", { conversationId: message.conversationId });
        }
      }
    });

    // Lắng nghe trạng thái gõ chữ cá nhân (Cho Direct chat)
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

    // Listen for DIRECT chat read receipts on personal queue
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

  // 2. Lắng nghe chi tiết cho phòng đang Active
  useEffect(() => {
    if (!isConnected || !activeConversation) return;

    const roomId = activeConversation.id;

    // Lắng nghe tin nhắn mới trực tiếp trong phòng
    const unsubscribeRoomMessages = subscribe<MessageResponse>(`/topic/chat.${roomId}`, (message) => {
      setMessages(prev => {
        if (prev.some(m => m.id === message.id)) return prev;
        return [message, ...prev];
      });

      // Cập nhật preview lastMessage trên Sidebar
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

        // Đưa cuộc hội thoại lên đầu danh sách
        updated.splice(index, 1);
        return [conv, ...updated];
      });

      // Tự động báo nhận đã xem tin nhắn nếu tin nhắn từ người khác gửi đến
      if (user && message.senderId !== user.id && isConnected) {
        send("/app/chat.seen", { conversationId: roomId });
      }
    });

    // Lắng nghe trạng thái soạn thảo trong phòng
    const unsubscribeRoomTyping = subscribe<TypingResponse>(`/topic/chat.${roomId}/typing`, (data) => {
      // Bỏ qua trạng thái soạn thảo của chính mình
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

    // Lắng nghe trạng thái đã đọc (Seen status)
    const unsubscribeRoomSeen = subscribe<{ conversationId: string; userId: string; readAt: string }>(`/topic/chat.${roomId}/seen`, (payload) => {
      try {
        setMessages(prev => prev.map(msg => {
          // Bổ sung tích đã đọc cho tin nhắn
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

  // 3. Heartbeat Ping Presence & Heartbeat định kỳ duy trì trạng thái
  useEffect(() => {
    if (!isConnected || !isAuthenticated) return;

    // ping lần đầu ngay khi connect
    send("/app/chat.ping", {});

    const interval = setInterval(() => {
      send("/app/chat.ping", {});
    }, 30000); // 30 giây ping một lần (khớp với spec: TTL 40s trên Redis)

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
