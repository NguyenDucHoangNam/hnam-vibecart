package com.vibecart.api.modules.social.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.request.PostRequest;
import com.vibecart.api.modules.social.dto.response.PostResponse;
import com.vibecart.api.modules.social.service.PostService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/posts")
@RequiredArgsConstructor
@Slf4j
public class PostController {

    private final PostService postService;
    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<PostResponse>> createPost(@Valid @RequestBody PostRequest request) {
        String username = SecurityUtils.getCurrentUsername();
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
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String creatorId) {

        String username = SecurityUtils.getOptionalUsername();
        PageResponse<PostResponse> result = postService.getPosts(page, size, creatorId, username);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<PostResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách bài viết thành công")
                        .result(result)
                        .build()
        );
    }
    @GetMapping("/{postId}")
    public ResponseEntity<ApiResponse<PostResponse>> getPost(@PathVariable String postId) {
        String username = SecurityUtils.getOptionalUsername();
        PostResponse result = postService.getPost(postId, username);

        return ResponseEntity.ok(
                ApiResponse.<PostResponse>builder()
                        .code(1000)
                        .message("Lấy chi tiết bài viết thành công")
                        .result(result)
                        .build()
        );
    }
    @PutMapping("/{postId}")
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<PostResponse>> updatePost(
            @PathVariable String postId,
            @Valid @RequestBody PostRequest request) {

        String username = SecurityUtils.getCurrentUsername();
        PostResponse result = postService.updatePost(postId, request, username);

        return ResponseEntity.ok(
                ApiResponse.<PostResponse>builder()
                        .code(1000)
                        .message("Cập nhật bài viết thành công")
                        .result(result)
                        .build()
        );
    }
    @DeleteMapping("/{postId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<ApiResponse<Void>> deletePost(@PathVariable String postId) {
        String username = SecurityUtils.getCurrentUsername();
        boolean isAdmin = SecurityUtils.hasRole("ROLE_ADMIN");
        postService.deletePost(postId, username, isAdmin);

        return ResponseEntity.ok(
                ApiResponse.<Void>builder()
                        .code(1000)
                        .message("Đã xóa bài viết thành công")
                        .build()
        );
    }
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getFeed(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String username = SecurityUtils.getCurrentUsername();
        PageResponse<PostResponse> result = postService.getFeed(page, size, username);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<PostResponse>>builder()
                        .code(1000)
                        .message("Lấy news feed thành công")
                        .result(result)
                        .build()
        );
    }
    @PostMapping("/{postId}/likes")
    public ResponseEntity<ApiResponse<Boolean>> toggleLike(@PathVariable String postId) {
        String username = SecurityUtils.getCurrentUsername();
        boolean isLiked = postService.toggleLike(postId, username);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(1000)
                        .message(isLiked ? "Đã thích bài viết" : "Đã bỏ thích bài viết")
                        .result(isLiked)
                        .build()
        );
    }
    @GetMapping("/{postId}/likes/check")
    public ResponseEntity<ApiResponse<Boolean>> checkLiked(@PathVariable String postId) {
        String username = SecurityUtils.getCurrentUsername();
        boolean isLiked = postService.isLiked(postId, username);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(1000)
                        .result(isLiked)
                        .build()
        );
    }
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

}
