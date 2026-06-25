package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.social.dto.response.FollowResponse;
import com.vibecart.api.modules.social.entity.Follow;
import com.vibecart.api.modules.social.entity.FollowId;
import com.vibecart.api.modules.social.mapper.FollowMapper;
import com.vibecart.api.modules.social.repository.FollowRepository;
import com.vibecart.api.modules.social.service.FeedFanoutService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
public class FollowServiceImplTest {

    @Mock
    private FollowRepository followRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private FollowMapper followMapper;

    @Mock
    private FeedFanoutService feedFanoutService;

    @InjectMocks
    private FollowServiceImpl followService;

    private User currentUser;
    private User targetUser;
    private FollowId followId;
    private Follow follow;
    private ZonedDateTime now;

    @BeforeEach
    void setUp() {
        now = ZonedDateTime.now();

        currentUser = User.builder()
                .username("currentuser")
                .email("current@email.com")
                .fullName("Current User")
                .avatarUrl("https://example.com/current.jpg")
                .build();
        currentUser.setId("user-1");

        targetUser = User.builder()
                .username("targetuser")
                .email("target@email.com")
                .fullName("Target User")
                .avatarUrl("https://example.com/target.jpg")
                .build();
        targetUser.setId("user-2");

        followId = FollowId.builder()
                .followerId("user-1")
                .followingId("user-2")
                .build();

        follow = Follow.builder()
                .id(followId)
                .follower(currentUser)
                .following(targetUser)
                .createdAt(now)
                .build();
    }

    // ==================== toggleFollow ====================

    @Nested
    class ToggleFollow {

        @Test
        void toggleFollow_newFollow_returnsTrue() {
            // Given
            when(userRepository.findByUsername("currentuser")).thenReturn(Optional.of(currentUser));
            when(userRepository.findById("user-2")).thenReturn(Optional.of(targetUser));
            when(followRepository.existsById(followId)).thenReturn(false);
            when(followRepository.save(any(Follow.class))).thenReturn(follow);

            // When
            boolean result = followService.toggleFollow("user-2", "currentuser");

            // Then
            assertTrue(result);
            verify(followRepository).save(any(Follow.class));
            verify(followRepository, never()).deleteById(any());
        }

        @Test
        void toggleFollow_existingFollow_unfollows_returnsFalse() {
            // Given
            when(userRepository.findByUsername("currentuser")).thenReturn(Optional.of(currentUser));
            when(userRepository.findById("user-2")).thenReturn(Optional.of(targetUser));
            when(followRepository.existsById(followId)).thenReturn(true);

            // When
            boolean result = followService.toggleFollow("user-2", "currentuser");

            // Then
            assertFalse(result);
            verify(followRepository).deleteById(followId);
            verify(followRepository, never()).save(any());
        }

        @Test
        void toggleFollow_cannotFollowSelf_throwsAppException() {
            // Given: currentUser tries to follow themselves
            when(userRepository.findByUsername("currentuser")).thenReturn(Optional.of(currentUser));

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> followService.toggleFollow("user-1", "currentuser"));

            assertEquals(ErrorCode.CANNOT_FOLLOW_SELF, exception.getErrorCode());
            verify(followRepository, never()).existsById(any());
            verify(followRepository, never()).save(any());
        }

        @Test
        void toggleFollow_targetUserNotFound_throwsAppException() {
            // Given
            when(userRepository.findByUsername("currentuser")).thenReturn(Optional.of(currentUser));
            when(userRepository.findById("nonexistent-user")).thenReturn(Optional.empty());

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> followService.toggleFollow("nonexistent-user", "currentuser"));

            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
            verify(followRepository, never()).save(any());
        }

        @Test
        void toggleFollow_currentUserNotFound_throwsAppException() {
            // Given
            when(userRepository.findByUsername("unknownuser")).thenReturn(Optional.empty());

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> followService.toggleFollow("user-2", "unknownuser"));

            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
            verify(followRepository, never()).save(any());
        }
    }

    // ==================== getFollowers ====================

    @Nested
    class GetFollowers {

        @Test
        void getFollowers_success_returnsPageResponseWithFollowedByMeFlag() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Follow> followPage = new PageImpl<>(List.of(follow), pageable, 1);

            FollowResponse followResponse = new FollowResponse(
                    "user-1", "currentuser", "Current User",
                    "https://example.com/current.jpg", false, now
            );

            when(followRepository.findByIdFollowingId("user-2", pageable)).thenReturn(followPage);
            when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(
                    User.builder().username("viewer").build()
            ));
            // Viewer: findByUsername returns a user with a specific ID
            User viewerUser = User.builder().username("viewer").build();
            viewerUser.setId("user-viewer");
            when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewerUser));
            when(followRepository.findFollowingIdsIn("user-viewer", List.of("user-1"))).thenReturn(List.of());
            when(followMapper.toFollowResponse(currentUser, false, now)).thenReturn(followResponse);

            // When
            PageResponse<FollowResponse> result = followService.getFollowers("user-2", 0, 10, "viewer");

            // Then
            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals(0, result.getPage());
            assertEquals(10, result.getSize());
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getTotalPages());
            assertTrue(result.isLast());

            FollowResponse firstFollower = result.getContent().get(0);
            assertEquals("user-1", firstFollower.userId());
            assertEquals("currentuser", firstFollower.username());
            assertFalse(firstFollower.followedByMe());

            verify(followRepository).findByIdFollowingId("user-2", pageable);
        }

        @Test
        void getFollowers_withNullCurrentUsername_returnsFollowedByMeFalse() {
            // Given: anonymous user (currentUsername is null)
            Pageable pageable = PageRequest.of(0, 10);
            Page<Follow> followPage = new PageImpl<>(List.of(follow), pageable, 1);

            FollowResponse followResponse = new FollowResponse(
                    "user-1", "currentuser", "Current User",
                    "https://example.com/current.jpg", false, now
            );

            when(followRepository.findByIdFollowingId("user-2", pageable)).thenReturn(followPage);
            // currentUsername=null → getCurrentUserId returns null → followedByMe=false
            when(followMapper.toFollowResponse(currentUser, false, now)).thenReturn(followResponse);

            // When
            PageResponse<FollowResponse> result = followService.getFollowers("user-2", 0, 10, null);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertFalse(result.getContent().get(0).followedByMe());

            // Should not call findFollowingIdsIn when currentUserId is null
            verify(followRepository, never()).findFollowingIdsIn(any(), any());
        }

        @Test
        void getFollowers_emptyPage_returnsEmptyPageResponse() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Follow> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(followRepository.findByIdFollowingId("user-2", pageable)).thenReturn(emptyPage);

            // When
            PageResponse<FollowResponse> result = followService.getFollowers("user-2", 0, 10, null);

            // Then
            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getTotalElements());
            assertTrue(result.isLast());
        }

        @Test
        void getFollowers_viewerFollowsFollower_followedByMeIsTrue() {
            // Given: the viewer already follows one of the followers
            Pageable pageable = PageRequest.of(0, 10);
            Page<Follow> followPage = new PageImpl<>(List.of(follow), pageable, 1);

            User viewerUser = User.builder().username("viewer").build();
            viewerUser.setId("user-viewer");

            FollowResponse followResponse = new FollowResponse(
                    "user-1", "currentuser", "Current User",
                    "https://example.com/current.jpg", true, now
            );

            when(followRepository.findByIdFollowingId("user-2", pageable)).thenReturn(followPage);
            when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewerUser));
            when(followRepository.findFollowingIdsIn("user-viewer", List.of("user-1"))).thenReturn(List.of("user-1"));
            when(followMapper.toFollowResponse(currentUser, true, now)).thenReturn(followResponse);

            // When
            PageResponse<FollowResponse> result = followService.getFollowers("user-2", 0, 10, "viewer");

            // Then
            assertTrue(result.getContent().get(0).followedByMe());
        }
    }

    // ==================== getFollowing ====================

    @Nested
    class GetFollowing {

        @Test
        void getFollowing_success_returnsPageResponseOfFollowing() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Follow> followPage = new PageImpl<>(List.of(follow), pageable, 1);

            User viewerUser = User.builder().username("viewer").build();
            viewerUser.setId("user-viewer");

            FollowResponse followResponse = new FollowResponse(
                    "user-2", "targetuser", "Target User",
                    "https://example.com/target.jpg", false, now
            );

            when(followRepository.findByIdFollowerId("user-1", pageable)).thenReturn(followPage);
            when(userRepository.findByUsername("viewer")).thenReturn(Optional.of(viewerUser));
            when(followRepository.findFollowingIdsIn("user-viewer", List.of("user-2"))).thenReturn(List.of());
            when(followMapper.toFollowResponse(targetUser, false, now)).thenReturn(followResponse);

            // When
            PageResponse<FollowResponse> result = followService.getFollowing("user-1", 0, 10, "viewer");

            // Then
            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals(0, result.getPage());
            assertEquals(10, result.getSize());
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getTotalPages());
            assertTrue(result.isLast());

            FollowResponse firstFollowing = result.getContent().get(0);
            assertEquals("user-2", firstFollowing.userId());
            assertEquals("targetuser", firstFollowing.username());

            verify(followRepository).findByIdFollowerId("user-1", pageable);
        }

        @Test
        void getFollowing_withNullCurrentUsername_returnsFollowedByMeFalse() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Follow> followPage = new PageImpl<>(List.of(follow), pageable, 1);

            FollowResponse followResponse = new FollowResponse(
                    "user-2", "targetuser", "Target User",
                    "https://example.com/target.jpg", false, now
            );

            when(followRepository.findByIdFollowerId("user-1", pageable)).thenReturn(followPage);
            when(followMapper.toFollowResponse(targetUser, false, now)).thenReturn(followResponse);

            // When
            PageResponse<FollowResponse> result = followService.getFollowing("user-1", 0, 10, null);

            // Then
            assertFalse(result.getContent().get(0).followedByMe());
            verify(followRepository, never()).findFollowingIdsIn(any(), any());
        }

        @Test
        void getFollowing_emptyPage_returnsEmptyPageResponse() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<Follow> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(followRepository.findByIdFollowerId("user-1", pageable)).thenReturn(emptyPage);

            // When
            PageResponse<FollowResponse> result = followService.getFollowing("user-1", 0, 10, null);

            // Then
            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getTotalElements());
        }
    }

    // ==================== isFollowing ====================

    @Nested
    class IsFollowing {

        @Test
        void isFollowing_true_returnsTrue() {
            // Given
            when(userRepository.findByUsername("currentuser")).thenReturn(Optional.of(currentUser));
            when(followRepository.existsByIdFollowerIdAndIdFollowingId("user-1", "user-2")).thenReturn(true);

            // When
            boolean result = followService.isFollowing("user-2", "currentuser");

            // Then
            assertTrue(result);
            verify(followRepository).existsByIdFollowerIdAndIdFollowingId("user-1", "user-2");
        }

        @Test
        void isFollowing_false_returnsFalse() {
            // Given
            when(userRepository.findByUsername("currentuser")).thenReturn(Optional.of(currentUser));
            when(followRepository.existsByIdFollowerIdAndIdFollowingId("user-1", "user-2")).thenReturn(false);

            // When
            boolean result = followService.isFollowing("user-2", "currentuser");

            // Then
            assertFalse(result);
        }

        @Test
        void isFollowing_currentUserNotFound_throwsAppException() {
            // Given
            when(userRepository.findByUsername("unknownuser")).thenReturn(Optional.empty());

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> followService.isFollowing("user-2", "unknownuser"));

            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
        }
    }

    // ==================== getFollowerCount ====================

    @Nested
    class GetFollowerCount {

        @Test
        void getFollowerCount_success_returnsCount() {
            // Given
            when(followRepository.countByIdFollowingId("user-2")).thenReturn(42L);

            // When
            long result = followService.getFollowerCount("user-2");

            // Then
            assertEquals(42L, result);
            verify(followRepository).countByIdFollowingId("user-2");
        }

        @Test
        void getFollowerCount_noFollowers_returnsZero() {
            // Given
            when(followRepository.countByIdFollowingId("user-2")).thenReturn(0L);

            // When
            long result = followService.getFollowerCount("user-2");

            // Then
            assertEquals(0L, result);
        }
    }

    // ==================== getFollowingCount ====================

    @Nested
    class GetFollowingCount {

        @Test
        void getFollowingCount_success_returnsCount() {
            // Given
            when(followRepository.countByIdFollowerId("user-1")).thenReturn(15L);

            // When
            long result = followService.getFollowingCount("user-1");

            // Then
            assertEquals(15L, result);
            verify(followRepository).countByIdFollowerId("user-1");
        }

        @Test
        void getFollowingCount_noFollowing_returnsZero() {
            // Given
            when(followRepository.countByIdFollowerId("user-1")).thenReturn(0L);

            // When
            long result = followService.getFollowingCount("user-1");

            // Then
            assertEquals(0L, result);
        }
    }
}
