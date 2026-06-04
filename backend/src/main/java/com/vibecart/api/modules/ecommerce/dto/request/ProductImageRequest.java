package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record ProductImageRequest(
        @NotBlank(message = "Image URL is required")
        String imageUrl,
        boolean isThumbnail,
        Integer sortOrder
) {}
