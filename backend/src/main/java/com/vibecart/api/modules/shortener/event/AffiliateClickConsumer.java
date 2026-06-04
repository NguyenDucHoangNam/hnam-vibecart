package com.vibecart.api.modules.shortener.event;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.shortener.entity.ClickEvent;
import com.vibecart.api.modules.shortener.entity.ShortLink;
import com.vibecart.api.modules.shortener.repository.ClickEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AffiliateClickConsumer {

    private final ClickEventRepository clickEventRepository;
    private final List<ClickEventMessage> buffer = Collections.synchronizedList(new ArrayList<>());

    private static final int BATCH_SIZE = 50;

    @KafkaListener(
            topics = KafkaTopicConfig.AFFILIATE_CLICKS_TOPIC,
            groupId = "affiliate-click-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleClickEvent(ClickEventMessage message) {
        log.debug("Received Kafka click event for ShortLink: {}", message.getShortLinkId());
        buffer.add(message);

        if (buffer.size() >= BATCH_SIZE) {
            flushBuffer();
        }
    }

    @Scheduled(fixedRate = 3000)
    public void periodicFlush() {
        flushBuffer();
    }

    private synchronized void flushBuffer() {
        if (buffer.isEmpty()) {
            return;
        }

        List<ClickEventMessage> toSave = new ArrayList<>(buffer);
        buffer.clear();

        log.info("Flushing {} click events to PostgreSQL in batch...", toSave.size());

        List<ClickEvent> entities = toSave.stream().map(msg -> {
            ShortLink shortLink = new ShortLink();
            shortLink.setId(msg.getShortLinkId());

            return ClickEvent.builder()
                    .shortLink(shortLink)
                    .clickTime(msg.getClickTime())
                    .ipAddress(msg.getIpAddress())
                    .userAgent(msg.getUserAgent())
                    .deviceType(msg.getDeviceType())
                    .browser(msg.getBrowser())
                    .country(msg.getCountry())
                    .build();
        }).toList();

        try {
            clickEventRepository.saveAll(entities);
            log.info("Successfully saved batch of {} click events.", entities.size());
        } catch (Exception e) {
            log.error("Failed to write click events batch to database: {}", e.getMessage(), e);
        }
    }
}
