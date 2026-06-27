package com.vibecart.api.modules.ecommerce.event;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.document.ProductDocument;
import com.vibecart.api.modules.ecommerce.repository.ProductSearchRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
@Component
@Slf4j
@RequiredArgsConstructor
public class SearchSyncConsumer {

    private final ProductSearchRepository productSearchRepository;
    private final ProductRepository productRepository;

    @KafkaListener(
            topics = KafkaTopicConfig.PRODUCT_SYNC_TOPIC,
            groupId = "search-sync-group",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void handleProductSyncEvent(ProductSyncEvent event) {
        log.info("Received product sync event: type={}, productId={}",
                event.getEventType(), event.getProductId());

        try {
            switch (event.getEventType()) {
                case "CREATED", "UPDATED" -> indexProduct(event);
                case "DELETED" -> deleteProduct(event.getProductId());
                default -> log.warn("Unknown event type: {}", event.getEventType());
            }
        } catch (Exception e) {
            log.error("Failed to process product sync event for product: {}",
                    event.getProductId(), e);
            throw e;
        }
    }

    private void indexProduct(ProductSyncEvent event) {
        BigDecimal minPrice = event.getMinPrice();
        BigDecimal maxPrice = event.getMaxPrice();

        try {
            var productOpt = productRepository.findById(event.getProductId());
            if (productOpt.isPresent()) {
                var product = productOpt.get();
                if (product.getVariants() != null && !product.getVariants().isEmpty()) {

                    var activeVariants = product.getVariants().stream()
                            .filter(v -> !v.isDeleted() && "ACTIVE".equals(v.getStatus().name()))
                            .toList();
                    
                    if (!activeVariants.isEmpty()) {
                        minPrice = activeVariants.stream()
                                .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                        ? v.getDiscountPrice() : v.getPrice())
                                .min(BigDecimal::compareTo)
                                .orElse(BigDecimal.ZERO);
                                
                        maxPrice = activeVariants.stream()
                                .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                        ? v.getDiscountPrice() : v.getPrice())
                                .max(BigDecimal::compareTo)
                                .orElse(BigDecimal.ZERO);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Failed to recalculate price bounds from database for product: {}. Using event payload bounds.", event.getProductId(), e);
        }

        ProductDocument document = ProductDocument.builder()
                .id(event.getProductId())
                .name(event.getName())
                .description(event.getDescription())
                .categoryId(event.getCategoryId())
                .categoryName(event.getCategoryName())
                .creatorId(event.getCreatorId())
                .thumbnailUrl(event.getThumbnailUrl())
                .minPrice(minPrice)
                .maxPrice(maxPrice)
                .status(event.getStatus())
                .updatedAt(event.getTimestamp())
                .build();

        productSearchRepository.save(document);
        log.info("Indexed product in Elasticsearch: {} (minPrice={}, maxPrice={})", event.getProductId(), minPrice, maxPrice);
    }

    private void deleteProduct(String productId) {
        productSearchRepository.deleteById(productId);
        log.info("Deleted product from Elasticsearch: {}", productId);
    }
}
