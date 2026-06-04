package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.constraints.NotBlank;

public record CategoryRequest(
        @NotBlank(message = "Category name is required")
        String name,
        String parentId
) {}
