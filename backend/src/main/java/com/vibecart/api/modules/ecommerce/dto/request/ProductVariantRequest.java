package com.vibecart.api.modules.ecommerce.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record ProductVariantRequest(
        @NotBlank(message = "SKU code is required")
        String skuCode,
        @NotBlank(message = "Variant name is required")
        String variantName,
        @NotNull(message = "Price is required")
        @DecimalMin(value = "0.0", inclusive = false, message = "Price must be greater than 0")
        BigDecimal price,
        @DecimalMin(value = "0.0", inclusive = true, message = "Discount price cannot be negative")
        BigDecimal discountPrice,
        @NotNull(message = "Initial quantity is required")
        @Min(value = 0, message = "Quantity cannot be negative")
        Integer initialQuantity
) {}
