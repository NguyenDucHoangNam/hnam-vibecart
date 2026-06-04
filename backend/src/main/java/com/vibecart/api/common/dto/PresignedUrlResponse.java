package com.vibecart.api.common.dto;

public record PresignedUrlResponse(
    String uploadUrl,
    String key,
    String publicUrl,
    int expiresInMinutes
) {}
