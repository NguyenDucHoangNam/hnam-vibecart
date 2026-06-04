package com.vibecart.api.modules.ecommerce.event;

import com.vibecart.api.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderPaidProducer {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderPaidProducer.class);

    private final KafkaTemplate<String, OrderPaidEvent> kafkaTemplate;

    public void sendOrderPaidEvent(OrderPaidEvent event) {
        log.info("Sending ORDER_PAID event for order: {}, email: {}",
                event.getOrderId(), event.getUserEmail());
        kafkaTemplate.send(KafkaTopicConfig.ORDER_PAID_TOPIC, event.getOrderId(), event);
    }
}
