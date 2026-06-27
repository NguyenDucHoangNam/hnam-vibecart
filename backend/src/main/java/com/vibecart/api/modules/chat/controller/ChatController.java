package com.vibecart.api.modules.chat.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.chat.dto.request.ConversationRequest;
import com.vibecart.api.modules.chat.dto.request.MessageRequest;
import com.vibecart.api.modules.chat.dto.request.PresignedUrlRequest;
import com.vibecart.api.modules.chat.dto.request.SeenRequest;
import com.vibecart.api.modules.chat.dto.request.TypingRequest;
import com.vibecart.api.modules.chat.dto.response.ConversationResponse;
import com.vibecart.api.modules.chat.dto.response.MessageResponse;
import com.vibecart.api.modules.chat.dto.response.PresenceResponse;
import com.vibecart.api.modules.chat.dto.response.PresignedUrlResponse;
import com.vibecart.api.modules.chat.service.ChatService;
import com.vibecart.api.modules.chat.service.PresenceService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
@RestController
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final PresenceService presenceService;
    @PostMapping("/api/v1/chat/conversations")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ConversationResponse>> createOrGetConversation(
            @Valid @RequestBody ConversationRequest request) {

        String username = SecurityUtils.getCurrentUsername();
        log.info("REST: createOrGetConversation by user '{}', type={}", username, request.getType());

        ConversationResponse result = chatService.createOrGetConversation(request, username);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ConversationResponse>builder()
                        .code(1000)
                        .message("Khởi tạo cuộc hội thoại thành công")
                        .result(result)
                        .build());
    }
    @GetMapping("/api/v1/chat/conversations")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<ConversationResponse>>> getConversations() {

        String username = SecurityUtils.getCurrentUsername();
        log.info("REST: getConversations for user '{}'", username);

        List<ConversationResponse> result = chatService.getConversationsForUser(username);

        return ResponseEntity.ok(
                ApiResponse.<List<ConversationResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách hội thoại thành công")
                        .result(result)
                        .build());
    }
    @GetMapping("/api/v1/chat/conversations/{conversationId}/messages")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<MessageResponse>>> getMessages(
            @PathVariable String conversationId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String username = SecurityUtils.getCurrentUsername();
        log.info("REST: getMessages in room '{}' for user '{}'", conversationId, username);

        PageResponse<MessageResponse> result = chatService.getMessages(conversationId, page, size, username);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<MessageResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách tin nhắn thành công")
                        .result(result)
                        .build());
    }
    @PostMapping("/api/v1/chat/attachments/presigned-url")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<PresignedUrlResponse>> getPresignedUrl(
            @Valid @RequestBody PresignedUrlRequest request) {

        String username = SecurityUtils.getCurrentUsername();
        log.info("REST: getPresignedUrl for file '{}' by user '{}'", request.getFileName(), username);

        PresignedUrlResponse result = chatService.generateAttachmentUploadUrl(request, username);

        return ResponseEntity.ok(
                ApiResponse.<PresignedUrlResponse>builder()
                        .code(1000)
                        .message("Sinh URL upload tệp đính kèm thành công")
                        .result(result)
                        .build());
    }
    @GetMapping("/api/v1/chat/presence/{userId}")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<PresenceResponse>> getUserPresence(@PathVariable String userId) {
        log.info("REST: getUserPresence for userId '{}'", userId);

        PresenceResponse result = presenceService.getUserPresence(userId);

        return ResponseEntity.ok(
                ApiResponse.<PresenceResponse>builder()
                        .code(1000)
                        .message("Lấy trạng thái trực tuyến thành công")
                        .result(result)
                        .build());
    }
    @GetMapping("/api/v1/chat/presence/active")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<List<com.vibecart.api.modules.social.dto.response.FollowResponse>>> getActiveUsers() {
        String username = SecurityUtils.getCurrentUsername();
        log.info("REST: getActiveUsers requested by '{}'", username);

        List<com.vibecart.api.modules.social.dto.response.FollowResponse> result = presenceService
                .getActiveUsers(username);

        return ResponseEntity.ok(
                ApiResponse.<List<com.vibecart.api.modules.social.dto.response.FollowResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách người dùng đang hoạt động thành công")
                        .result(result)
                        .build());
    }
    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Valid MessageRequest request, Principal principal) {
        if (principal != null) {
            String username = principal.getName();
            log.info("STOMP: sendMessage in room '{}' by '{}'", request.getConversationId(), username);
            chatService.saveAndBroadcastMessage(request, username);
        }
    }
    @MessageMapping("/chat.typing")
    public void typing(@Valid TypingRequest request, Principal principal) {
        if (principal != null) {
            String username = principal.getName();
            log.info("STOMP: typing state in room '{}' isTyping={} by '{}'", request.getConversationId(),
                    request.isTyping(), username);
            chatService.broadcastTyping(request, username);
        }
    }
    @MessageMapping("/chat.ping")
    public void ping(Principal principal) {
        if (principal != null) {
            String username = principal.getName();
            log.debug("STOMP: presence heartbeat ping from user '{}'", username);
            presenceService.setOnline(username);
        }
    }
    @MessageMapping("/chat.seen")
    public void seen(@Valid SeenRequest request, Principal principal) {
        if (principal != null) {
            String username = principal.getName();
            log.debug("STOMP: marking conversation '{}' as read by '{}'", request.getConversationId(), username);
            chatService.markConversationAsRead(request.getConversationId(), username);
        }
    }

}
