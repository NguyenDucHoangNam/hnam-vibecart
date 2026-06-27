package com.vibecart.api.modules.chat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;
@Document(collection = "messages")
@CompoundIndex(name = "conv_created_idx", def = "{'conversation_id': 1, 'created_at': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private String id;

    @Field("conversation_id")
    private String conversationId;

    @Field("sender_id")
    private String senderId;

    @Field("content")
    private String content;

    @Field("type")
    private String type;

    @Field("attachment_metadata")
    private AttachmentMetadata attachmentMetadata;

    @Field("read_by")
    private List<ReadReceipt> readBy;

    @Builder.Default
    @Field("created_at")
    private Instant createdAt = Instant.now();
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentMetadata {
        @Field("file_url")
        private String fileUrl;

        @Field("file_name")
        private String fileName;

        @Field("file_size")
        private Long fileSize;

        @Field("mime_type")
        private String mimeType;
    }
    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadReceipt {
        @Field("user_id")
        private String userId;

        @Field("read_at")
        private Instant readAt;
    }
}
