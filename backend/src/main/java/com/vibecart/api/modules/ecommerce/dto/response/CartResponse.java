package com.vibecart.api.modules.ecommerce.dto.response;

import java.math.BigDecimal;
import java.util.List;

public record CartResponse(
        List<CartItemResponse> items,
        BigDecimal totalOriginalAmount,
        BigDecimal totalDiscountAmount,
        BigDecimal totalSavingAmount
) {}
