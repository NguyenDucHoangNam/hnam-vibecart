package com.vibecart.api.modules.ecommerce.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSyncEvent {
    private String eventType; // CREATED, UPDATED, DELETED
    private ZonedDateTime timestamp;
    private String productId;
    private String name;
    private String description;
    private String categoryId;
    private String categoryName;
    private String creatorId;
    private String thumbnailUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private String status;
}
