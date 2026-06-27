package com.vibecart.api.modules.ecommerce.messaging.producer;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.dto.event.OrderPaidEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class OrderPaidProducer {

    private final KafkaTemplate<String, OrderPaidEvent> kafkaTemplate;

    public void sendOrderPaidEvent(OrderPaidEvent event) {
        log.info("Sending ORDER_PAID event for order: {}, email: {}",
                event.getOrderId(), event.getUserEmail());
        kafkaTemplate.send(KafkaTopicConfig.ORDER_PAID_TOPIC, event.getOrderId(), event);
    }
}
