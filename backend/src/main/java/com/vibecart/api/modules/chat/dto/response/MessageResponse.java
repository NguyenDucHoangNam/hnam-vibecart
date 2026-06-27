package com.vibecart.api.modules.chat.dto.response;

import lombok.*;

import java.time.Instant;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponse {

    private String id;
    private String conversationId;
    private String senderId;
    private String content;
    private String type;
    private AttachmentMetadataResponse attachmentMetadata;
    private List<ReadReceiptResponse> readBy;
    private Instant createdAt;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentMetadataResponse {
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String mimeType;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReadReceiptResponse {
        private String userId;
        private Instant readAt;
    }
}
