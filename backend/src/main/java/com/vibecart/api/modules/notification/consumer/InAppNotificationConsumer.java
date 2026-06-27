package com.vibecart.api.modules.notification.consumer;

import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.retrytopic.DltStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InAppNotificationConsumer {

    private final NotificationService notificationService;

    @RetryableTopic(
            attempts = "3",
            backOff = @BackOff(delay = 2000, multiplier = 2.0),
            dltStrategy = DltStrategy.FAIL_ON_ERROR,
            exclude = {IllegalArgumentException.class}
    )
    @KafkaListener(
            topics = "in-app-notification-events",
            groupId = "vibecart-in-app-notification-group"
    )
    public void consume(@Payload InAppNotificationEvent event) {
        log.info("Received in-app notification event: type={}, actor={}, recipient={}",
                event.getType(), event.getActorUsername(), event.getRecipientUsername());
        notificationService.processAndBroadcast(event);
    }

    @DltHandler
    public void handleDeadLetter(@Payload InAppNotificationEvent event,
                                  @Header(KafkaHeaders.RECEIVED_TOPIC) String topic,
                                  @Header(KafkaHeaders.EXCEPTION_MESSAGE) String errorMessage) {
        log.error("CRITICAL - In-app notification failed all retries. Topic='{}', EventId='{}', " +
                        "Actor='{}', Recipient='{}', Type='{}', Error: {}",
                topic, event.getEventId(), event.getActorUsername(),
                event.getRecipientUsername(), event.getType(), errorMessage);
    }
}
