package com.vibecart.api.modules.ecommerce.dto.response;

import java.time.ZonedDateTime;

public record InventoryHistoryResponse(
        String id,
        String transactionType,
        Integer quantityChanged,
        String reason,
        String createdBy,
        ZonedDateTime createdAt
) {}
