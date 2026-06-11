package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.modules.social.entity.Post;
import com.vibecart.api.modules.social.repository.FollowRepository;
import com.vibecart.api.modules.social.repository.PostRepository;
import com.vibecart.api.modules.social.service.FeedFanoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Triển khai Fan-out on Write cho News Feed sử dụng Redis List.
 * <p>
 * Mỗi user có một Redis List {@code feed:timeline:{userId}} chứa danh sách postId
 * sắp xếp mới nhất → cũ nhất. Tối đa {@value #MAX_TIMELINE_SIZE} phần tử.
 * <p>
 * Tất cả phương thức chạy bất đồng bộ trên thread pool {@code feedFanoutExecutor}
 * để không block API request chính.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FeedFanoutServiceImpl implements FeedFanoutService {

    private final StringRedisTemplate redisTemplate;
    private final FollowRepository followRepository;
    private final PostRepository postRepository;

    static final String TIMELINE_KEY_PREFIX = "feed:timeline:";
    static final int MAX_TIMELINE_SIZE = 500;
    static final long TIMELINE_TTL_DAYS = 30;
    /** Số bài gần nhất của người được follow sẽ backfill vào timeline khi follow mới */
    private static final int BACKFILL_SIZE = 20;

    @Override
    @Async("feedFanoutExecutor")
    public void fanoutNewPost(String creatorId, String postId) {
        try {
            // Lấy toàn bộ follower IDs
            List<String> followerIds = followRepository.findAllFollowerIdsByFollowingId(creatorId);
            log.info("Fan-out post {} from creator {} to {} followers", postId, creatorId, followerIds.size());

            // Đẩy vào timeline của chính creator
            pushToTimeline(creatorId, postId);

            // Đẩy vào timeline của từng follower
            for (String followerId : followerIds) {
                pushToTimeline(followerId, postId);
            }

            log.info("Fan-out completed for post {}: {} timelines updated", postId, followerIds.size() + 1);
        } catch (Exception e) {
            log.error("Fan-out failed for post {} from creator {}: {}", postId, creatorId, e.getMessage(), e);
        }
    }

    @Override
    @Async("feedFanoutExecutor")
    public void removeDeletedPost(String creatorId, String postId) {
        try {
            List<String> followerIds = followRepository.findAllFollowerIdsByFollowingId(creatorId);
            log.info("Removing deleted post {} from {} timelines", postId, followerIds.size() + 1);

            // Xóa khỏi timeline của creator
            removeFromTimeline(creatorId, postId);

            // Xóa khỏi timeline của từng follower
            for (String followerId : followerIds) {
                removeFromTimeline(followerId, postId);
            }

            log.info("Remove completed for deleted post {}", postId);
        } catch (Exception e) {
            log.error("Remove failed for deleted post {}: {}", postId, e.getMessage(), e);
        }
    }

    @Override
    @Async("feedFanoutExecutor")
    public void onFollow(String followerId, String followingId) {
        try {
            // Lấy N bài gần nhất của người được follow
            List<Post> recentPosts = postRepository.findByCreatorIdOrderByCreatedAtDesc(
                    followingId, PageRequest.of(0, BACKFILL_SIZE)
            ).getContent();

            if (recentPosts.isEmpty()) {
                log.debug("No posts to backfill from user {} to follower {}", followingId, followerId);
                return;
            }

            String timelineKey = TIMELINE_KEY_PREFIX + followerId;

            // Đẩy từng postId vào timeline (theo thứ tự cũ → mới để LPUSH ra đúng thứ tự)
            for (int i = recentPosts.size() - 1; i >= 0; i--) {
                String postId = recentPosts.get(i).getId();
                // Chỉ thêm nếu chưa có trong timeline (tránh duplicate)
                Long existingCount = redisTemplate.opsForList().remove(timelineKey, 0, postId);
                redisTemplate.opsForList().leftPush(timelineKey, postId);
            }

            // Trim và gia hạn TTL
            redisTemplate.opsForList().trim(timelineKey, 0, MAX_TIMELINE_SIZE - 1);
            redisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);

            log.info("Backfill {} posts from user {} to follower {}", recentPosts.size(), followingId, followerId);
        } catch (Exception e) {
            log.error("Backfill failed on follow: follower={}, following={}: {}", followerId, followingId, e.getMessage(), e);
        }
    }

    @Override
    @Async("feedFanoutExecutor")
    public void onUnfollow(String followerId, String followingId) {
        try {
            // Lấy danh sách bài viết của người bị unfollow
            List<Post> posts = postRepository.findByCreatorIdOrderByCreatedAtDesc(
                    followingId, PageRequest.of(0, MAX_TIMELINE_SIZE)
            ).getContent();

            if (posts.isEmpty()) {
                log.debug("No posts to remove from timeline of follower {} after unfollowing {}", followerId, followingId);
                return;
            }

            String timelineKey = TIMELINE_KEY_PREFIX + followerId;
            int removedCount = 0;

            for (Post post : posts) {
                Long removed = redisTemplate.opsForList().remove(timelineKey, 0, post.getId());
                if (removed != null && removed > 0) {
                    removedCount++;
                }
            }

            log.info("Removed {} posts of user {} from timeline of follower {}", removedCount, followingId, followerId);
        } catch (Exception e) {
            log.error("Cleanup failed on unfollow: follower={}, following={}: {}", followerId, followingId, e.getMessage(), e);
        }
    }

    @Override
    @Async("feedFanoutExecutor")
    public void warmUpTimeline(String userId) {
        try {
            String timelineKey = TIMELINE_KEY_PREFIX + userId;

            // Kiểm tra nếu timeline đã tồn tại (race condition protection)
            Long existingSize = redisTemplate.opsForList().size(timelineKey);
            if (existingSize != null && existingSize > 0) {
                log.debug("Timeline already exists for user {}, skipping warm-up", userId);
                return;
            }

            // Lấy feed từ DB (query cũ)
            Slice<Post> feedSlice = postRepository.findFeedByUserId(userId, PageRequest.of(0, MAX_TIMELINE_SIZE));
            List<Post> feedPosts = feedSlice.getContent();

            if (feedPosts.isEmpty()) {
                log.debug("No feed posts to warm up for user {}", userId);
                return;
            }

            // Đẩy postId vào Redis List (bài mới nhất đẩy trước → LPUSH → đúng thứ tự)
            List<String> postIds = new ArrayList<>();
            for (Post post : feedPosts) {
                postIds.add(post.getId());
            }

            // rightPushAll để giữ nguyên thứ tự (bài mới nhất ở đầu list)
            redisTemplate.opsForList().rightPushAll(timelineKey, postIds);
            redisTemplate.opsForList().trim(timelineKey, 0, MAX_TIMELINE_SIZE - 1);
            redisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);

            log.info("Warmed up timeline for user {} with {} posts", userId, feedPosts.size());
        } catch (Exception e) {
            log.error("Warm-up failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    /**
     * Đẩy postId vào đầu timeline (mới nhất) và giới hạn kích thước.
     */
    private void pushToTimeline(String userId, String postId) {
        String timelineKey = TIMELINE_KEY_PREFIX + userId;
        redisTemplate.opsForList().leftPush(timelineKey, postId);
        redisTemplate.opsForList().trim(timelineKey, 0, MAX_TIMELINE_SIZE - 1);
        redisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);
    }

    /**
     * Xóa postId khỏi timeline.
     */
    private void removeFromTimeline(String userId, String postId) {
        String timelineKey = TIMELINE_KEY_PREFIX + userId;
        redisTemplate.opsForList().remove(timelineKey, 0, postId);
    }
}
