package com.vibecart.api.modules.iam.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.dto.request.UpdateUserRolesRequest;
import com.vibecart.api.modules.iam.dto.request.UpdateUserStatusRequest;
import com.vibecart.api.modules.iam.dto.response.UserResponse;
import com.vibecart.api.modules.iam.entity.Role;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.mapper.UserMapper;
import com.vibecart.api.modules.iam.repository.RoleRepository;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.iam.service.AdminUserService;
import com.vibecart.api.modules.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.Set;
@Service
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserServiceImpl implements AdminUserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserMapper userMapper;
    private final SearchService searchService;
    private final StringRedisTemplate redisTemplate;

    private static final String KEY_REFRESH_TOKEN = "refresh_token:";
    private static final String KEY_USER_SESSIONS = "user_sessions:";
    private static final Set<String> VALID_STATUSES = Set.of(
            "ACTIVE", "BANNED", "PENDING_VERIFICATION", "PENDING_DELETION");

    @Override
    @Transactional(readOnly = true)
    public Page<UserResponse> searchUsers(String search, String status, String role, Pageable pageable) {
        String normalizedSearch = (search == null || search.trim().isEmpty()) ? null
                : "%" + search.trim().toLowerCase() + "%";
        String normalizedStatus = (status == null || status.trim().isEmpty() || "ALL".equalsIgnoreCase(status)) ? null
                : status.trim();
        String normalizedRole = (role == null || role.trim().isEmpty() || "ALL".equalsIgnoreCase(role)) ? null
                : role.trim();

        return userRepository.searchUsers(normalizedSearch, normalizedStatus, normalizedRole, pageable)
                .map(userMapper::toUserResponse);
    }

    @Override
    @Transactional
    public UserResponse updateUserStatus(String userId, UpdateUserStatusRequest request, String adminUsername) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (targetUser.getUsername().equals(adminUsername)) {
            throw new AppException(ErrorCode.CANNOT_CHANGE_OWN_STATUS);
        }

        String newStatus = request.getStatus().toUpperCase();
        if (!VALID_STATUSES.contains(newStatus)) {
            throw new AppException(ErrorCode.INVALID_STATUS);
        }

        targetUser.setStatus(newStatus);
        User updatedUser = userRepository.save(targetUser);
        searchService.indexUser(updatedUser);

        if ("BANNED".equals(newStatus)) {
            purgeAllSessions(targetUser.getUsername());
            log.info("User {} BANNED by admin {}. All sessions purged.", targetUser.getUsername(), adminUsername);
        } else {
            log.info("User {} status updated to {} by admin {}", targetUser.getUsername(), newStatus, adminUsername);
        }

        return userMapper.toUserResponse(updatedUser);
    }

    @Override
    @Transactional
    public UserResponse updateUserRoles(String userId, UpdateUserRolesRequest request, String adminUsername) {
        User targetUser = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        if (targetUser.getUsername().equals(adminUsername)) {
            throw new AppException(ErrorCode.CANNOT_CHANGE_OWN_ROLE);
        }

        if (request.getRoles() == null || request.getRoles().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_INPUT);
        }
        String roleName = request.getRoles().iterator().next();
        Role role = roleRepository.findByName(roleName)
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_INPUT));

        targetUser.setRole(role);
        User updatedUser = userRepository.save(targetUser);
        searchService.indexUser(updatedUser);

        log.info("User {} roles updated to {} by admin {}", targetUser.getUsername(), request.getRoles(),
                adminUsername);

        purgeAllSessions(targetUser.getUsername());

        return userMapper.toUserResponse(updatedUser);
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
