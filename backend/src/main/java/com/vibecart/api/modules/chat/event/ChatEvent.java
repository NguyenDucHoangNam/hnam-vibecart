package com.vibecart.api.modules.chat.event;

import lombok.*;
import java.io.Serializable;
import java.util.List;
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent implements Serializable {
    private String type;
    private String conversationId;
    private String payloadJson;
    private List<String> targetUsernames;
}
