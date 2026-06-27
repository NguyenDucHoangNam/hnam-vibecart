package com.vibecart.api.modules.search.dto.response;

import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProductSearchItemResponse {
    private String id;
    private String name;
    private String categoryId;
    private String categoryName;
    private String creatorId;
    private String thumbnailUrl;
    private BigDecimal minPrice;
    private BigDecimal maxPrice;
    private BigDecimal minOriginalPrice;
    private BigDecimal maxOriginalPrice;
    private String status;
    private ZonedDateTime createdAt;
}
