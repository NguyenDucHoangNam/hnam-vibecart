import { api } from "@/lib/api-client";
import { ENDPOINTS } from "@/constants/api";
import { NotificationResponse } from "@/types/notification";

interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  last: boolean;
}

export const notificationService = {
  getNotifications: async (
    page = 0,
    size = 10,
    readStatus: "ALL" | "UNREAD" = "ALL"
  ): Promise<PageResponse<NotificationResponse>> => {
    return api.get<PageResponse<NotificationResponse>>(ENDPOINTS.NOTIFICATIONS.LIST, {
      params: { page, size, readStatus },
    });
  },

  getUnreadCount: async (): Promise<number> => {
    return api.get<number>(ENDPOINTS.NOTIFICATIONS.UNREAD_COUNT);
  },

  markAsRead: async (id: string): Promise<void> => {
    await api.put(ENDPOINTS.NOTIFICATIONS.READ(id));
  },

  markAllAsRead: async (): Promise<void> => {
    await api.put(ENDPOINTS.NOTIFICATIONS.READ_ALL);
  },

  deleteNotification: async (id: string): Promise<void> => {
    await api.delete(ENDPOINTS.NOTIFICATIONS.DELETE(id));
  },

  deleteAllNotifications: async (): Promise<void> => {
    await api.delete(ENDPOINTS.NOTIFICATIONS.DELETE_ALL);
  },
};
