package com.vibecart.api.modules.chat.dto.response;

import lombok.*;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PresenceResponse {
    private String userId;
    private String status;
    private Instant lastActiveAt;
}
