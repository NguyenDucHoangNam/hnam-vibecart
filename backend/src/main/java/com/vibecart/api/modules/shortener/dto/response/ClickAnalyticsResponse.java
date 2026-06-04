package com.vibecart.api.modules.shortener.dto.response;

import java.math.BigDecimal;
import java.util.Map;

public record ClickAnalyticsResponse(
    String shortLinkId,
    String shortCode,
    long totalClicks,
    BigDecimal totalCommission,
    Map<String, Long> deviceDistribution,
    Map<String, Long> browserDistribution,
    Map<String, Long> countryDistribution
) {}
