package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.constraints.NotNull;

public record InventoryAdjustRequest(
    @NotNull(message = "Adjustment quantity is required")
    Integer adjustmentQuantity,

    String reason
) {}
