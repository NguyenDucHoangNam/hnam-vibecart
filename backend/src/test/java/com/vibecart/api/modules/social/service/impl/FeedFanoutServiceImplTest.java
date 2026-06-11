package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.modules.social.entity.Post;
import com.vibecart.api.modules.social.repository.FollowRepository;
import com.vibecart.api.modules.social.repository.PostRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.SliceImpl;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.ZonedDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeedFanoutServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private FollowRepository followRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private ListOperations<String, String> listOperations;

    @InjectMocks
    private FeedFanoutServiceImpl feedFanoutService;

    private static final String CREATOR_ID = "creator-001";
    private static final String FOLLOWER_1 = "follower-001";
    private static final String FOLLOWER_2 = "follower-002";
    private static final String POST_ID = "post-001";
    private static final String TIMELINE_PREFIX = "feed:timeline:";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForList()).thenReturn(listOperations);
    }

    // ==================== fanoutNewPost ====================
    @Nested
    @DisplayName("fanoutNewPost")
    class FanoutNewPostTests {

        @Test
        void fanoutNewPost_pushesToCreatorAndAllFollowers() {
            when(followRepository.findAllFollowerIdsByFollowingId(CREATOR_ID))
                    .thenReturn(List.of(FOLLOWER_1, FOLLOWER_2));

            feedFanoutService.fanoutNewPost(CREATOR_ID, POST_ID);

            // Verify push to creator timeline
            verify(listOperations).leftPush(TIMELINE_PREFIX + CREATOR_ID, POST_ID);
            verify(listOperations).trim(eq(TIMELINE_PREFIX + CREATOR_ID), eq(0L), eq(499L));

            // Verify push to each follower timeline
            verify(listOperations).leftPush(TIMELINE_PREFIX + FOLLOWER_1, POST_ID);
            verify(listOperations).leftPush(TIMELINE_PREFIX + FOLLOWER_2, POST_ID);

            // Verify TTL set for all 3 timelines
            verify(redisTemplate, times(3)).expire(anyString(), eq(30L), any());
        }

        @Test
        void fanoutNewPost_noFollowers_onlyPushesToCreator() {
            when(followRepository.findAllFollowerIdsByFollowingId(CREATOR_ID))
                    .thenReturn(List.of());

            feedFanoutService.fanoutNewPost(CREATOR_ID, POST_ID);

            verify(listOperations).leftPush(TIMELINE_PREFIX + CREATOR_ID, POST_ID);
            verify(listOperations, times(1)).leftPush(anyString(), anyString());
        }
    }

    // ==================== removeDeletedPost ====================
    @Nested
    @DisplayName("removeDeletedPost")
    class RemoveDeletedPostTests {

        @Test
        void removeDeletedPost_removesFromCreatorAndAllFollowers() {
            when(followRepository.findAllFollowerIdsByFollowingId(CREATOR_ID))
                    .thenReturn(List.of(FOLLOWER_1, FOLLOWER_2));

            feedFanoutService.removeDeletedPost(CREATOR_ID, POST_ID);

            verify(listOperations).remove(TIMELINE_PREFIX + CREATOR_ID, 0, POST_ID);
            verify(listOperations).remove(TIMELINE_PREFIX + FOLLOWER_1, 0, POST_ID);
            verify(listOperations).remove(TIMELINE_PREFIX + FOLLOWER_2, 0, POST_ID);
        }

        @Test
        void removeDeletedPost_noFollowers_onlyRemovesFromCreator() {
            when(followRepository.findAllFollowerIdsByFollowingId(CREATOR_ID))
                    .thenReturn(List.of());

            feedFanoutService.removeDeletedPost(CREATOR_ID, POST_ID);

            verify(listOperations).remove(TIMELINE_PREFIX + CREATOR_ID, 0, POST_ID);
            verify(listOperations, times(1)).remove(anyString(), anyLong(), anyString());
        }
    }

    // ==================== onFollow ====================
    @Nested
    @DisplayName("onFollow")
    class OnFollowTests {

        @Test
        void onFollow_backfillsRecentPostsToFollowerTimeline() {
            Post post1 = Post.builder().content("Post 1").build();
            post1.setId("post-1");
            Post post2 = Post.builder().content("Post 2").build();
            post2.setId("post-2");

            Page<Post> postPage = new PageImpl<>(List.of(post1, post2));
            when(postRepository.findByCreatorIdOrderByCreatedAtDesc(
                    eq(CREATOR_ID), any(PageRequest.class))).thenReturn(postPage);
            when(listOperations.remove(anyString(), anyLong(), anyString())).thenReturn(0L);

            feedFanoutService.onFollow(FOLLOWER_1, CREATOR_ID);

            // Verify posts pushed in reverse order (oldest first for LPUSH)
            verify(listOperations).leftPush(TIMELINE_PREFIX + FOLLOWER_1, "post-2");
            verify(listOperations).leftPush(TIMELINE_PREFIX + FOLLOWER_1, "post-1");

            // Verify trim and TTL
            verify(listOperations).trim(TIMELINE_PREFIX + FOLLOWER_1, 0, 499);
            verify(redisTemplate).expire(eq(TIMELINE_PREFIX + FOLLOWER_1), eq(30L), any());
        }

        @Test
        void onFollow_noPosts_doesNothing() {
            Page<Post> emptyPage = new PageImpl<>(List.of());
            when(postRepository.findByCreatorIdOrderByCreatedAtDesc(
                    eq(CREATOR_ID), any(PageRequest.class))).thenReturn(emptyPage);

            feedFanoutService.onFollow(FOLLOWER_1, CREATOR_ID);

            verify(listOperations, never()).leftPush(anyString(), anyString());
        }
    }

    // ==================== onUnfollow ====================
    @Nested
    @DisplayName("onUnfollow")
    class OnUnfollowTests {

        @Test
        void onUnfollow_removesPostsOfUnfollowedUserFromTimeline() {
            Post post1 = Post.builder().content("Post 1").build();
            post1.setId("post-1");
            Post post2 = Post.builder().content("Post 2").build();
            post2.setId("post-2");

            Page<Post> postPage = new PageImpl<>(List.of(post1, post2));
            when(postRepository.findByCreatorIdOrderByCreatedAtDesc(
                    eq(CREATOR_ID), any(PageRequest.class))).thenReturn(postPage);
            when(listOperations.remove(anyString(), anyLong(), anyString())).thenReturn(1L);

            feedFanoutService.onUnfollow(FOLLOWER_1, CREATOR_ID);

            verify(listOperations).remove(TIMELINE_PREFIX + FOLLOWER_1, 0, "post-1");
            verify(listOperations).remove(TIMELINE_PREFIX + FOLLOWER_1, 0, "post-2");
        }

        @Test
        void onUnfollow_noPosts_doesNothing() {
            Page<Post> emptyPage = new PageImpl<>(List.of());
            when(postRepository.findByCreatorIdOrderByCreatedAtDesc(
                    eq(CREATOR_ID), any(PageRequest.class))).thenReturn(emptyPage);

            feedFanoutService.onUnfollow(FOLLOWER_1, CREATOR_ID);

            verify(listOperations, never()).remove(anyString(), anyLong(), anyString());
        }
    }

    // ==================== warmUpTimeline ====================
    @Nested
    @DisplayName("warmUpTimeline")
    class WarmUpTimelineTests {

        @Test
        void warmUpTimeline_populatesFromDatabase() {
            String userId = "user-001";
            Post post1 = Post.builder().content("Post 1").build();
            post1.setId("post-1");

            when(listOperations.size(TIMELINE_PREFIX + userId)).thenReturn(0L);
            when(postRepository.findFeedByUserId(eq(userId), any(PageRequest.class)))
                    .thenReturn(new SliceImpl<>(List.of(post1)));

            feedFanoutService.warmUpTimeline(userId);

            verify(listOperations).rightPushAll(TIMELINE_PREFIX + userId, List.of("post-1"));
            verify(listOperations).trim(TIMELINE_PREFIX + userId, 0, 499);
            verify(redisTemplate).expire(eq(TIMELINE_PREFIX + userId), eq(30L), any());
        }

        @Test
        void warmUpTimeline_skipsIfAlreadyExists() {
            String userId = "user-001";

            when(listOperations.size(TIMELINE_PREFIX + userId)).thenReturn(10L);

            feedFanoutService.warmUpTimeline(userId);

            verify(postRepository, never()).findFeedByUserId(anyString(), any());
            verify(listOperations, never()).rightPushAll(anyString(), anyList());
        }

        @Test
        void warmUpTimeline_emptyFeed_doesNotPush() {
            String userId = "user-001";

            when(listOperations.size(TIMELINE_PREFIX + userId)).thenReturn(0L);
            when(postRepository.findFeedByUserId(eq(userId), any(PageRequest.class)))
                    .thenReturn(new SliceImpl<>(List.of()));

            feedFanoutService.warmUpTimeline(userId);

            verify(listOperations, never()).rightPushAll(anyString(), anyList());
        }
    }
}
