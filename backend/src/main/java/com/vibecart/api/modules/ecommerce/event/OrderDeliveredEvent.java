package com.vibecart.api.modules.ecommerce.event;

import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderDeliveredEvent {
    private String orderId;
    private String userId;
    private BigDecimal finalAmount;
    private ZonedDateTime timestamp;
}
