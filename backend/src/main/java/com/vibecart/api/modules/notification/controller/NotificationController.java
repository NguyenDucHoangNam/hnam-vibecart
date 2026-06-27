package com.vibecart.api.modules.notification.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.util.SecurityUtils;
import com.vibecart.api.modules.notification.dto.request.UpdatePreferencesRequest;
import com.vibecart.api.modules.notification.dto.response.NotificationResponse;
import com.vibecart.api.modules.notification.dto.response.PreferencesResponse;
import com.vibecart.api.modules.notification.service.NotificationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
@Slf4j
@Validated
@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<NotificationResponse>>> getNotifications(
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size,
            @RequestParam(defaultValue = "ALL") String readStatus) {

        String userId = SecurityUtils.getCurrentUserId();
        PageResponse<NotificationResponse> result = notificationService.getNotifications(userId, page, size, readStatus);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<NotificationResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách thông báo thành công")
                        .result(result)
                        .build());
    }

    @GetMapping("/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount() {
        String userId = SecurityUtils.getCurrentUserId();
        long count = notificationService.getUnreadCount(userId);

        return ResponseEntity.ok(
                ApiResponse.<Long>builder()
                        .code(1000)
                        .result(count)
                        .build());
    }

    @PutMapping("/{id}/read")
    public ResponseEntity<ApiResponse<Void>> markAsRead(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.markAsRead(id, userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã đánh dấu đã đọc")
                        .build());
    }

    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Void>> markAllAsRead() {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.markAllAsRead(userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã đánh dấu tất cả đã đọc")
                        .build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> deleteNotification(@PathVariable String id) {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.deleteNotification(id, userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa thông báo")
                        .build());
    }

    @DeleteMapping
    public ResponseEntity<ApiResponse<Void>> deleteAllNotifications() {
        String userId = SecurityUtils.getCurrentUserId();
        notificationService.deleteAllNotifications(userId);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa tất cả thông báo")
                        .build());
    }

    @GetMapping("/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> getPreferences() {
        String userId = SecurityUtils.getCurrentUserId();
        PreferencesResponse result = notificationService.getPreferences(userId);

        return ResponseEntity.ok(
                ApiResponse.<PreferencesResponse>builder()
                        .code(1000)
                        .result(result)
                        .build());
    }

    @PutMapping("/preferences")
    public ResponseEntity<ApiResponse<PreferencesResponse>> updatePreferences(
            @Valid @RequestBody UpdatePreferencesRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        PreferencesResponse result = notificationService.updatePreferences(userId, request);

        return ResponseEntity.ok(
                ApiResponse.<PreferencesResponse>builder()
                        .code(1000)
                        .message("Đã cập nhật cài đặt thông báo")
                        .result(result)
                        .build());
    }
}
