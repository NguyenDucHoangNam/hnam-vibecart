package com.vibecart.api.modules.chat.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.entity.MediaMetadata;
import com.vibecart.api.common.repository.MediaMetadataRepository;
import com.vibecart.api.common.service.StorageService;
import com.vibecart.api.modules.chat.dto.request.ConversationRequest;
import com.vibecart.api.modules.chat.dto.request.MessageRequest;
import com.vibecart.api.modules.chat.dto.request.PresignedUrlRequest;
import com.vibecart.api.modules.chat.dto.request.TypingRequest;
import com.vibecart.api.modules.chat.dto.response.ConversationResponse;
import com.vibecart.api.modules.chat.dto.response.MessageResponse;
import com.vibecart.api.modules.chat.dto.response.PresignedUrlResponse;
import com.vibecart.api.modules.chat.dto.response.TypingResponse;
import com.vibecart.api.modules.chat.entity.Conversation;
import com.vibecart.api.modules.chat.entity.Message;
import com.vibecart.api.modules.chat.mapper.ChatMapper;
import com.vibecart.api.modules.chat.dto.event.ChatEvent;
import com.vibecart.api.modules.chat.repository.ConversationRepository;
import com.vibecart.api.modules.chat.repository.MessageRepository;
import com.vibecart.api.modules.chat.service.ChatService;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.mapper.UserMapper;
import com.vibecart.api.modules.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;
@Service
@Slf4j
@RequiredArgsConstructor
public class ChatServiceImpl implements ChatService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final StorageService storageService;
    private final MediaMetadataRepository mediaMetadataRepository;
    private final ChatMapper chatMapper;
    private final UserMapper userMapper;
    private final RedisTemplate<String, ChatEvent> chatRedisTemplate;
    private final ObjectMapper objectMapper;

    private static final long MAX_FILE_SIZE_BYTES = 20 * 1024 * 1024;
    private static final Set<String> FORBIDDEN_EXTENSIONS = Set.of(
            ".exe", ".bat", ".sh", ".cmd", ".msi", ".ps1", ".vbs", ".vbe",
            ".scr", ".pif", ".com", ".dll", ".jar", ".cpl", ".hta", ".inf",
            ".reg", ".ws", ".wsf", ".sct", ".lnk", ".app", ".action", ".command"
    );
    private static final String CHANNEL_PREFIX = "chat:user:";
    @Override
    public ConversationResponse createOrGetConversation(ConversationRequest request, String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);
        String currentUserId = currentUser.getId();

        Set<String> memberIds = new HashSet<>(request.getMemberIds());
        memberIds.add(currentUserId);

        List<User> foundUsers = userRepository.findAllById(memberIds);
        if (foundUsers.size() != memberIds.size()) {
            throw new AppException(ErrorCode.USER_NOT_EXISTED);
        }

        if (memberIds.size() != 2) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }

        List<Conversation> directList = conversationRepository.findDirectConversationsBetween(memberIds);
        if (directList != null && !directList.isEmpty()) {
            directList.sort((c1, c2) -> {
                Instant u1 = c1.getUpdatedAt() != null ? c1.getUpdatedAt() : Instant.EPOCH;
                Instant u2 = c2.getUpdatedAt() != null ? c2.getUpdatedAt() : Instant.EPOCH;
                return u2.compareTo(u1);
            });
            return toConversationResponse(directList.get(0));
        }

        Map<String, Integer> unreadCounts = new HashMap<>();
        for (String memberId : memberIds) {
            unreadCounts.put(memberId, 0);
        }

        Conversation conversation = Conversation.builder()
                .type("DIRECT")
                .memberIds(memberIds)
                .unreadCounts(unreadCounts)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        Conversation saved = conversationRepository.save(conversation);
        log.info("Created new {} conversation with ID: {}", saved.getType(), saved.getId());

        return toConversationResponse(saved);
    }
    @Override
    public List<ConversationResponse> getConversationsForUser(String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);
        List<Conversation> list = conversationRepository
                .findByMemberIdsContainingOrderByUpdatedAtDesc(currentUser.getId());
        return list.stream()
                .map(this::toConversationResponse)
                .collect(Collectors.toList());
    }
    @Override
    public PageResponse<MessageResponse> getMessages(String conversationId, int page, int size,
            String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);
        String currentUserId = currentUser.getId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        boolean isAdmin = currentUser.getRole() != null 
                && ("ADMIN".equals(currentUser.getRole().getName()) || "ROLE_ADMIN".equals(currentUser.getRole().getName()));

        if (!conversation.getMemberIds().contains(currentUserId) && !isAdmin) {
            throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }

        markConversationAsRead(conversationId, currentUsername);

        Pageable pageable = PageRequest.of(page, size);
        Page<Message> msgPage = messageRepository.findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

        List<MessageResponse> content = msgPage.getContent().stream()
                .map(chatMapper::toMessageResponse)
                .collect(Collectors.toList());

        return PageResponse.<MessageResponse>builder()
                .content(content)
                .page(msgPage.getNumber())
                .size(msgPage.getSize())
                .totalElements(msgPage.getTotalElements())
                .totalPages(msgPage.getTotalPages())
                .last(msgPage.isLast())
                .build();
    }
    @Override
    public MessageResponse saveAndBroadcastMessage(MessageRequest request, String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);
        String currentUserId = currentUser.getId();

        if ("TEXT".equals(request.getType())) {
            if (request.getContent() == null || request.getContent().trim().isEmpty()) {
                throw new AppException(ErrorCode.INVALID_INPUT);
            }
        }

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!conversation.getMemberIds().contains(currentUserId)) {
            throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }

        Message.AttachmentMetadata attachment = null;
        if (request.getAttachmentMetadata() != null) {
            String fileUrl = request.getAttachmentMetadata().getFileUrl();
            if (fileUrl != null && !fileUrl.trim().isEmpty()) {
                String s3Key = extractS3KeyFromUrl(fileUrl);
                if (s3Key != null) {
                    Optional<MediaMetadata> metadataOpt = mediaMetadataRepository.findByS3Key(s3Key);
                    if (metadataOpt.isEmpty() || !"VERIFIED".equals(metadataOpt.get().getStatus())) {
                        log.warn("Attempt to send message with unverified S3 attachment: key={}", s3Key);
                        throw new AppException(ErrorCode.INVALID_INPUT);
                    }
                }
            }

            attachment = Message.AttachmentMetadata.builder()
                    .fileUrl(request.getAttachmentMetadata().getFileUrl())
                    .fileName(request.getAttachmentMetadata().getFileName())
                    .fileSize(request.getAttachmentMetadata().getFileSize())
                    .mimeType(request.getAttachmentMetadata().getMimeType())
                    .build();
        }

        Message.ReadReceipt selfReceipt = Message.ReadReceipt.builder()
                .userId(currentUserId)
                .readAt(Instant.now())
                .build();

        Message message = Message.builder()
                .conversationId(request.getConversationId())
                .senderId(currentUserId)
                .content(request.getContent())
                .type(request.getType())
                .attachmentMetadata(attachment)
                .readBy(new ArrayList<>(List.of(selfReceipt)))
                .createdAt(Instant.now())
                .build();

        Message savedMessage = messageRepository.save(message);

        Conversation.LastMessage lastMessage = Conversation.LastMessage.builder()
                .messageId(savedMessage.getId())
                .senderId(currentUserId)
                .content(savedMessage.getContent())
                .type(savedMessage.getType())
                .createdAt(savedMessage.getCreatedAt())
                .build();

        conversation.setLastMessage(lastMessage);
        conversation.setUpdatedAt(Instant.now());

        if (conversation.getUnreadCounts() == null) {
            conversation.setUnreadCounts(new HashMap<>());
        }

        for (String memberId : conversation.getMemberIds()) {
            if (!memberId.equals(currentUserId)) {
                int currentUnread = conversation.getUnreadCounts().getOrDefault(memberId, 0);
                conversation.getUnreadCounts().put(memberId, currentUnread + 1);
            }
        }

        conversationRepository.save(conversation);

        MessageResponse response = chatMapper.toMessageResponse(savedMessage);

        List<String> targetUsernames = getTargetUsernames(conversation.getMemberIds(), currentUserId);
        try {
            ChatEvent event = ChatEvent.builder()
                    .type("MESSAGE")
                    .conversationId(conversation.getId())
                    .payloadJson(objectMapper.writeValueAsString(response))
                    .targetUsernames(targetUsernames)
                    .build();

            for (String targetUsername : targetUsernames) {
                chatRedisTemplate.convertAndSend(CHANNEL_PREFIX + targetUsername, event);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message response for Redis pub/sub", e);
        }

        return response;
    }
    @Override
    public void broadcastTyping(TypingRequest request, String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);
        String currentUserId = currentUser.getId();

        Conversation conversation = conversationRepository.findById(request.getConversationId())
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        if (!conversation.getMemberIds().contains(currentUserId)) {
            throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }

        TypingResponse typingResponse = TypingResponse.builder()
                .conversationId(request.getConversationId())
                .username(currentUsername)
                .isTyping(request.isTyping())
                .build();

        List<String> targetUsernames = getTargetUsernames(conversation.getMemberIds(), currentUserId);
        try {
            ChatEvent event = ChatEvent.builder()
                    .type("TYPING")
                    .conversationId(conversation.getId())
                    .payloadJson(objectMapper.writeValueAsString(typingResponse))
                    .targetUsernames(targetUsernames)
                    .build();

            for (String targetUsername : targetUsernames) {
                chatRedisTemplate.convertAndSend(CHANNEL_PREFIX + targetUsername, event);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize typing event for Redis", e);
        }
    }
    @Override
    public PresignedUrlResponse generateAttachmentUploadUrl(PresignedUrlRequest request, String currentUsername) {
        if (request.getFileSize() > MAX_FILE_SIZE_BYTES) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        String fileNameLower = request.getFileName().toLowerCase();
        for (String ext : FORBIDDEN_EXTENSIONS) {
            if (fileNameLower.endsWith(ext)) {
                throw new AppException(ErrorCode.FILE_TYPE_NOT_SUPPORTED);
            }
        }

        String uuid = UUID.randomUUID().toString();
        String fileKey = String.format("chat/attachments/%s_%s", uuid,
                request.getFileName().replaceAll("[^a-zA-Z0-9.-]", "_"));

        String uploadUrl = storageService.generatePresignedUploadUrl(fileKey, request.getContentType(),
                request.getFileSize(), 5);
        String fileUrl = storageService.getFileUrl(fileKey);

        MediaMetadata metadata = MediaMetadata.builder()
                .s3Key(fileKey)
                .uploadedBy(currentUsername)
                .fileSize(request.getFileSize())
                .contentType(request.getContentType())
                .status("PENDING")
                .build();
        mediaMetadataRepository.save(metadata);

        log.info("Generated S3 Pre-signed URL for user {} upload. Key: {}, Size: {} bytes", currentUsername, fileKey,
                request.getFileSize());

        return PresignedUrlResponse.builder()
                .uploadUrl(uploadUrl)
                .fileUrl(fileUrl)
                .fileKey(fileKey)
                .build();
    }
    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }
    private List<String> getTargetUsernames(Set<String> memberIds, String senderId) {
        if (memberIds.isEmpty())
            return Collections.emptyList();

        return userRepository.findAllById(memberIds).stream()
                .map(User::getUsername)
                .collect(Collectors.toList());
    }
    private ConversationResponse toConversationResponse(Conversation conversation) {
        ConversationResponse response = chatMapper.toConversationResponse(conversation);

        List<User> memberUsers = userRepository.findAllById(conversation.getMemberIds());
        List<UserResponse> memberProfiles = memberUsers.stream()
                .map(userMapper::toUserResponse)
                .collect(Collectors.toList());
        response.setMembers(memberProfiles);

        if (conversation.getLastMessage() != null) {
            ConversationResponse.LastMessageResponse lastMsgResponse = ConversationResponse.LastMessageResponse
                    .builder()
                    .messageId(conversation.getLastMessage().getMessageId())
                    .senderId(conversation.getLastMessage().getSenderId())
                    .content(conversation.getLastMessage().getContent())
                    .type(conversation.getLastMessage().getType())
                    .createdAt(conversation.getLastMessage().getCreatedAt())
                    .build();
            response.setLastMessage(lastMsgResponse);
        }

        return response;
    }
    private void broadcastReadReceipt(String conversationId, String readerId, List<String> targetUsernames) {
        try {
            Map<String, Object> receiptPayload = Map.of(
                    "conversationId", conversationId,
                    "userId", readerId,
                    "readAt", Instant.now().toString());

            ChatEvent event = ChatEvent.builder()
                    .type("READ_RECEIPT")
                    .conversationId(conversationId)
                    .payloadJson(objectMapper.writeValueAsString(receiptPayload))
                    .targetUsernames(targetUsernames)
                    .build();

            for (String targetUsername : targetUsernames) {
                chatRedisTemplate.convertAndSend(CHANNEL_PREFIX + targetUsername, event);
            }
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize read receipt payload", e);
        }
    }
    @Override
    public void markConversationAsRead(String conversationId, String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);
        String currentUserId = currentUser.getId();

        Conversation conversation = conversationRepository.findById(conversationId)
                .orElseThrow(() -> new AppException(ErrorCode.CONVERSATION_NOT_FOUND));

        boolean isAdmin = currentUser.getRole() != null 
                && ("ADMIN".equals(currentUser.getRole().getName()) || "ROLE_ADMIN".equals(currentUser.getRole().getName()));

        if (!conversation.getMemberIds().contains(currentUserId) && !isAdmin) {
            throw new AppException(ErrorCode.CONVERSATION_ACCESS_DENIED);
        }

        if (conversation.getUnreadCounts() != null
                && conversation.getUnreadCounts().getOrDefault(currentUserId, 0) > 0) {
            int unreadCount = conversation.getUnreadCounts().get(currentUserId);
            conversation.getUnreadCounts().put(currentUserId, 0);
            conversationRepository.save(conversation);

            int limit = Math.max(unreadCount, 50);
            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0,
                    limit);
            org.springframework.data.domain.Page<Message> msgPage = messageRepository
                    .findByConversationIdOrderByCreatedAtDesc(conversationId, pageable);

            List<Message> toUpdate = new ArrayList<>();
            Instant now = Instant.now();
            for (Message m : msgPage.getContent()) {
                if (!m.getSenderId().equals(currentUserId)) {
                    if (m.getReadBy() == null) {
                        m.setReadBy(new ArrayList<>());
                    }
                    boolean alreadyRead = m.getReadBy().stream()
                            .anyMatch(r -> r.getUserId().equals(currentUserId));
                    if (!alreadyRead) {
                        m.getReadBy().add(new Message.ReadReceipt(currentUserId, now));
                        toUpdate.add(m);
                    }
                }
            }
            if (!toUpdate.isEmpty()) {
                messageRepository.saveAll(toUpdate);
            }

            List<String> targetUsernames = getTargetUsernames(conversation.getMemberIds(), currentUserId);
            broadcastReadReceipt(conversationId, currentUserId, targetUsernames);
        }
    }
    private String extractS3KeyFromUrl(String fileUrl) {
        if (fileUrl == null)
            return null;
        int index = fileUrl.indexOf("chat/attachments/");
        if (index != -1) {
            return fileUrl.substring(index);
        }
        return null;
    }
}
