package com.vibecart.api.modules.jobs.dto.event;

import lombok.*;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationEvent {
    private String eventId;
    private String recipientEmail;
    private String subject;
    private String body;
    private String templateType;
    private Map<String, Object> templateParams;
}
