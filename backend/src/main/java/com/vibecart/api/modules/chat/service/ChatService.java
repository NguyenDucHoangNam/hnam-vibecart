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
public interface ChatService {
    ConversationResponse createOrGetConversation(ConversationRequest request, String currentUsername);
    List<ConversationResponse> getConversationsForUser(String currentUsername);
    PageResponse<MessageResponse> getMessages(String conversationId, int page, int size, String currentUsername);
    MessageResponse saveAndBroadcastMessage(MessageRequest request, String currentUsername);
    void broadcastTyping(TypingRequest request, String currentUsername);
    PresignedUrlResponse generateAttachmentUploadUrl(PresignedUrlRequest request, String currentUsername);
    void markConversationAsRead(String conversationId, String currentUsername);
}
