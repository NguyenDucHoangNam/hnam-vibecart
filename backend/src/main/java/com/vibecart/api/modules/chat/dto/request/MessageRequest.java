package com.vibecart.api.modules.chat.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @Pattern(regexp = "TEXT|IMAGE|VIDEO|DOCUMENT", message = "Type must be TEXT, IMAGE, VIDEO or DOCUMENT")
    private String type;

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
    }
}
