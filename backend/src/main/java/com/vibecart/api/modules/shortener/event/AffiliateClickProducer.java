package com.vibecart.api.modules.shortener.event;

import com.vibecart.api.config.KafkaTopicConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Producer gửi sự kiện click tiếp thị liên kết sang Kafka.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AffiliateClickProducer {

    private final KafkaTemplate<String, ClickEventMessage> kafkaTemplate;

    public void sendClickEvent(ClickEventMessage message) {
        log.info("Sending click event for ShortLink ID: {}", message.getShortLinkId());
        kafkaTemplate.send(KafkaTopicConfig.AFFILIATE_CLICKS_TOPIC, message.getShortLinkId(), message);
    }
}
