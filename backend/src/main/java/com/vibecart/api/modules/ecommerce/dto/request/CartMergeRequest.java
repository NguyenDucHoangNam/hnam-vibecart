package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CartMergeRequest(
        @NotEmpty(message = "Items cannot be empty")
        List<@Valid CartMergeItem> items
) {
    public record CartMergeItem(
            @NotBlank(message = "Variant ID is required")
            String variantId,
            @NotNull @Min(value = 1)
            Integer quantity
    ) {}
}
