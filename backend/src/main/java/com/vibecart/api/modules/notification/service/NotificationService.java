package com.vibecart.api.modules.notification.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.notification.dto.request.UpdatePreferencesRequest;
import com.vibecart.api.modules.notification.dto.response.NotificationResponse;
import com.vibecart.api.modules.notification.dto.response.PreferencesResponse;

public interface NotificationService {
    PageResponse<NotificationResponse> getNotifications(String userId, int page, int size, String readStatus);
    long getUnreadCount(String userId);
    void markAsRead(String notificationId, String userId);
    void markAllAsRead(String userId);
    void deleteNotification(String notificationId, String userId);
    void deleteAllNotifications(String userId);
    void processAndBroadcast(InAppNotificationEvent event);

    PreferencesResponse getPreferences(String userId);
    PreferencesResponse updatePreferences(String userId, UpdatePreferencesRequest request);
}
