package com.vibecart.api.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageRequest {

    @NotBlank(message = "Conversation ID must not be blank")
    private String conversationId;

    private String content;

    @NotBlank(message = "Message type must not be blank")
    private String type; // TEXT, IMAGE, VIDEO, DOCUMENT, PRODUCT, ORDER

    private AttachmentMetadataRequest attachmentMetadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttachmentMetadataRequest {
        private String fileUrl;
        private String fileName;
        private Long fileSize;
        private String mimeType;
        private String cardId;
    }
}
