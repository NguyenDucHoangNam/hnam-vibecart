package com.vibecart.api.modules.search.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.event.ProductSyncEvent;
import com.vibecart.api.modules.search.entity.OutboxEvent;
import com.vibecart.api.modules.search.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Scheduler xử lý các sự kiện Outbox chờ gửi sang Kafka để đồng bộ dữ liệu sản phẩm.
 */
@Component
public class OutboxScheduler {

    private static final Logger log = LoggerFactory.getLogger(OutboxScheduler.class);

    private final OutboxEventRepository outboxEventRepository;
    private final KafkaTemplate<String, ProductSyncEvent> kafkaTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Khởi tạo OutboxScheduler với các dependency cần thiết.
     */
    public OutboxScheduler(OutboxEventRepository outboxEventRepository,
                           KafkaTemplate<String, ProductSyncEvent> kafkaTemplate,
                           ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Xử lý các sự kiện Outbox đang chờ (PENDING), gửi đồng bộ sang Kafka để đảm bảo at-least-once delivery.
     */
    @Scheduled(fixedDelay = 500)
    public void processPendingEvents() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByAggregateTypeAndStatusOrderByCreatedAtAsc("PRODUCT", "PENDING");
        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Found {} pending outbox events to process", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                ProductSyncEvent syncEvent = objectMapper.readValue(event.getPayload(), ProductSyncEvent.class);

                kafkaTemplate.send(KafkaTopicConfig.PRODUCT_SYNC_TOPIC, syncEvent.getProductId(), syncEvent).get();

                event.setStatus("PROCESSED");
                outboxEventRepository.save(event);
                log.info("Successfully published outbox event {} to Kafka topic {}", event.getId(), KafkaTopicConfig.PRODUCT_SYNC_TOPIC);
            } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
                log.error("Failed to parse outbox event payload. Event ID: {}. Payload: {}", event.getId(), event.getPayload(), e);
                event.setStatus("FAILED");
                outboxEventRepository.save(event);
            } catch (InterruptedException e) {
                log.error("Outbox scheduler interrupted while sending event: {}", event.getId(), e);
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Transient error publishing outbox event {} to Kafka. Will retry in next run.", event.getId(), e);
                break;
            }
        }
    }
}
