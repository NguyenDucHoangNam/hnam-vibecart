package com.vibecart.api.modules.chat.service.impl;

import com.vibecart.api.modules.chat.dto.response.PresenceResponse;
import com.vibecart.api.modules.chat.service.PresenceService;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.social.repository.FollowRepository;
import com.vibecart.api.modules.social.dto.response.FollowResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class PresenceServiceImpl implements PresenceService {

    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final FollowRepository followRepository;

    private static final String PRESENCE_KEY_PREFIX = "presence:user:";
    private static final String LAST_ACTIVE_KEY_PREFIX = "presence:last_active:";
    private static final String ACTIVE_USERS_SET = "presence:active_users";
    private static final long ONLINE_TTL_SECONDS = 40;
    @Override
    public void setOnline(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            String userId = user.getId();
            String key = PRESENCE_KEY_PREFIX + userId;
            String lastActiveKey = LAST_ACTIVE_KEY_PREFIX + userId;

            redisTemplate.opsForValue().set(key, "ONLINE", ONLINE_TTL_SECONDS, TimeUnit.SECONDS);
            redisTemplate.opsForValue().set(lastActiveKey, String.valueOf(Instant.now().toEpochMilli()));

            redisTemplate.opsForSet().add(ACTIVE_USERS_SET, userId);

            log.debug("Set user presence to ONLINE: username={}, userId={}", username, userId);
        });
    }
    @Override
    public void setOffline(String username) {
        userRepository.findByUsername(username).ifPresent(user -> {
            String userId = user.getId();
            String key = PRESENCE_KEY_PREFIX + userId;
            String lastActiveKey = LAST_ACTIVE_KEY_PREFIX + userId;

            redisTemplate.delete(key);
            redisTemplate.opsForValue().set(lastActiveKey, String.valueOf(Instant.now().toEpochMilli()));

            redisTemplate.opsForSet().remove(ACTIVE_USERS_SET, userId);

            log.debug("Set user presence to OFFLINE: username={}, userId={}", username, userId);
        });
    }
    @Override
    public PresenceResponse getUserPresence(String userId) {
        String key = PRESENCE_KEY_PREFIX + userId;
        String lastActiveKey = LAST_ACTIVE_KEY_PREFIX + userId;

        Boolean isOnline = redisTemplate.hasKey(key);
        String lastActiveVal = redisTemplate.opsForValue().get(lastActiveKey);

        Instant lastActiveAt = null;
        if (lastActiveVal != null) {
            try {
                lastActiveAt = Instant.ofEpochMilli(Long.parseLong(lastActiveVal));
            } catch (NumberFormatException e) {
                log.warn("Invalid last active timestamp in Redis for user {}: {}", userId, lastActiveVal);
            }
        }

        if (lastActiveAt == null) {
            User user = userRepository.findById(userId).orElse(null);
            if (user != null && user.getUpdatedAt() != null) {
                lastActiveAt = user.getUpdatedAt().toInstant();
            }
        }

        return PresenceResponse.builder()
                .userId(userId)
                .status(Boolean.TRUE.equals(isOnline) ? "ONLINE" : "OFFLINE")
                .lastActiveAt(lastActiveAt)
                .build();
    }
    @Override
    public List<FollowResponse> getActiveUsers(String currentUsername) {
        log.info("Fetching all active/online users from Redis SET...");
        try {
            Set<String> userIds = redisTemplate.opsForSet().members(ACTIVE_USERS_SET);
            if (userIds == null || userIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<String> verifiedOnlineIds = userIds.stream()
                    .filter(uid -> {
                        Boolean exists = redisTemplate.hasKey(PRESENCE_KEY_PREFIX + uid);
                        if (!Boolean.TRUE.equals(exists)) {
                            redisTemplate.opsForSet().remove(ACTIVE_USERS_SET, uid);
                            return false;
                        }
                        return true;
                    })
                    .collect(Collectors.toList());

            if (verifiedOnlineIds.isEmpty()) {
                return Collections.emptyList();
            }

            List<User> activeUsers = userRepository.findAllById(verifiedOnlineIds);

            String currentUserId = null;
            if (currentUsername != null && !currentUsername.equals("anonymousUser") && !currentUsername.isBlank()) {
                var curUserOpt = userRepository.findByUsername(currentUsername);
                if (curUserOpt.isPresent()) {
                    currentUserId = curUserOpt.get().getId();
                }
            }

            final String finalCurrentUserId = currentUserId;
            return activeUsers.stream()
                    .filter(u -> finalCurrentUserId == null || !u.getId().equals(finalCurrentUserId))
                    .map(user -> {
                        boolean followedByMe = false;
                        if (finalCurrentUserId != null) {
                            followedByMe = followRepository.existsByIdFollowerIdAndIdFollowingId(finalCurrentUserId,
                                    user.getId());
                        }

                        return new FollowResponse(
                                user.getId(),
                                user.getUsername(),
                                user.getFullName(),
                                user.getAvatarUrl(),
                                followedByMe,
                                ZonedDateTime.now());
                    })
                    .limit(10)
                    .toList();
        } catch (Exception e) {
            log.error("Failed to fetch active/online users", e);
            return Collections.emptyList();
        }
    }
}
