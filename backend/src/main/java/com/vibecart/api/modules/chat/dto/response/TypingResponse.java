
package com.vibecart.api.modules.chat.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TypingResponse {
    private String conversationId;
    private String username;
    private boolean isTyping;

    public String getConversationId() {
        return conversationId;
    }

    public void setConversationId(String conversationId) {
        this.conversationId = conversationId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
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
