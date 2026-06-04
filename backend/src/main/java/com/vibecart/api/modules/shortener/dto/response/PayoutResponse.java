package com.vibecart.api.modules.shortener.dto.response;

import lombok.*;

import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResponse {
    private String id;
    private String creatorId;
    private BigDecimal amount;
    private String bankName;
    private String bankAccountNumber;
    private String bankAccountName;
    private String status;
    private String adminNote;
    private ZonedDateTime createdAt;
}
