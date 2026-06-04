package com.vibecart.api.modules.ecommerce.dto.response;

import java.math.BigDecimal;

public record CartItemResponse(
        String variantId,
        String spuId,
        String productName,
        String variantName,
        String thumbnailUrl,
        String creatorId,
        String creatorName,
        int quantity,
        BigDecimal originalPrice,
        BigDecimal discountPrice,
        int availableStock,
        String status
) {}
