package com.vibecart.api.modules.ecommerce.dto.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderPaidEvent {
    private String eventType;
    private ZonedDateTime timestamp;
    private String orderId;
    private String orderCode;
    private String userId;
    private String userEmail;
    private BigDecimal finalAmount;
    private String paymentTransactionId;
}
