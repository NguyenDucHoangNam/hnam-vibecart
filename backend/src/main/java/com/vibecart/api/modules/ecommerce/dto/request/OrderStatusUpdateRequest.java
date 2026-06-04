package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record OrderStatusUpdateRequest(
    @NotBlank(message = "New status is required")
    String newStatus,

    String trackingNumber
) {}
