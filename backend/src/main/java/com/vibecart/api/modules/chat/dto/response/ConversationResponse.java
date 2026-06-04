package com.vibecart.api.modules.chat.dto.response;

import com.vibecart.api.modules.iam.dto.response.UserResponse;
import lombok.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationResponse {

    private String id;
    private String type;
    private String name;
    private String avatarUrl;
    private Set<String> memberIds;
    private Map<String, Integer> unreadCounts;
    private LastMessageResponse lastMessage;
    private Instant createdAt;
    private Instant updatedAt;
    private List<UserResponse> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastMessageResponse {
        private String messageId;
        private String senderId;
        private String content;
        private String type;
        private Instant createdAt;
    }
}
