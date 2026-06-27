package com.vibecart.api.modules.ecommerce.event;

import com.vibecart.api.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderDeliveredProducer {

    private final KafkaTemplate<String, OrderDeliveredEvent> kafkaTemplate;

    public void sendOrderDeliveredEvent(OrderDeliveredEvent event) {
        log.info("Sending ORDER_DELIVERED event for order: {}", event.getOrderId());
        kafkaTemplate.send(KafkaTopicConfig.ORDER_DELIVERED_TOPIC, event.getOrderId(), event);
    }
}
