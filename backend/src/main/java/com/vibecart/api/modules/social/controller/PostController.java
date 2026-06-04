package com.vibecart.api.modules.social.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.request.PostRequest;
import com.vibecart.api.modules.social.dto.response.PostResponse;
import com.vibecart.api.modules.social.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;

    // ==================== 1. TẠO BÀI VIẾT (CREATOR ONLY) ====================
    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(@Valid @RequestBody PostRequest request) {
        String username = getCurrentUsername();
        log.info("API: Create post by {}", username);
        PostResponse result = postService.createPost(request, username);

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<PostResponse>builder()
                        .code(1000)
                        .message("Tạo bài viết thành công")
                        .result(result)
                        .build()
        );
    }

    // ==================== 2. DANH SÁCH BÀI VIẾT (PUBLIC) ====================
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String creatorId) {

        String username = getOptionalUsername();
        PageResponse<PostResponse> result = postService.getPosts(page, size, creatorId, username);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<PostResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách bài viết thành công")
                        .result(result)
                        .build()
        );
    }

    // ==================== 3. CHI TIẾT BÀI VIẾT (PUBLIC) ====================
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable String postId) {
        String username = getOptionalUsername();
        PostResponse result = postService.getPost(postId, username);

        return ResponseEntity.ok(
                ApiResponse.<PostResponse>builder()
                        .code(1000)
                        .message("Lấy chi tiết bài viết thành công")
                        .result(result)
                        .build()
        );
    }

    // ==================== 4. CẬP NHẬT BÀI VIẾT (OWNER ONLY) ====================
    @PutMapping("/{postId}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable String postId,
            @Valid @RequestBody PostRequest request) {

        String username = getCurrentUsername();
        PostResponse result = postService.updatePost(postId, request, username);

        return ResponseEntity.ok(
                ApiResponse.<PostResponse>builder()
                        .code(1000)
                        .message("Cập nhật bài viết thành công")
                        .result(result)
                        .build()
        );
    }

    // ==================== 5. XÓA BÀI VIẾT (OWNER HOẶC ADMIN) ====================
    @DeleteMapping("/{postId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable String postId) {
        String username = getCurrentUsername();
        boolean isAdmin = hasRole("ROLE_ADMIN");
        postService.deletePost(postId, username, isAdmin);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa bài viết thành công")
                        .build()
        );
    }

    // ==================== 6. NEWS FEED (AUTHENTICATED) ====================
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String username = getCurrentUsername();
        PageResponse<PostResponse> result = postService.getFeed(page, size, username);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<PostResponse>>builder()
                        .code(1000)
                        .message("Lấy news feed thành công")
                        .result(result)
                        .build()
        );
    }

    // ==================== 7. TOGGLE LIKE (AUTHENTICATED) ====================
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(@PathVariable String postId) {
        String username = getCurrentUsername();
        boolean isLiked = postService.toggleLike(postId, username);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(1000)
                        .message(isLiked ? "Đã thích bài viết" : "Đã bỏ thích bài viết")
                        .result(isLiked)
                        .build()
        );
    }

    // ==================== 8. CHECK LIKED (AUTHENTICATED) ====================
    @GetMapping("/{postId}/likes/check")
    public ResponseEntity<ApiResponse<Boolean>> checkLiked(@PathVariable String postId) {
        String username = getCurrentUsername();
        boolean isLiked = postService.isLiked(postId, username);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(1000)
                        .result(isLiked)
                        .build()
        );
    }

    // ==================== 9. LIKE COUNT (PUBLIC) ====================
    @GetMapping("/{postId}/likes/count")
    public ResponseEntity<ApiResponse<Long>> getLikeCount(@PathVariable String postId) {
        long count = postService.getLikeCount(postId);

        return ResponseEntity.ok(
                ApiResponse.<Long>builder()
                        .code(1000)
                        .result(count)
                        .build()
        );
    }

    // ==================== HELPER ====================

    private String getCurrentUsername() {
        return SecurityContextHolder.getContext().getAuthentication().getName();
    }

    private String getOptionalUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }
        return null;
    }

    private boolean hasRole(String role) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals(role));
    }
}
