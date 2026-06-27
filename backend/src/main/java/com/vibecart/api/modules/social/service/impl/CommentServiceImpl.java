package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.social.dto.request.CommentRequest;
import com.vibecart.api.modules.social.dto.response.CommentResponse;
import com.vibecart.api.modules.social.entity.Post;
import com.vibecart.api.modules.social.entity.PostComment;
import com.vibecart.api.modules.social.mapper.CommentMapper;
import com.vibecart.api.modules.social.repository.PostCommentRepository;
import com.vibecart.api.modules.social.repository.PostRepository;
import com.vibecart.api.modules.social.service.CommentService;
import com.vibecart.api.modules.social.util.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.kafka.core.KafkaTemplate;
import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.config.KafkaTopicConfig;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class CommentServiceImpl implements CommentService {

    private final PostCommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final CommentMapper commentMapper;
    private final ProfanityFilter profanityFilter;
    private final KafkaTemplate<String, InAppNotificationEvent> notificationKafkaTemplate;
    private static final int MAX_NESTING_DEPTH = 2;


    @Override
    @Transactional
    public CommentResponse addComment(String postId, CommentRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));


        if (profanityFilter.containsProfanity(request.content())) {
            throw new AppException(ErrorCode.PROFANITY_DETECTED);
        }

        PostComment comment = PostComment.builder()
                .post(post)
                .user(user)
                .content(request.content())
                .build();


        if (request.parentId() != null && !request.parentId().isBlank()) {
            PostComment parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));


            int parentDepth = calculateDepth(parent);
            if (parentDepth >= MAX_NESTING_DEPTH) {
                throw new AppException(ErrorCode.MAX_COMMENT_DEPTH);
            }

            comment.setParent(parent);
        }

        PostComment savedComment = commentRepository.save(comment);
        log.info("Comment added by {} on post {}", username, postId);

        try {
            User postCreator = post.getCreator();
            if (!postCreator.getId().equals(user.getId())) {
                InAppNotificationEvent event = InAppNotificationEvent.builder()
                        .eventId(UUID.randomUUID().toString())
                        .recipientId(postCreator.getId())
                        .recipientUsername(postCreator.getUsername())
                        .actorId(user.getId())
                        .actorUsername(user.getUsername())
                        .actorFullName(user.getFullName())
                        .actorAvatarUrl(user.getAvatarUrl())
                        .type("COMMENT")
                        .referenceId(post.getId())
                        .content(user.getFullName() + " đã bình luận về bài viết của bạn")
                        .build();
                notificationKafkaTemplate.send(KafkaTopicConfig.IN_APP_NOTIFICATION_TOPIC, event);
            }

            if (comment.getParent() != null) {
                User parentUser = comment.getParent().getUser();
                if (!parentUser.getId().equals(user.getId()) && !parentUser.getId().equals(postCreator.getId())) {
                    InAppNotificationEvent event = InAppNotificationEvent.builder()
                            .eventId(UUID.randomUUID().toString())
                            .recipientId(parentUser.getId())
                            .recipientUsername(parentUser.getUsername())
                            .actorId(user.getId())
                            .actorUsername(user.getUsername())
                            .actorFullName(user.getFullName())
                            .actorAvatarUrl(user.getAvatarUrl())
                            .type("COMMENT")
                            .referenceId(post.getId())
                            .content(user.getFullName() + " đã phản hồi bình luận của bạn")
                            .build();
                    notificationKafkaTemplate.send(KafkaTopicConfig.IN_APP_NOTIFICATION_TOPIC, event);
                }
            }
        } catch (Exception e) {
            log.error("Failed to send COMMENT notification for post {}: {}", postId, e.getMessage());
        }

        return commentMapper.toCommentResponse(savedComment, List.of());
    }


    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getComments(String postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        Page<PostComment> rootCommentsPage = commentRepository
                .findByPostIdAndParentIsNullOrderByCreatedAtDesc(postId, pageable);

        List<PostComment> rootComments = rootCommentsPage.getContent();


        Map<String, List<PostComment>> repliesByParentId;
        if (rootComments.isEmpty()) {
            repliesByParentId = Map.of();
        } else {
            List<String> rootIds = rootComments.stream().map(PostComment::getId).toList();
            List<PostComment> depth1Replies = commentRepository
                    .findRepliesByPostIdAndParentIds(postId, rootIds);


            List<String> depth1Ids = depth1Replies.stream().map(PostComment::getId).toList();
            List<PostComment> depth2Replies = depth1Ids.isEmpty()
                    ? List.of()
                    : commentRepository.findRepliesByPostIdAndParentIds(postId, depth1Ids);


            List<PostComment> allReplies = new ArrayList<>();
            allReplies.addAll(depth1Replies);
            allReplies.addAll(depth2Replies);

            repliesByParentId = allReplies.stream()
                    .collect(Collectors.groupingBy(
                            reply -> reply.getParent().getId(),
                            LinkedHashMap::new,
                            Collectors.toList()
                    ));
        }


        List<CommentResponse> content = rootComments.stream()
                .map(rootComment -> toCommentResponseTree(rootComment, repliesByParentId))
                .toList();

        return PageResponse.<CommentResponse>builder()
                .content(content)
                .page(rootCommentsPage.getNumber())
                .size(rootCommentsPage.getSize())
                .totalElements(rootCommentsPage.getTotalElements())
                .totalPages(rootCommentsPage.getTotalPages())
                .last(rootCommentsPage.isLast())
                .build();
    }


    @Override
    @Transactional
    public void deleteComment(String postId, String commentId, String username, boolean isAdmin) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));


        if (!comment.getPost().getId().equals(postId)) {
            throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
        }


        boolean isCommentOwner = comment.getUser().getUsername().equals(username);
        boolean isPostCreator = comment.getPost().getCreator().getUsername().equals(username);

        if (!isAdmin && !isCommentOwner && !isPostCreator) {
            throw new AppException(ErrorCode.COMMENT_ACCESS_DENIED);
        }

        commentRepository.delete(comment);
        log.info("Comment {} deleted by {}", commentId, username);
    }
    private int calculateDepth(PostComment comment) {
        int depth = 0;
        PostComment current = comment;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }
    private CommentResponse toCommentResponseTree(PostComment comment, Map<String, List<PostComment>> repliesByParentId) {
        List<PostComment> directReplies = repliesByParentId.getOrDefault(comment.getId(), List.of());

        List<CommentResponse> replyResponses = directReplies.stream()
                .map(reply -> toCommentResponseTree(reply, repliesByParentId))
                .toList();

        return commentMapper.toCommentResponse(comment, replyResponses);
    }
}
