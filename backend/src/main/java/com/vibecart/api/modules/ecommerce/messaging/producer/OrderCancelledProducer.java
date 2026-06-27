package com.vibecart.api.modules.ecommerce.messaging.producer;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.dto.event.OrderCancelledEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderCancelledProducer {

    private final KafkaTemplate<String, OrderCancelledEvent> kafkaTemplate;

    public void sendOrderCancelledEvent(OrderCancelledEvent event) {
        log.info("Sending ORDER_CANCELLED event for order: {}", event.getOrderId());
        kafkaTemplate.send(KafkaTopicConfig.ORDER_CANCELLED_TOPIC, event.getOrderId(), event);
    }
}
