package com.vibecart.api.modules.ecommerce.dto.event;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderCancelledEvent {
    private String orderId;
    private String userId;
    private ZonedDateTime timestamp;
}
