package com.vibecart.api.modules.notification.consumer;

import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationConsumer {

    private final NotificationService notificationService;

    @KafkaListener(
            topics = "in-app-notification-events",
            groupId = "vibecart-in-app-notification-group"
    )
    public void consume(InAppNotificationEvent event) {
        log.info("Received in-app notification event: type={}, actor={}, recipient={}",
                event.getType(), event.getActorUsername(), event.getRecipientUsername());
        try {
            notificationService.processAndBroadcast(event);
        } catch (Exception e) {
            log.error("Failed to process in-app notification event: {}", e.getMessage(), e);
        }
    }
}
