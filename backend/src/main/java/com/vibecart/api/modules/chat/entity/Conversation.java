package com.vibecart.api.modules.chat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

/**
 * Thực thể MongoDB đại diện cho một cuộc hội thoại (Direct hoặc Group).
 */
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
    private String type;

    @Field("name")
    private String name;

    @Field("avatar_url")
    private String avatarUrl;

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

    /**
     * Thông tin tin nhắn cuối cùng để hiển thị nhanh trên danh sách hội thoại.
     */
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
        private String type;

        @Field("created_at")
        private Instant createdAt;
    }
}
