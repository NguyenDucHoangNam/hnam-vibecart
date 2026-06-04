package com.vibecart.api.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeenRequest {

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;
}
