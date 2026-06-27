"use client";

import React, { createContext, useContext, useState, useEffect, useCallback, useRef, useMemo } from "react";
import { useAuth } from "@/hooks/useAuth";
import { useToast } from "@/context/ToastContext";
import { useWebSocket } from "@/hooks/useWebSocket";
import { notificationService } from "@/services/notification.service";
import { NotificationResponse } from "@/types/notification";

interface NotificationContextType {
  notifications: NotificationResponse[];
  unreadCount: number;
  isLoading: boolean;
  hasMore: boolean;
  activeTab: "ALL" | "UNREAD";
  recentNotifications: NotificationResponse[];
  earlierNotifications: NotificationResponse[];
  isConnected: boolean;

  setActiveTab: (tab: "ALL" | "UNREAD") => void;
  fetchNotifications: () => Promise<void>;
  loadMore: () => Promise<void>;
  markAsRead: (id: string) => void;
  markAllAsRead: () => void;
  deleteNotification: (id: string) => void;
  deleteAllNotifications: () => void;
}

const NotificationContext = createContext<NotificationContextType | undefined>(undefined);

export function NotificationProvider({ children }: { children: React.ReactNode }) {
  const { isAuthenticated } = useAuth();
  const toast = useToast();
  const { isConnected, subscribe } = useWebSocket();

  const [notifications, setNotifications] = useState<NotificationResponse[]>([]);
  const [unreadCount, setUnreadCount] = useState(0);
  const [isLoading, setIsLoading] = useState(false);
  const [hasMore, setHasMore] = useState(true);
  const [page, setPage] = useState(0);
  const [activeTab, setActiveTabState] = useState<"ALL" | "UNREAD">("ALL");

  const audioRef = useRef<HTMLAudioElement | null>(null);
  const initializedRef = useRef(false);

  useEffect(() => {
    if (typeof window !== "undefined") {
      audioRef.current = new Audio("/sounds/notification.wav");
      audioRef.current.volume = 0.5;
    }
  }, []);

  const playNotificationSound = useCallback(() => {
    if (audioRef.current) {
      audioRef.current.currentTime = 0;
      audioRef.current.play().catch(() => {});
    }
  }, []);

  const fetchNotifications = useCallback(async () => {
    if (!isAuthenticated) return;
    setIsLoading(true);
    try {
      const data = await notificationService.getNotifications(0, 10, activeTab);
      setNotifications(data.content);
      setHasMore(!data.last);
      setPage(0);
    } catch (err) {
      console.error("Failed to fetch notifications:", err);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated, activeTab]);

  const loadMore = useCallback(async () => {
    if (!isAuthenticated || isLoading || !hasMore) return;
    setIsLoading(true);
    try {
      const nextPage = page + 1;
      const data = await notificationService.getNotifications(nextPage, 10, activeTab);
      setNotifications((prev) => [...prev, ...data.content]);
      setHasMore(!data.last);
      setPage(nextPage);
    } catch (err) {
      console.error("Failed to load more notifications:", err);
    } finally {
      setIsLoading(false);
    }
  }, [isAuthenticated, isLoading, hasMore, page, activeTab]);

  const fetchUnreadCount = useCallback(async () => {
    if (!isAuthenticated) return;
    try {
      const count = await notificationService.getUnreadCount();
      setUnreadCount(count);
    } catch (err) {
      console.error("Failed to fetch unread count:", err);
    }
  }, [isAuthenticated]);

  useEffect(() => {
    if (isAuthenticated && !initializedRef.current) {
      initializedRef.current = true;
      fetchNotifications();
      fetchUnreadCount();
    }
    if (!isAuthenticated) {
      initializedRef.current = false;
      setNotifications([]);
      setUnreadCount(0);
      setPage(0);
      setHasMore(true);
    }
  }, [isAuthenticated, fetchNotifications, fetchUnreadCount]);

  useEffect(() => {
    if (!isConnected || !isAuthenticated) return;

    const unsubscribe = subscribe<NotificationResponse>("/user/queue/notifications", (notification) => {
      try {
        setNotifications((prev) => [notification, ...prev]);
        setUnreadCount((prev) => prev + 1);
        if (notification.sendSound !== false) {
          playNotificationSound();
        }
        toast.success(notification.content);
      } catch (err) {
        console.error("Failed to process notification:", err);
      }
    });

    return () => {
      if (unsubscribe) unsubscribe();
    };
  }, [isConnected, isAuthenticated, subscribe, playNotificationSound, toast]);

  const setActiveTab = useCallback(
    (tab: "ALL" | "UNREAD") => {
      setActiveTabState(tab);
      setPage(0);
      setHasMore(true);
      setNotifications([]);
    },
    []
  );

  useEffect(() => {
    if (isAuthenticated && initializedRef.current) {
      fetchNotifications();
    }
  }, [activeTab, fetchNotifications, isAuthenticated]);

  const markAsRead = useCallback((id: string) => {
    setNotifications((prev) =>
      prev.map((n) => (n.id === id ? { ...n, read: true } : n))
    );
    setUnreadCount((prev) => Math.max(0, prev - 1));

    notificationService.markAsRead(id).catch(() => {
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, read: false } : n))
      );
      setUnreadCount((prev) => prev + 1);
      toast.error("Không thể đánh dấu đã đọc");
    });
  }, [toast]);

  const markAllAsRead = useCallback(() => {
    const prevNotifications = [...notifications];
    const prevCount = unreadCount;

    setNotifications((prev) => prev.map((n) => ({ ...n, read: true })));
    setUnreadCount(0);

    notificationService.markAllAsRead().catch(() => {
      setNotifications(prevNotifications);
      setUnreadCount(prevCount);
      toast.error("Không thể đánh dấu tất cả đã đọc");
    });
  }, [notifications, unreadCount, toast]);

  const deleteNotification = useCallback(
    (id: string) => {
      const backup = notifications.find((n) => n.id === id);
      const wasUnread = backup && !backup.read;

      setNotifications((prev) => prev.filter((n) => n.id !== id));
      if (wasUnread) setUnreadCount((prev) => Math.max(0, prev - 1));

      notificationService.deleteNotification(id).catch(() => {
        if (backup) {
          setNotifications((prev) =>
            [...prev, backup].sort(
              (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime()
            )
          );
        }
        if (wasUnread) setUnreadCount((prev) => prev + 1);
        toast.error("Không thể xóa thông báo");
      });
    },
    [notifications, toast]
  );

  const deleteAllNotifications = useCallback(() => {
    const prevNotifications = [...notifications];
    const prevCount = unreadCount;

    setNotifications([]);
    setUnreadCount(0);

    notificationService.deleteAllNotifications().catch(() => {
      setNotifications(prevNotifications);
      setUnreadCount(prevCount);
      toast.error("Không thể xóa tất cả thông báo");
    });
  }, [notifications, unreadCount, toast]);

  const now = useMemo(() => Date.now(), [notifications]);
  const twentyFourHoursAgo = now - 24 * 60 * 60 * 1000;

  const recentNotifications = useMemo(
    () => notifications.filter((n) => new Date(n.createdAt).getTime() > twentyFourHoursAgo),
    [notifications, twentyFourHoursAgo]
  );

  const earlierNotifications = useMemo(
    () => notifications.filter((n) => new Date(n.createdAt).getTime() <= twentyFourHoursAgo),
    [notifications, twentyFourHoursAgo]
  );

  const value: NotificationContextType = {
    notifications,
    unreadCount,
    isLoading,
    hasMore,
    activeTab,
    recentNotifications,
    earlierNotifications,
    isConnected,
    setActiveTab,
    fetchNotifications,
    loadMore,
    markAsRead,
    markAllAsRead,
    deleteNotification,
    deleteAllNotifications,
  };

  return (
    <NotificationContext.Provider value={value}>
      {children}
    </NotificationContext.Provider>
  );
}

export function useNotification() {
  const context = useContext(NotificationContext);
  if (!context) {
    throw new Error("useNotification must be used within a NotificationProvider");
  }
  return context;
}
