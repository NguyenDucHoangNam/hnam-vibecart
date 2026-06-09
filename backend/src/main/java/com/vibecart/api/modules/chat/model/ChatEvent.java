package com.vibecart.api.modules.chat.model;

import lombok.*;
import java.io.Serializable;
import java.util.List;

/**
 * Đối tượng sự kiện Chat (ChatEvent) luân chuyển qua Redis Pub/Sub.
 */
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
