package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.config.KafkaTopicConfig;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.social.dto.response.FollowResponse;
import com.vibecart.api.modules.social.entity.Follow;
import com.vibecart.api.modules.social.entity.FollowId;
import com.vibecart.api.modules.social.mapper.FollowMapper;
import com.vibecart.api.modules.social.repository.FollowRepository;
import com.vibecart.api.modules.social.service.FeedFanoutService;
import com.vibecart.api.modules.social.service.FollowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
@Service
@RequiredArgsConstructor
@Slf4j
public class FollowServiceImpl implements FollowService {

    private final FollowRepository followRepository;
    private final UserRepository userRepository;
    private final FollowMapper followMapper;
    private final FeedFanoutService feedFanoutService;
    private final KafkaTemplate<String, InAppNotificationEvent> notificationKafkaTemplate;


    @Override
    @Transactional
    public boolean toggleFollow(String targetUserId, String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);


        if (currentUser.getId().equals(targetUserId)) {
            throw new AppException(ErrorCode.CANNOT_FOLLOW_SELF);
        }


        User targetUser = userRepository.findById(targetUserId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));

        FollowId followId = FollowId.builder()
                .followerId(currentUser.getId())
                .followingId(targetUserId)
                .build();

        if (followRepository.existsById(followId)) {
            followRepository.deleteById(followId);
            log.info("User {} unfollowed {}", currentUsername, targetUser.getUsername());

            feedFanoutService.onUnfollow(currentUser.getId(), targetUserId);

            return false;
        } else {
            Follow follow = Follow.builder()
                    .id(followId)
                    .follower(currentUser)
                    .following(targetUser)
                    .build();
            followRepository.save(follow);
            log.info("User {} followed {}", currentUsername, targetUser.getUsername());

            feedFanoutService.onFollow(currentUser.getId(), targetUserId);

            InAppNotificationEvent event = InAppNotificationEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .recipientId(targetUser.getId())
                    .recipientUsername(targetUser.getUsername())
                    .actorId(currentUser.getId())
                    .actorUsername(currentUser.getUsername())
                    .actorFullName(currentUser.getFullName())
                    .actorAvatarUrl(currentUser.getAvatarUrl())
                    .type("FOLLOW")
                    .content(currentUser.getFullName() + " đã bắt đầu theo dõi bạn")
                    .build();
            notificationKafkaTemplate.send(KafkaTopicConfig.IN_APP_NOTIFICATION_TOPIC, event);

            return true;
        }
    }


    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> getFollowers(String userId, int page, int size, String currentUsername) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Follow> followPage = followRepository.findByIdFollowingId(userId, pageable);


        String currentUserId = getCurrentUserId(currentUsername);
        List<String> followerIds = followPage.getContent().stream()
                .map(f -> f.getFollower().getId())
                .toList();

        Set<String> followedByMeIds = batchCheckFollowed(currentUserId, followerIds);

        List<FollowResponse> content = followPage.getContent().stream()
                .map(follow -> {
                    User follower = follow.getFollower();
                    boolean followedByMe = followedByMeIds.contains(follower.getId());
                    return followMapper.toFollowResponse(follower, followedByMe, follow.getCreatedAt());
                })
                .toList();

        return PageResponse.<FollowResponse>builder()
                .content(content)
                .page(followPage.getNumber())
                .size(followPage.getSize())
                .totalElements(followPage.getTotalElements())
                .totalPages(followPage.getTotalPages())
                .last(followPage.isLast())
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public PageResponse<FollowResponse> getFollowing(String userId, int page, int size, String currentUsername) {
        Pageable pageable = PageRequest.of(page, size);
        Page<Follow> followPage = followRepository.findByIdFollowerId(userId, pageable);


        String currentUserId = getCurrentUserId(currentUsername);
        List<String> followingIds = followPage.getContent().stream()
                .map(f -> f.getFollowing().getId())
                .toList();

        Set<String> followedByMeIds = batchCheckFollowed(currentUserId, followingIds);

        List<FollowResponse> content = followPage.getContent().stream()
                .map(follow -> {
                    User following = follow.getFollowing();
                    boolean followedByMe = followedByMeIds.contains(following.getId());
                    return followMapper.toFollowResponse(following, followedByMe, follow.getCreatedAt());
                })
                .toList();

        return PageResponse.<FollowResponse>builder()
                .content(content)
                .page(followPage.getNumber())
                .size(followPage.getSize())
                .totalElements(followPage.getTotalElements())
                .totalPages(followPage.getTotalPages())
                .last(followPage.isLast())
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public boolean isFollowing(String targetUserId, String currentUsername) {
        User currentUser = findUserByUsername(currentUsername);
        return followRepository.existsByIdFollowerIdAndIdFollowingId(currentUser.getId(), targetUserId);
    }


    @Override
    @Transactional(readOnly = true)
    public long getFollowerCount(String userId) {
        return followRepository.countByIdFollowingId(userId);
    }


    @Override
    @Transactional(readOnly = true)
    public long getFollowingCount(String userId) {
        return followRepository.countByIdFollowerId(userId);
    }



    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    private String getCurrentUserId(String currentUsername) {
        if (currentUsername == null) return null;
        return userRepository.findByUsername(currentUsername)
                .map(User::getId)
                .orElse(null);
    }
    private Set<String> batchCheckFollowed(String currentUserId, List<String> targetIds) {
        if (currentUserId == null || targetIds.isEmpty()) return Set.of();
        return new HashSet<>(followRepository.findFollowingIdsIn(currentUserId, targetIds));
    }
}
