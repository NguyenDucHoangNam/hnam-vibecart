package com.vibecart.api.common.dto;

import java.time.ZonedDateTime;

public record MediaUploadResponse(
    String url,
    String key,
    String contentType,
    long size,
    ZonedDateTime uploadedAt
) {}
