package com.vibecart.api.modules.ecommerce.dto.response;

import java.math.BigDecimal;

public record OrderItemResponse(
        String variantId,
        String productName,
        String variantName,
        BigDecimal price,
        BigDecimal discountPrice,
        Integer quantity
) {}
