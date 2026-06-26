package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.social.dto.request.CommentRequest;
import com.vibecart.api.modules.social.dto.response.CommentResponse;
import com.vibecart.api.modules.social.entity.Post;
import com.vibecart.api.modules.social.entity.PostComment;
import com.vibecart.api.modules.social.mapper.CommentMapper;
import com.vibecart.api.modules.social.repository.PostCommentRepository;
import com.vibecart.api.modules.social.repository.PostRepository;
import com.vibecart.api.modules.social.util.ProfanityFilter;
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
public class CommentServiceImplTest {

    @Mock
    private PostCommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CommentMapper commentMapper;

    @Mock
    private ProfanityFilter profanityFilter;

    @InjectMocks
    private CommentServiceImpl commentService;

    private User testUser;
    private User postCreator;
    private Post testPost;
    private PostComment rootComment;
    private PostComment replyComment;
    private ZonedDateTime now;

    @BeforeEach
    void setUp() {
        now = ZonedDateTime.now();

        testUser = User.builder()
                .username("testuser")
                .email("test@email.com")
                .fullName("Test User")
                .avatarUrl("https://example.com/avatar.jpg")
                .build();
        testUser.setId("user-1");

        postCreator = User.builder()
                .username("postcreator")
                .email("creator@email.com")
                .fullName("Post Creator")
                .avatarUrl("https://example.com/creator.jpg")
                .build();
        postCreator.setId("user-2");

        testPost = Post.builder()
                .creator(postCreator)
                .content("Test post content")
                .build();
        testPost.setId("post-1");
        testPost.setCreatedAt(now);

        rootComment = PostComment.builder()
                .post(testPost)
                .user(testUser)
                .content("Root comment")
                .parent(null)
                .build();
        rootComment.setId("comment-1");
        rootComment.setCreatedAt(now);
        rootComment.setUpdatedAt(now);

        replyComment = PostComment.builder()
                .post(testPost)
                .user(postCreator)
                .content("Reply comment")
                .parent(rootComment)
                .build();
        replyComment.setId("comment-2");
        replyComment.setCreatedAt(now);
        replyComment.setUpdatedAt(now);
    }

    // ==================== addComment ====================

    @Nested
    class AddComment {

        @Test
        void addComment_successRootComment_returnsCommentResponse() {
            // Given
            CommentRequest request = new CommentRequest("Root comment", null);

            CommentResponse expectedResponse = new CommentResponse(
                    "comment-1", "post-1", "user-1", "testuser",
                    "https://example.com/avatar.jpg", "Root comment",
                    null, List.of(), now, now
            );

            when(postRepository.findById("post-1")).thenReturn(Optional.of(testPost));
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(commentRepository.save(any(PostComment.class))).thenReturn(rootComment);
            when(commentMapper.toCommentResponse(eq(rootComment), eq(List.of()))).thenReturn(expectedResponse);

            // When
            CommentResponse result = commentService.addComment("post-1", request, "testuser");

            // Then
            assertNotNull(result);
            assertEquals("comment-1", result.id());
            assertEquals("post-1", result.postId());
            assertEquals("testuser", result.username());
            assertEquals("Root comment", result.content());
            assertNull(result.parentId());
            assertTrue(result.replies().isEmpty());

            verify(postRepository).findById("post-1");
            verify(userRepository).findByUsername("testuser");
            verify(commentRepository).save(any(PostComment.class));
            verify(commentMapper).toCommentResponse(eq(rootComment), eq(List.of()));
        }

        @Test
        void addComment_successReply_returnsCommentResponseWithParentId() {
            // Given
            CommentRequest request = new CommentRequest("Reply comment", "comment-1");

            CommentResponse expectedResponse = new CommentResponse(
                    "comment-2", "post-1", "user-2", "postcreator",
                    "https://example.com/creator.jpg", "Reply comment",
                    "comment-1", List.of(), now, now
            );

            when(postRepository.findById("post-1")).thenReturn(Optional.of(testPost));
            when(userRepository.findByUsername("postcreator")).thenReturn(Optional.of(postCreator));
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(rootComment));
            when(commentRepository.save(any(PostComment.class))).thenReturn(replyComment);
            when(commentMapper.toCommentResponse(eq(replyComment), eq(List.of()))).thenReturn(expectedResponse);

            // When
            CommentResponse result = commentService.addComment("post-1", request, "postcreator");

            // Then
            assertNotNull(result);
            assertEquals("comment-2", result.id());
            assertEquals("comment-1", result.parentId());
            assertEquals("Reply comment", result.content());

            verify(commentRepository).findById("comment-1");
            verify(commentRepository).save(any(PostComment.class));
        }

        @Test
        void addComment_postNotFound_throwsAppException() {
            // Given
            CommentRequest request = new CommentRequest("Any comment", null);
            when(postRepository.findById("nonexistent-post")).thenReturn(Optional.empty());

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> commentService.addComment("nonexistent-post", request, "testuser"));

            assertEquals(ErrorCode.POST_NOT_FOUND, exception.getErrorCode());
            verify(postRepository).findById("nonexistent-post");
            verify(commentRepository, never()).save(any());
        }

        @Test
        void addComment_parentCommentNotFound_throwsAppException() {
            // Given
            CommentRequest request = new CommentRequest("Reply comment", "nonexistent-comment");

            when(postRepository.findById("post-1")).thenReturn(Optional.of(testPost));
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(commentRepository.findById("nonexistent-comment")).thenReturn(Optional.empty());

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> commentService.addComment("post-1", request, "testuser"));

            assertEquals(ErrorCode.COMMENT_NOT_FOUND, exception.getErrorCode());
            verify(commentRepository, never()).save(any());
        }

        @Test
        void addComment_maxDepthExceeded_throwsAppException() {
            // Given: parent already has 2 levels of parents (depth >= 2), so reply should be blocked
            PostComment greatgrandparent = PostComment.builder()
                    .post(testPost)
                    .user(testUser)
                    .content("Greatgrandparent comment")
                    .parent(null)
                    .build();
            greatgrandparent.setId("comment-greatgrandparent");

            PostComment grandparent = PostComment.builder()
                    .post(testPost)
                    .user(testUser)
                    .content("Grandparent comment")
                    .parent(greatgrandparent)
                    .build();
            grandparent.setId("comment-grandparent");

            PostComment parentWithParent = PostComment.builder()
                    .post(testPost)
                    .user(testUser)
                    .content("Parent with parent")
                    .parent(grandparent)
                    .build();
            parentWithParent.setId("comment-parent-with-parent");

            CommentRequest request = new CommentRequest("Deep reply", "comment-parent-with-parent");

            when(postRepository.findById("post-1")).thenReturn(Optional.of(testPost));
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(commentRepository.findById("comment-parent-with-parent")).thenReturn(Optional.of(parentWithParent));

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> commentService.addComment("post-1", request, "testuser"));

            assertEquals(ErrorCode.MAX_COMMENT_DEPTH, exception.getErrorCode());
            verify(commentRepository, never()).save(any());
        }

        @Test
        void addComment_parentIdBlank_treatedAsRootComment() {
            // Given: parentId is blank string → should be treated as root comment
            CommentRequest request = new CommentRequest("Root comment", "   ");

            CommentResponse expectedResponse = new CommentResponse(
                    "comment-1", "post-1", "user-1", "testuser",
                    "https://example.com/avatar.jpg", "Root comment",
                    null, List.of(), now, now
            );

            when(postRepository.findById("post-1")).thenReturn(Optional.of(testPost));
            when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(testUser));
            when(commentRepository.save(any(PostComment.class))).thenReturn(rootComment);
            when(commentMapper.toCommentResponse(eq(rootComment), eq(List.of()))).thenReturn(expectedResponse);

            // When
            CommentResponse result = commentService.addComment("post-1", request, "testuser");

            // Then
            assertNotNull(result);
            assertNull(result.parentId());
            // Should NOT attempt to find parent comment
            verify(commentRepository, never()).findById(anyString());
        }
    }

    // ==================== getComments ====================

    @Nested
    class GetComments {

        @Test
        void getComments_successWithReplies_returnsPageResponseWithNestedReplies() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<PostComment> rootPage = new PageImpl<>(List.of(rootComment), pageable, 1);

            CommentResponse replyResponse = new CommentResponse(
                    "comment-2", "post-1", "user-2", "postcreator",
                    "https://example.com/creator.jpg", "Reply comment",
                    "comment-1", List.of(), now, now
            );

            CommentResponse rootResponse = new CommentResponse(
                    "comment-1", "post-1", "user-1", "testuser",
                    "https://example.com/avatar.jpg", "Root comment",
                    null, List.of(replyResponse), now, now
            );

            when(commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtDesc("post-1", pageable))
                    .thenReturn(rootPage);
            when(commentRepository.findRepliesByPostIdAndParentIds("post-1", List.of("comment-1")))
                    .thenReturn(List.of(replyComment));
            when(commentRepository.findRepliesByPostIdAndParentIds("post-1", List.of("comment-2")))
                    .thenReturn(List.of());
            when(commentMapper.toCommentResponse(eq(replyComment), eq(List.of())))
                    .thenReturn(replyResponse);
            when(commentMapper.toCommentResponse(eq(rootComment), eq(List.of(replyResponse))))
                    .thenReturn(rootResponse);

            // When
            PageResponse<CommentResponse> result = commentService.getComments("post-1", 0, 10);

            // Then
            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals(0, result.getPage());
            assertEquals(10, result.getSize());
            assertEquals(1, result.getTotalElements());
            assertEquals(1, result.getTotalPages());
            assertTrue(result.isLast());

            CommentResponse firstComment = result.getContent().get(0);
            assertEquals("comment-1", firstComment.id());
            assertEquals(1, firstComment.replies().size());
            assertEquals("comment-2", firstComment.replies().get(0).id());
            verify(commentRepository).findByPostIdAndParentIsNullOrderByCreatedAtDesc("post-1", pageable);
            verify(commentRepository).findRepliesByPostIdAndParentIds("post-1", List.of("comment-1"));
            verify(commentRepository).findRepliesByPostIdAndParentIds("post-1", List.of("comment-2"));
        }

        @Test
        void getComments_emptyPage_returnsEmptyPageResponse() {
            // Given
            Pageable pageable = PageRequest.of(0, 10);
            Page<PostComment> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtDesc("post-1", pageable))
                    .thenReturn(emptyPage);

            // When
            PageResponse<CommentResponse> result = commentService.getComments("post-1", 0, 10);

            // Then
            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getPage());
            assertEquals(10, result.getSize());
            assertEquals(0, result.getTotalElements());
            assertEquals(0, result.getTotalPages());
            assertTrue(result.isLast());

            verify(commentRepository, never()).findByParentIdOrderByCreatedAtAsc(anyString());
        }

        @Test
        void getComments_multipleRootComments_returnsAllWithReplies() {
            // Given
            PostComment secondRoot = PostComment.builder()
                    .post(testPost)
                    .user(postCreator)
                    .content("Second root comment")
                    .parent(null)
                    .build();
            secondRoot.setId("comment-3");
            secondRoot.setCreatedAt(now);
            secondRoot.setUpdatedAt(now);

            Pageable pageable = PageRequest.of(0, 10);
            Page<PostComment> rootPage = new PageImpl<>(List.of(rootComment, secondRoot), pageable, 2);

            CommentResponse rootResponse1 = new CommentResponse(
                    "comment-1", "post-1", "user-1", "testuser",
                    "https://example.com/avatar.jpg", "Root comment",
                    null, List.of(), now, now
            );
            CommentResponse rootResponse2 = new CommentResponse(
                    "comment-3", "post-1", "user-2", "postcreator",
                    "https://example.com/creator.jpg", "Second root comment",
                    null, List.of(), now, now
            );

            when(commentRepository.findByPostIdAndParentIsNullOrderByCreatedAtDesc("post-1", pageable))
                    .thenReturn(rootPage);
            when(commentRepository.findByParentIdOrderByCreatedAtAsc("comment-1")).thenReturn(List.of());
            when(commentRepository.findByParentIdOrderByCreatedAtAsc("comment-3")).thenReturn(List.of());
            when(commentMapper.toCommentResponse(eq(rootComment), eq(List.of()))).thenReturn(rootResponse1);
            when(commentMapper.toCommentResponse(eq(secondRoot), eq(List.of()))).thenReturn(rootResponse2);

            // When
            PageResponse<CommentResponse> result = commentService.getComments("post-1", 0, 10);

            // Then
            assertNotNull(result);
            assertEquals(2, result.getContent().size());
            assertEquals(2, result.getTotalElements());
        }
    }

    // ==================== deleteComment ====================

    @Nested
    class DeleteComment {

        @Test
        void deleteComment_successByCommentOwner_deletesComment() {
            // Given
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(rootComment));

            // When
            commentService.deleteComment("post-1", "comment-1", "testuser", false);

            // Then
            verify(commentRepository).findById("comment-1");
            verify(commentRepository).delete(rootComment);
        }

        @Test
        void deleteComment_successByPostCreator_deletesComment() {
            // Given: postcreator is the post's creator, not the comment owner
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(rootComment));

            // When: postcreator deletes testuser's comment on their own post
            commentService.deleteComment("post-1", "comment-1", "postcreator", false);

            // Then
            verify(commentRepository).delete(rootComment);
        }

        @Test
        void deleteComment_successByAdmin_deletesComment() {
            // Given: admin is neither comment owner nor post creator
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(rootComment));

            // When
            commentService.deleteComment("post-1", "comment-1", "adminuser", true);

            // Then
            verify(commentRepository).delete(rootComment);
        }

        @Test
        void deleteComment_accessDenied_throwsAppException() {
            // Given: user is NOT owner, NOT post creator, NOT admin
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(rootComment));

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> commentService.deleteComment("post-1", "comment-1", "randomuser", false));

            assertEquals(ErrorCode.COMMENT_ACCESS_DENIED, exception.getErrorCode());
            verify(commentRepository, never()).delete(any());
        }

        @Test
        void deleteComment_commentNotFound_throwsAppException() {
            // Given
            when(commentRepository.findById("nonexistent")).thenReturn(Optional.empty());

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> commentService.deleteComment("post-1", "nonexistent", "testuser", false));

            assertEquals(ErrorCode.COMMENT_NOT_FOUND, exception.getErrorCode());
            verify(commentRepository, never()).delete(any());
        }

        @Test
        void deleteComment_commentNotMatchingPost_throwsAppException() {
            // Given: comment belongs to post-1, but we pass post-2
            when(commentRepository.findById("comment-1")).thenReturn(Optional.of(rootComment));

            // When & Then
            AppException exception = assertThrows(AppException.class,
                    () -> commentService.deleteComment("post-2", "comment-1", "testuser", false));

            assertEquals(ErrorCode.COMMENT_NOT_FOUND, exception.getErrorCode());
            verify(commentRepository, never()).delete(any());
        }
    }
}
