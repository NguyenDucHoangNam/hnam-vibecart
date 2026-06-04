package com.vibecart.api.modules.social.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.request.CommentRequest;
import com.vibecart.api.modules.social.dto.response.CommentResponse;
import com.vibecart.api.modules.social.service.CommentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts/{postId}/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;

    // ==================== 1. THÊM COMMENT (AUTHENTICATED) ====================
    @PostMapping
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @PathVariable String postId,
            @Valid @RequestBody CommentRequest request) {

        String username = getCurrentUsername();
        log.info("API: Add comment to post {} by {}", postId, username);
        CommentResponse result = commentService.addComment(postId, request, username);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CommentResponse>builder()
                        .code(1000)
                        .message("Thêm bình luận thành công")
                        .result(result)
                        .build()
        );
    }

    // ==================== 2. DANH SÁCH COMMENT (PUBLIC) ====================
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> getComments(
            @PathVariable String postId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        PageResponse<CommentResponse> result = commentService.getComments(postId, page, size);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<CommentResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách bình luận thành công")
                        .result(result)
                        .build()
        );
    }

    // ==================== 3. XÓA COMMENT (AUTHENTICATED) ====================
    @DeleteMapping("/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(
            @PathVariable String postId,
            @PathVariable String commentId) {

        String username = getCurrentUsername();
        boolean isAdmin = hasRole("ROLE_ADMIN");
        commentService.deleteComment(postId, commentId, username, isAdmin);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa bình luận thành công")
                        .build()
        );
    }

    // ==================== HELPER ====================

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}
