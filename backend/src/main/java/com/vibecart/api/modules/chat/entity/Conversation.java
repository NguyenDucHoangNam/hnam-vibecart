package com.vibecart.api.modules.chat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Document(collection = "conversations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    private String id;

    @Field("type")
    private String type; // DIRECT or GROUP

    @Field("name")
    private String name; // Null if DIRECT

    @Field("avatar_url")
    private String avatarUrl; // Null if DIRECT

    @Field("member_ids")
    private Set<String> memberIds;

    @Field("unread_counts")
    private Map<String, Integer> unreadCounts;

    @Field("last_message")
    private LastMessage lastMessage;

    @Builder.Default
    @Field("created_at")
    private Instant createdAt = Instant.now();

    @Builder.Default
    @Field("updated_at")
    private Instant updatedAt = Instant.now();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastMessage {
        @Field("message_id")
        private String messageId;

        @Field("sender_id")
        private String senderId;

        @Field("content")
        private String content;

        @Field("type")
        private String type; // TEXT, IMAGE, VIDEO, DOCUMENT, PRODUCT, ORDER

        @Field("created_at")
        private Instant createdAt;
    }
}
