package com.vibecart.api.modules.notification.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.notification.dto.request.UpdatePreferencesRequest;
import com.vibecart.api.modules.notification.dto.response.NotificationResponse;
import com.vibecart.api.modules.notification.dto.response.PreferencesResponse;

public interface NotificationService {
    PageResponse<NotificationResponse> getNotifications(String username, int page, int size, String readStatus);
    long getUnreadCount(String username);
    void markAsRead(String notificationId, String username);
    void markAllAsRead(String username);
    void deleteNotification(String notificationId, String username);
    void deleteAllNotifications(String username);
    void processAndBroadcast(InAppNotificationEvent event);

    PreferencesResponse getPreferences(String username);
    PreferencesResponse updatePreferences(String username, UpdatePreferencesRequest request);
}
