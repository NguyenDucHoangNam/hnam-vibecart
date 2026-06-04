package com.vibecart.api.modules.chat.dto.response;

import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingResponse {
    private String conversationId;
    private String username;
    private boolean isTyping;
}
