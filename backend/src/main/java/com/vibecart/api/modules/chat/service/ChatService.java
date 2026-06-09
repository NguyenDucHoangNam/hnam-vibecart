package com.vibecart.api.modules.chat.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.chat.dto.request.ConversationRequest;
import com.vibecart.api.modules.chat.dto.request.MessageRequest;
import com.vibecart.api.modules.chat.dto.request.PresignedUrlRequest;
import com.vibecart.api.modules.chat.dto.request.TypingRequest;
import com.vibecart.api.modules.chat.dto.response.ConversationResponse;
import com.vibecart.api.modules.chat.dto.response.MessageResponse;
import com.vibecart.api.modules.chat.dto.response.PresignedUrlResponse;

import java.util.List;

/**
 * Interface định nghĩa các nghiệp vụ chat, quản lý phòng và tin nhắn.
 */
public interface ChatService {

    /**
     * Tạo mới hoặc lấy cuộc trò chuyện có sẵn.
     */
    ConversationResponse createOrGetConversation(ConversationRequest request, String currentUsername);

    /**
     * Lấy danh sách các cuộc trò chuyện của người dùng hiện tại.
     */
    List<ConversationResponse> getConversationsForUser(String currentUsername);

    /**
     * Lấy tin nhắn phân trang trong cuộc trò chuyện (bao gồm xác thực thành viên).
     */
    PageResponse<MessageResponse> getMessages(String conversationId, int page, int size, String currentUsername);

    /**
     * Lưu tin nhắn mới vào DB và phát tán qua Redis Pub/Sub và WebSocket.
     */
    MessageResponse saveAndBroadcastMessage(MessageRequest request, String currentUsername);

    /**
     * Phát tán trạng thái gõ phím của người dùng.
     */
    void broadcastTyping(TypingRequest request, String currentUsername);

    /**
     * Sinh Pre-signed URL cho phép Client tải tệp đính kèm trực tiếp lên S3.
     */
    PresignedUrlResponse generateAttachmentUploadUrl(PresignedUrlRequest request, String currentUsername);

    /**
     * Đánh dấu cuộc trò chuyện đã được đọc hoàn toàn bởi người dùng chỉ định.
     */
    void markConversationAsRead(String conversationId, String currentUsername);
}
