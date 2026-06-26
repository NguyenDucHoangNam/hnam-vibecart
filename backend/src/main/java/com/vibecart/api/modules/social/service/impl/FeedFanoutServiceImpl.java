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
 * Lớp triển khai của {@link FeedFanoutService} quản lý cập nhật timeline bằng Redis.
 * <p>
 * Lưu trữ danh sách bài viết dưới dạng Redis List (dạng Cache Timeline).
 * Tất cả các thao tác cập nhật (fan-out, backfill, cleanup) được xử lý bất đồng bộ (sử dụng {@link Async})
 * để đảm bảo tối ưu hóa thời gian phản hồi cho các luồng xử lý chính.
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
    private static final int BACKFILL_SIZE = 20;

    /**
     * Fan-out bài viết mới tới tất cả người theo dõi (followers) và bản thân người tạo.
     */
    @Override
    @Async("feedFanoutExecutor")
    public void fanoutNewPost(String creatorId, String postId) {
        try {
            List<String> followerIds = followRepository.findAllFollowerIdsByFollowingId(creatorId);
            log.info("Fan-out post {} from creator {} to {} followers", postId, creatorId, followerIds.size());

            pushToTimeline(creatorId, postId);

            for (String followerId : followerIds) {
                pushToTimeline(followerId, postId);
            }

            log.info("Fan-out completed for post {}: {} timelines updated", postId, followerIds.size() + 1);
        } catch (Exception e) {
            log.error("Fan-out failed for post {} from creator {}: {}", postId, creatorId, e.getMessage(), e);
        }
    }

    /**
     * Xóa bài viết khỏi timeline của người theo dõi và người tạo khi bài viết bị xóa.
     */
    @Override
    @Async("feedFanoutExecutor")
    public void removeDeletedPost(String creatorId, String postId) {
        try {
            List<String> followerIds = followRepository.findAllFollowerIdsByFollowingId(creatorId);
            log.info("Removing deleted post {} from {} timelines", postId, followerIds.size() + 1);

            removeFromTimeline(creatorId, postId);

            for (String followerId : followerIds) {
                removeFromTimeline(followerId, postId);
            }

            log.info("Remove completed for deleted post {}", postId);
        } catch (Exception e) {
            log.error("Remove failed for deleted post {}: {}", postId, e.getMessage(), e);
        }
    }

    /**
     * Đưa các bài viết gần đây của người được follow vào timeline của người follow.
     */
    @Override
    @Async("feedFanoutExecutor")
    public void onFollow(String followerId, String followingId) {
        try {
            List<Post> recentPosts = postRepository.findByCreatorIdOrderByCreatedAtDesc(
                    followingId, PageRequest.of(0, BACKFILL_SIZE)
            ).getContent();

            if (recentPosts.isEmpty()) {
                log.debug("No posts to backfill from user {} to follower {}", followingId, followerId);
                return;
            }

            String timelineKey = TIMELINE_KEY_PREFIX + followerId;

            for (int i = recentPosts.size() - 1; i >= 0; i--) {
                String postId = recentPosts.get(i).getId();
                redisTemplate.opsForList().remove(timelineKey, 0, postId);
                redisTemplate.opsForList().leftPush(timelineKey, postId);
            }

            redisTemplate.opsForList().trim(timelineKey, 0, MAX_TIMELINE_SIZE - 1);
            redisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);

            log.info("Backfill {} posts from user {} to follower {}", recentPosts.size(), followingId, followerId);
        } catch (Exception e) {
            log.error("Backfill failed on follow: follower={}, following={}: {}", followerId, followingId, e.getMessage(), e);
        }
    }

    /**
     * Dọn dẹp timeline của người theo dõi sau khi unfollow.
     */
    @Override
    @Async("feedFanoutExecutor")
    public void onUnfollow(String followerId, String followingId) {
        try {
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

    /**
     * Nạp dữ liệu (warm-up) timeline từ DB lên Redis khi cache bị trống.
     */
    @Override
    @Async("feedFanoutExecutor")
    public void warmUpTimeline(String userId) {
        try {
            String timelineKey = TIMELINE_KEY_PREFIX + userId;

            Long existingSize = redisTemplate.opsForList().size(timelineKey);
            if (existingSize != null && existingSize > 0) {
                log.debug("Timeline already exists for user {}, skipping warm-up", userId);
                return;
            }

            Slice<Post> feedSlice = postRepository.findFeedByUserId(userId, PageRequest.of(0, MAX_TIMELINE_SIZE));
            List<Post> feedPosts = feedSlice.getContent();

            if (feedPosts.isEmpty()) {
                log.debug("No feed posts to warm up for user {}", userId);
                return;
            }

            List<String> postIds = new ArrayList<>();
            for (Post post : feedPosts) {
                postIds.add(post.getId());
            }

            redisTemplate.opsForList().rightPushAll(timelineKey, postIds);
            redisTemplate.opsForList().trim(timelineKey, 0, MAX_TIMELINE_SIZE - 1);
            redisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);

            log.info("Warmed up timeline for user {} with {} posts", userId, feedPosts.size());
        } catch (Exception e) {
            log.error("Warm-up failed for user {}: {}", userId, e.getMessage(), e);
        }
    }

    private void pushToTimeline(String userId, String postId) {
        String timelineKey = TIMELINE_KEY_PREFIX + userId;
        redisTemplate.opsForList().leftPush(timelineKey, postId);
        redisTemplate.opsForList().trim(timelineKey, 0, MAX_TIMELINE_SIZE - 1);
        redisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);
    }

    private void removeFromTimeline(String userId, String postId) {
        String timelineKey = TIMELINE_KEY_PREFIX + userId;
        redisTemplate.opsForList().remove(timelineKey, 0, postId);
    }
}
