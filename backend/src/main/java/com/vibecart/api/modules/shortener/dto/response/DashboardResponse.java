package com.vibecart.api.modules.shortener.dto.response;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {
    private String creatorId;
    private long totalClicks;
    private long totalOrders;
    private double conversionRate;
    private BigDecimal pendingCommission;
    private BigDecimal approvedCommission;
}
