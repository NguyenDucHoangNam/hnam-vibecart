package com.vibecart.api.modules.iam.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.jobs.dto.NotificationEvent;
import com.vibecart.api.modules.search.entity.OutboxEvent;
import com.vibecart.api.modules.search.repository.OutboxEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class IamOutboxScheduler {

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Scheduled(fixedDelay = 500)
    @Transactional
    public void processIamPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository
                .findByAggregateTypeAndStatusOrderByCreatedAtAsc("IAM", "PENDING");

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending IAM outbox events to process", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                NotificationEvent notificationEvent = objectMapper.readValue(event.getPayload(), NotificationEvent.class);

                // Publish to Kafka and wait for ACK synchronously to ensure at-least-once delivery
                kafkaTemplate.send(KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC, event.getAggregateId(), notificationEvent).get();

                event.setStatus("PROCESSED");
                outboxEventRepository.save(event);
                log.info("Successfully published IAM outbox notification event {} to Kafka topic {}", 
                        event.getId(), KafkaTopicConfig.NOTIFICATION_EVENTS_TOPIC);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("Failed to parse IAM outbox event payload. Event ID: {}. Payload: {}", 
                        event.getId(), event.getPayload(), e);
                event.setStatus("FAILED");
                outboxEventRepository.save(event);
            } catch (InterruptedException e) {
                log.error("IAM Outbox scheduler interrupted while sending event: {}", event.getId(), e);
                Thread.currentThread().interrupt();
                break; // Stop loop and retry next time
            } catch (Exception e) {
                log.error("Transient error publishing IAM outbox event {} to Kafka. Will retry in next run.", 
                        event.getId(), e);
                break; // Preserve ordering; event remains PENDING
            }
        }
    }
}
