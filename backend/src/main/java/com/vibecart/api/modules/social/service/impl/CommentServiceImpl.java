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
import com.vibecart.api.modules.social.service.ProfanityFilter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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

    /**
     * Spec: "hỗ trợ phân cấp đa tầng, giới hạn hiển thị tối đa 3 cấp".
     * Depth 0 = root, depth 1 = reply, depth 2 = sub-reply (cấp 3 cuối cùng).
     * MAX_NESTING_DEPTH = 2 → cho phép lưu tối đa 3 cấp (0,1,2).
     */
    private static final int MAX_NESTING_DEPTH = 2;

    // ==================== THÊM COMMENT ====================
    @Override
    @Transactional
    public CommentResponse addComment(String postId, CommentRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        // [BE-4] Profanity Filter — kiểm tra từ ngữ thô tục trước khi lưu
        if (profanityFilter.containsProfanity(request.content())) {
            throw new AppException(ErrorCode.PROFANITY_DETECTED);
        }

        PostComment comment = PostComment.builder()
                .post(post)
                .user(user)
                .content(request.content())
                .build();

        // Handle nested reply
        if (request.parentId() != null && !request.parentId().isBlank()) {
            PostComment parent = commentRepository.findById(request.parentId())
                    .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

            // [BE-3] Tính depth thực sự: đếm ngược qua chuỗi parent
            int parentDepth = calculateDepth(parent);
            if (parentDepth >= MAX_NESTING_DEPTH) {
                throw new AppException(ErrorCode.MAX_COMMENT_DEPTH);
            }

            comment.setParent(parent);
        }

        PostComment savedComment = commentRepository.save(comment);
        log.info("Comment added by {} on post {}", username, postId);

        return commentMapper.toCommentResponse(savedComment, List.of());
    }

    // ==================== LẤY DANH SÁCH COMMENT ====================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<CommentResponse> getComments(String postId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);

        // Lấy root comments (parent IS NULL) — phân trang
        Page<PostComment> rootCommentsPage = commentRepository
                .findByPostIdAndParentIsNullOrderByCreatedAtDesc(postId, pageable);

        List<PostComment> rootComments = rootCommentsPage.getContent();

        // [PERF-2] Batch Replies theo Root Page — chỉ load replies thuộc root comments trong trang hiện tại
        // thay vì bốc TOÀN BỘ replies của bài viết vào RAM (tránh OOM trên bài viral).
        Map<String, List<PostComment>> repliesByParentId;
        if (rootComments.isEmpty()) {
            repliesByParentId = Map.of();
        } else {
            // Depth 1: Lấy replies trực tiếp của root comments trong trang
            List<String> rootIds = rootComments.stream().map(PostComment::getId).toList();
            List<PostComment> depth1Replies = commentRepository
                    .findRepliesByPostIdAndParentIds(postId, rootIds);

            // Depth 2: Lấy sub-replies (cấp 3 cuối cùng) của depth-1 replies
            List<String> depth1Ids = depth1Replies.stream().map(PostComment::getId).toList();
            List<PostComment> depth2Replies = depth1Ids.isEmpty()
                    ? List.of()
                    : commentRepository.findRepliesByPostIdAndParentIds(postId, depth1Ids);

            // Gom nhóm tất cả replies (depth 1 + depth 2) theo parentId
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

        // Map root comments + attach replies from grouped map
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

    // ==================== XÓA COMMENT ====================
    @Override
    @Transactional
    public void deleteComment(String postId, String commentId, String username, boolean isAdmin) {
        PostComment comment = commentRepository.findById(commentId)
                .orElseThrow(() -> new AppException(ErrorCode.COMMENT_NOT_FOUND));

        // Verify comment belongs to this post
        if (!comment.getPost().getId().equals(postId)) {
            throw new AppException(ErrorCode.COMMENT_NOT_FOUND);
        }

        // Check permission: comment owner, post creator, or admin
        boolean isCommentOwner = comment.getUser().getUsername().equals(username);
        boolean isPostCreator = comment.getPost().getCreator().getUsername().equals(username);

        if (!isAdmin && !isCommentOwner && !isPostCreator) {
            throw new AppException(ErrorCode.COMMENT_ACCESS_DENIED);
        }

        commentRepository.delete(comment); // Soft delete via @SQLDelete
        log.info("Comment {} deleted by {}", commentId, username);
    }

    // ==================== HELPER METHODS ====================

    /**
     * Tính depth (số cấp cha) của 1 comment.
     * Root = depth 0, reply = depth 1, sub-reply = depth 2.
     */
    private int calculateDepth(PostComment comment) {
        int depth = 0;
        PostComment current = comment;
        while (current.getParent() != null) {
            depth++;
            current = current.getParent();
        }
        return depth;
    }

    /**
     * [BE-9] Build cây comment đệ quy từ pre-fetched grouped replies map.
     * Không gọi thêm query nào.
     */
    private CommentResponse toCommentResponseTree(PostComment comment, Map<String, List<PostComment>> repliesByParentId) {
        List<PostComment> directReplies = repliesByParentId.getOrDefault(comment.getId(), List.of());

        List<CommentResponse> replyResponses = directReplies.stream()
                .map(reply -> toCommentResponseTree(reply, repliesByParentId))
                .toList();

        return commentMapper.toCommentResponse(comment, replyResponses);
    }
}
