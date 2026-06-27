package com.vibecart.api.modules.search.consumer;

import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.ecommerce.document.ProductDocument;
import com.vibecart.api.modules.ecommerce.repository.ProductSearchRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import com.vibecart.api.modules.ecommerce.dto.event.ProductSyncEvent;
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
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

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
        BigDecimal[] prices = new BigDecimal[4];
        prices[0] = event.getMinPrice();
        prices[1] = event.getMaxPrice();
        prices[2] = prices[0];
        prices[3] = prices[1];

        try {
            transactionTemplate.executeWithoutResult(status -> {
                var productOpt = productRepository.findById(event.getProductId());
                if (productOpt.isPresent()) {
                    var product = productOpt.get();
                    if (product.getVariants() != null && !product.getVariants().isEmpty()) {

                        var activeVariants = product.getVariants().stream()
                                .filter(v -> !v.isDeleted() && "ACTIVE".equals(v.getStatus().name()))
                                .toList();
                        
                        if (!activeVariants.isEmpty()) {
                            prices[0] = activeVariants.stream()
                                    .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                            ? v.getDiscountPrice() : v.getPrice())
                                    .min(BigDecimal::compareTo)
                                    .orElse(BigDecimal.ZERO);
                                    
                            prices[1] = activeVariants.stream()
                                    .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                            ? v.getDiscountPrice() : v.getPrice())
                                    .max(BigDecimal::compareTo)
                                    .orElse(BigDecimal.ZERO);

                            prices[2] = activeVariants.stream()
                                    .map(v -> v.getPrice())
                                    .min(BigDecimal::compareTo)
                                    .orElse(BigDecimal.ZERO);
                                    
                            prices[3] = activeVariants.stream()
                                    .map(v -> v.getPrice())
                                    .max(BigDecimal::compareTo)
                                    .orElse(BigDecimal.ZERO);
                        }
                    }
                }
            });
        } catch (Exception e) {
            log.error("Failed to recalculate price bounds from database for product: {}. Using event payload bounds.", event.getProductId(), e);
        }

        BigDecimal minPrice = prices[0];
        BigDecimal maxPrice = prices[1];
        BigDecimal minOriginalPrice = prices[2];
        BigDecimal maxOriginalPrice = prices[3];

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
                .minOriginalPrice(minOriginalPrice)
                .maxOriginalPrice(maxOriginalPrice)
                .status(event.getStatus())
                .updatedAt(event.getTimestamp())
                .build();

        productSearchRepository.save(document);
        log.info("Indexed product in Elasticsearch: {} (minPrice={}, maxPrice={}, minOriginalPrice={}, maxOriginalPrice={})", 
                event.getProductId(), minPrice, maxPrice, minOriginalPrice, maxOriginalPrice);
    }

    private void deleteProduct(String productId) {
        productSearchRepository.deleteById(productId);
        log.info("Deleted product from Elasticsearch: {}", productId);
    }
}
