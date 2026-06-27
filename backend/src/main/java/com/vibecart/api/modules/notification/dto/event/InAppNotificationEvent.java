package com.vibecart.api.modules.notification.dto.event;

import lombok.*;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InAppNotificationEvent implements Serializable {
    private String eventId;
    private String recipientId;
    private String recipientUsername;
    private String actorId;
    private String actorUsername;
    private String actorFullName;
    private String actorAvatarUrl;
    private String type;
    private String referenceId;
    private String content;
}
