package com.vibecart.api.modules.chat.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;
import java.util.List;

/**
 * Thực thể MongoDB đại diện cho một tin nhắn trong phòng chat.
 */
@Document(collection = "messages")
@CompoundIndex(name = "conv_created_idx", def = "{'conversationId': 1, 'createdAt': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    private String id;

    @Indexed
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

    /**
     * Thông tin chi tiết của tệp đính kèm đi kèm tin nhắn (hình ảnh, video, sản phẩm, đơn hàng...).
     */
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

        @Field("card_id")
        private String cardId;
    }

    /**
     * Bản ghi thời gian đã đọc tin nhắn của từng thành viên trong cuộc trò chuyện.
     */
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
