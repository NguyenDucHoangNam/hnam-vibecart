package com.vibecart.api.modules.notification.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.util.SecurityUtils;
import com.vibecart.api.modules.notification.dto.request.UpdatePreferencesRequest;
import com.vibecart.api.modules.notification.dto.response.NotificationResponse;
import com.vibecart.api.modules.notification.dto.response.PreferencesResponse;
import com.vibecart.api.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "ALL") String readStatus) {

        String username = SecurityUtils.getCurrentUsername();
        PageResponse<NotificationResponse> result = notificationService.getNotifications(username, page, size, readStatus);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<NotificationResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách thông báo thành công")
                        .result(result)
                        .build());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        String username = SecurityUtils.getCurrentUsername();
        long count = notificationService.getUnreadCount(username);

        return ResponseEntity.ok(
                ApiResponse.<Long>builder()
                        .code(1000)
                        .result(count)
                        .build());
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String id) {
        String username = SecurityUtils.getCurrentUsername();
        notificationService.markAsRead(id, username);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã đánh dấu đã đọc")
                        .build());
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        String username = SecurityUtils.getCurrentUsername();
        notificationService.markAllAsRead(username);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã đánh dấu tất cả đã đọc")
                        .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable String id) {
        String username = SecurityUtils.getCurrentUsername();
        notificationService.deleteNotification(id, username);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa thông báo")
                        .build());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAllNotifications() {
        String username = SecurityUtils.getCurrentUsername();
        notificationService.deleteAllNotifications(username);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa tất cả thông báo")
                        .build());
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> getPreferences() {
        String username = SecurityUtils.getCurrentUsername();
        PreferencesResponse result = notificationService.getPreferences(username);

        return ResponseEntity.ok(
                ApiResponse.<PreferencesResponse>builder()
                        .code(1000)
                        .result(result)
                        .build());
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> updatePreferences(
            @RequestBody UpdatePreferencesRequest request) {
        String username = SecurityUtils.getCurrentUsername();
        PreferencesResponse result = notificationService.updatePreferences(username, request);

        return ResponseEntity.ok(
                ApiResponse.<PreferencesResponse>builder()
                        .code(1000)
                        .message("Đã cập nhật cài đặt thông báo")
                        .result(result)
                        .build());
    }
}
