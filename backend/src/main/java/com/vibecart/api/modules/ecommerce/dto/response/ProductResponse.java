package com.vibecart.api.modules.ecommerce.dto.response;

import java.time.ZonedDateTime;
import java.util.List;

public record ProductResponse(
        String id,
        String name,
        String description,
        String categoryId,
        String categoryName,
        String creatorId,
        String status,
        List<ProductImageResponse> images,
        List<ProductVariantResponse> variants,
        ZonedDateTime createdAt,
        ZonedDateTime updatedAt
) {}
