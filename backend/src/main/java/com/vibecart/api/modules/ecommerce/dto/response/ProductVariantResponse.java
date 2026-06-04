package com.vibecart.api.modules.ecommerce.dto.response;

import java.math.BigDecimal;

public record ProductVariantResponse(
        String id,
        String skuCode,
        String variantName,
        BigDecimal price,
        BigDecimal discountPrice,
        Integer quantity,
        Integer reservedQuantity,
        Integer availableStock,
        String status
) {}
