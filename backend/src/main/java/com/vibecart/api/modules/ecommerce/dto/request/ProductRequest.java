package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record ProductRequest(
        @NotBlank(message = "Product name cannot be blank")
        String name,
        String description,
        @NotBlank(message = "Category ID is required")
        String categoryId,
        @NotEmpty(message = "At least one variant is required")
        List<@Valid ProductVariantRequest> variants,
        List<@Valid ProductImageRequest> images
) {}
