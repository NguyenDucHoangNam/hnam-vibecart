package com.vibecart.api.modules.shortener.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ShortlinkCreateRequest {

    @NotBlank(message = "Product ID is required")
    private String productId;

    @NotBlank(message = "Original URL is required")
    private String longUrl;
}
