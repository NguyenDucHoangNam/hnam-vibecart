package com.vibecart.api.modules.ecommerce.dto.response;

import java.time.ZonedDateTime;
import java.util.List;

public record CheckoutResponse(
        String checkoutSessionId,
        List<OrderResponse> subOrders,
        ZonedDateTime createdAt
) {}
