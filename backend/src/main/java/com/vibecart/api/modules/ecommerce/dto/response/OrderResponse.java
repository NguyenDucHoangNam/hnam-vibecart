package com.vibecart.api.modules.ecommerce.dto.response;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.List;

public record OrderResponse(
        String orderId,
        String orderCode,
        String creatorId,
        String creatorName,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        List<OrderItemResponse> items,
        BigDecimal totalAmount,
        BigDecimal discountAmount,
        BigDecimal finalAmount,
        String status,
        String paymentUrl,
        ZonedDateTime createdAt,
        String trackingNumber
) {}
