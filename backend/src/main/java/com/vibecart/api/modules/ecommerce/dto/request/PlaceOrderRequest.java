package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record PlaceOrderRequest(
        @NotEmpty(message = "Order items cannot be empty")
        List<@Valid OrderItemRequest> items,
        @NotBlank(message = "Shipping address is required")
        String shippingAddress,
        @NotBlank(message = "Recipient name is required")
        String recipientName,
        @NotBlank(message = "Recipient phone is required")
        String recipientPhone,
        String customerNote,
        String voucherCode
) {}
