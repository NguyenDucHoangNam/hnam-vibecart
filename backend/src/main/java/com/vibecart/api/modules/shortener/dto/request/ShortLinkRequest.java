package com.vibecart.api.modules.shortener.dto.request;

import jakarta.validation.constraints.NotBlank;
import org.hibernate.validator.constraints.URL;

public record ShortLinkRequest(
    @NotBlank(message = "Original URL is required")
    @URL(message = "Invalid URL format")
    String originalUrl,

    @NotBlank(message = "Product ID is required")
    String productId
) {}
