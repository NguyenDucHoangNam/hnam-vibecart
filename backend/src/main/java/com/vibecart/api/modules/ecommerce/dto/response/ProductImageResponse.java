package com.vibecart.api.modules.ecommerce.dto.response;

public record ProductImageResponse(
        String id,
        String imageUrl,
        boolean isThumbnail,
        Integer sortOrder
) {}
