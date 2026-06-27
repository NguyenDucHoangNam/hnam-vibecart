package com.vibecart.api.modules.ecommerce.event;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.entity.ProductImage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
@Component
@Slf4j
@RequiredArgsConstructor
public class ProductSyncProducer {

    private final KafkaTemplate<String, ProductSyncEvent> kafkaTemplate;

    public void sendProductCreatedEvent(Product product) {
        sendEvent("CREATED", product);
    }

    public void sendProductUpdatedEvent(Product product) {
        sendEvent("UPDATED", product);
    }

    public void sendProductDeletedEvent(String productId) {
        ProductSyncEvent event = ProductSyncEvent.builder()
                .eventType("DELETED")
                .timestamp(ZonedDateTime.now())
                .productId(productId)
                .build();

        log.info("Sending DELETED event for product: {}", productId);
        kafkaTemplate.send(KafkaTopicConfig.PRODUCT_SYNC_TOPIC, productId, event);
    }

    private void sendEvent(String eventType, Product product) {

        String thumbnailUrl = null;
        if (product.getImages() != null) {
            thumbnailUrl = product.getImages().stream()
                    .filter(ProductImage::isThumbnail)
                    .map(ProductImage::getImageUrl)
                    .findFirst()
                    .orElse(product.getImages().isEmpty() ? null : product.getImages().get(0).getImageUrl());
        }


        BigDecimal minPrice = BigDecimal.ZERO;
        BigDecimal maxPrice = BigDecimal.ZERO;
        if (product.getVariants() != null && !product.getVariants().isEmpty()) {
            minPrice = product.getVariants().stream()
                    .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                            ? v.getDiscountPrice() : v.getPrice())
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
            maxPrice = product.getVariants().stream()
                    .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                            ? v.getDiscountPrice() : v.getPrice())
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO);
        }

        ProductSyncEvent event = ProductSyncEvent.builder()
                .eventType(eventType)
                .timestamp(ZonedDateTime.now())
                .productId(product.getId())
                .name(product.getName())
                .description(product.getDescription())
                .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                .creatorId(product.getCreatorId())
                .thumbnailUrl(thumbnailUrl)
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .status(product.getStatus().name())
                .build();

        log.info("Sending {} event for product: {}", eventType, product.getId());
        kafkaTemplate.send(KafkaTopicConfig.PRODUCT_SYNC_TOPIC, product.getId(), event);
    }
}
