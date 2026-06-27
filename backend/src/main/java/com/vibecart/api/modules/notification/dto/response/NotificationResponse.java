package com.vibecart.api.modules.notification.dto.response;

import lombok.*;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private String id;
    private String type;
    private String content;
    private boolean isRead;
    private Instant createdAt;
    private ActorInfo actor;
    private String referenceId;
    private boolean sendSound = true;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ActorInfo {
        private String id;
        private String username;
        private String fullName;
        private String avatarUrl;
    }
}
