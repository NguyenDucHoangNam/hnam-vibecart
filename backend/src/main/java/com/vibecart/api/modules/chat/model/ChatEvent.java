package com.vibecart.api.modules.chat.model;

import lombok.*;
import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent implements Serializable {
    private String type; // MESSAGE, TYPING, READ_RECEIPT
    private String conversationId;
    private String payloadJson; // Serialized JSON string of the target DTO
    private List<String> targetUsernames; // List of usernames to route the event to
}
