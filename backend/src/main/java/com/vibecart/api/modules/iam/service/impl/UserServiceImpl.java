package com.vibecart.api.modules.iam.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.dto.request.ChangePasswordRequest;
import com.vibecart.api.modules.iam.dto.request.UpdateProfileRequest;
import com.vibecart.api.modules.iam.dto.response.AuthResponse;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.mapper.UserMapper;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.iam.service.UserService;
import com.vibecart.api.modules.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.ZonedDateTime;
import java.util.Set;

/**
 * Implementation của {@link UserService} quản lý hồ sơ thông tin cá nhân của
 * người dùng.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final UserMapper userMapper;
    private final SearchService searchService;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_REFRESH_TOKEN = "refresh_token:";
    private static final String KEY_USER_SESSIONS = "user_sessions:";

    @Override
    @Transactional(readOnly = true)
    public UserResponse getProfile(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
        return userMapper.toUserResponse(user);
    }

    @Override
    @Transactional
    public AuthResponse updateProfile(String oldUsername, UpdateProfileRequest request) {
        User user = userRepository.findByUsername(oldUsername)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        boolean usernameChanged = false;
        String newUsername = request.getUsername();
        if (newUsername != null && !newUsername.trim().isEmpty() && !newUsername.equals(oldUsername)) {
            newUsername = newUsername.trim();
            if (newUsername.length() < 5 || newUsername.length() > 30) {
                throw new AppException(ErrorCode.USERNAME_INVALID);
            }
            if (!newUsername.matches("^[a-zA-Z0-9._-]+$")) {
                throw new AppException(ErrorCode.USERNAME_INVALID);
            }
            if (userRepository.existsByUsernameAnywhere(newUsername)) {
                throw new AppException(ErrorCode.USERNAME_EXISTED);
            }
            user.setUsername(newUsername);
            usernameChanged = true;
        }

        if (request.getFullName() != null) {
            user.setFullName(request.getFullName());
        }
        if (request.getAvatarUrl() != null) {
            user.setAvatarUrl(request.getAvatarUrl());
        }

        User updatedUser = userRepository.save(user);
        searchService.indexUser(updatedUser);
        log.info("Profile updated for user: {}. Username changed: {}", oldUsername, usernameChanged);

        UserResponse userResponse = userMapper.toUserResponse(updatedUser);

        if (usernameChanged) {
            purgeAllSessions(oldUsername);
            // Sẽ cần đăng nhập lại ở Client do đã thay đổi username và mất toàn bộ token
            return AuthResponse.builder()
                    .user(userResponse)
                    .build();
        } else {
            return AuthResponse.builder()
                    .user(userResponse)
                    .build();
        }
    }

    @Override
    @Transactional
    public void changePassword(String username, ChangePasswordRequest request) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (user.getPassword() == null || !passwordEncoder.matches(request.getOldPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.OLD_PASSWORD_INCORRECT);
        }

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        if (passwordEncoder.matches(request.getNewPassword(), user.getPassword())) {
            throw new AppException(ErrorCode.SAME_PASSWORD);
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);

        purgeAllSessions(username);

        log.info("Password changed for user: {}. All other sessions purged.", username);
    }

    @Override
    @Transactional
    public void deleteAccount(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        user.setStatus("PENDING_DELETION");
        user.setDeleted(true);
        userRepository.save(user);
        searchService.deleteUser(user.getId());

        purgeAllSessions(username);

        log.info("Account deletion requested for user: {}. Status set to PENDING_DELETION.", username);
    }

    private void purgeAllSessions(String username) {
        String sessionKey = KEY_USER_SESSIONS + username;
        Set<String> activeTokens = redisTemplate.opsForSet().members(sessionKey);
        if (activeTokens != null && !activeTokens.isEmpty()) {
            for (String token : activeTokens) {
                redisTemplate.delete(KEY_REFRESH_TOKEN + token);
            }
        }
        redisTemplate.delete(sessionKey);
        log.info("All sessions purged for user: {}", username);
    }
}
