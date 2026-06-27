package com.vibecart.api.modules.chat.dto.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingRequest {

    @NotBlank(message = "Conversation ID is required")
    private String conversationId;

    private boolean isTyping;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    @JsonProperty("isTyping")
    public boolean isTyping() {
        return isTyping;
    }

    @JsonProperty("isTyping")
    public void setTyping(boolean isTyping) {
        this.isTyping = isTyping;
    }
}
