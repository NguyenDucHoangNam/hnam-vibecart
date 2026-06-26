package com.vibecart.api.modules.social.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.response.FollowResponse;
import com.vibecart.api.modules.social.service.FollowService;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Collections;
import java.util.stream.Collectors;

/**
 * Controller quản lý tính năng theo dõi (follow/unfollow) và hồ sơ người dùng.
 */
@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
@Slf4j
public class FollowController {

    private final FollowService followService;
    private final UserRepository userRepository;

    /**
     * Bật/tắt theo dõi một người dùng.
     */
    @PostMapping("/{userId}/follow")
    public ResponseEntity<ApiResponse<Boolean>> toggleFollow(@PathVariable String userId) {
        String username = SecurityUtils.getCurrentUsername();
        log.info("API: Toggle follow for user {} by {}", userId, username);
        boolean isFollowing = followService.toggleFollow(userId, username);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(1000)
                        .message(isFollowing ? "Đã theo dõi thành công" : "Đã hủy theo dõi")
                        .result(isFollowing)
                        .build());
    }

    /**
     * Lấy danh sách người theo dõi của một người dùng.
     */
    @GetMapping("/{userId}/followers")
    public ResponseEntity<ApiResponse<PageResponse<FollowResponse>>> getFollowers(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String username = SecurityUtils.getOptionalUsername();
        PageResponse<FollowResponse> result = followService.getFollowers(userId, page, size, username);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<FollowResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách người theo dõi thành công")
                        .result(result)
                        .build());
    }

    /**
     * Lấy danh sách những người mà người dùng đang theo dõi.
     */
    @GetMapping("/{userId}/following")
    public ResponseEntity<ApiResponse<PageResponse<FollowResponse>>> getFollowing(
            @PathVariable String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        String username = SecurityUtils.getOptionalUsername();
        PageResponse<FollowResponse> result = followService.getFollowing(userId, page, size, username);

        return ResponseEntity.ok(
                ApiResponse.<PageResponse<FollowResponse>>builder()
                        .code(1000)
                        .message("Lấy danh sách đang theo dõi thành công")
                        .result(result)
                        .build());
    }

    /**
     * Kiểm tra trạng thái theo dõi giữa người dùng hiện tại và người dùng chỉ định.
     */
    @GetMapping("/{userId}/follow/check")
    public ResponseEntity<ApiResponse<Boolean>> checkFollow(@PathVariable String userId) {
        String username = SecurityUtils.getCurrentUsername();
        boolean isFollowing = followService.isFollowing(userId, username);

        return ResponseEntity.ok(
                ApiResponse.<Boolean>builder()
                        .code(1000)
                        .result(isFollowing)
                        .build());
    }

    /**
     * Lấy số lượng người theo dõi của một người dùng.
     */
    @GetMapping("/{userId}/followers/count")
    public ResponseEntity<ApiResponse<Long>> getFollowerCount(@PathVariable String userId) {
        long count = followService.getFollowerCount(userId);

        return ResponseEntity.ok(
                ApiResponse.<Long>builder()
                        .code(1000)
                        .result(count)
                        .build());
    }

    /**
     * Lấy số lượng người mà người dùng đang theo dõi.
     */
    @GetMapping("/{userId}/following/count")
    public ResponseEntity<ApiResponse<Long>> getFollowingCount(@PathVariable String userId) {
        long count = followService.getFollowingCount(userId);

        return ResponseEntity.ok(
                ApiResponse.<Long>builder()
                        .code(1000)
                        .result(count)
                        .build());
    }

    /**
     * Lấy thông tin hồ sơ công khai của người dùng.
     */
    @GetMapping("/{userId}/profile")
    public ResponseEntity<ApiResponse<UserResponse>> getUserProfile(@PathVariable String userId) {
        log.info("API: Get profile of user ID {}", userId);
        User userEntity = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        UserResponse userResponse = UserResponse.builder()
                .id(userEntity.getId())
                .username(userEntity.getUsername())
                .email(userEntity.getEmail())
                .fullName(userEntity.getFullName())
                .avatarUrl(userEntity.getAvatarUrl())
                .status(userEntity.getStatus())
                .oauthProvider(userEntity.getOauthProvider())
                .roles(userEntity.getRole() != null
                        ? java.util.Set.of(userEntity.getRole().getName())
                        : java.util.Collections.emptySet())
                .createdAt(userEntity.getCreatedAt())
                .build();

        return ResponseEntity.ok(
                ApiResponse.<UserResponse>builder()
                        .code(1000)
                        .message("Lấy thông tin cá nhân thành công")
                        .result(userResponse)
                        .build());
    }

}
