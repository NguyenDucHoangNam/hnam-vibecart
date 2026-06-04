package com.vibecart.api.modules.jobs.dto;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String eventId;
    private String recipientEmail;
    private String subject;
    private String body;
}
