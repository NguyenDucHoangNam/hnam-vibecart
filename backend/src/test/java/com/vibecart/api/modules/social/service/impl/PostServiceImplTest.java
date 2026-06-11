package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.entity.MediaMetadata;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.repository.MediaMetadataRepository;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.social.dto.request.PostRequest;
import com.vibecart.api.modules.social.dto.response.PostResponse;
import com.vibecart.api.modules.social.entity.Post;
import com.vibecart.api.modules.social.entity.PostLike;
import com.vibecart.api.modules.social.entity.PostLikeId;
import com.vibecart.api.modules.social.mapper.PostMapper;
import com.vibecart.api.modules.social.repository.PostCommentRepository;
import com.vibecart.api.modules.social.repository.PostLikeRepository;
import com.vibecart.api.modules.social.repository.PostRepository;
import com.vibecart.api.modules.social.service.FeedFanoutService;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
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
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.ZonedDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PostServiceImplTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private PostLikeRepository postLikeRepository;

    @Mock
    private PostCommentRepository postCommentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PostMapper postMapper;

    @Mock
    private ProductRepository productRepository;

    @Mock
    private MediaMetadataRepository mediaMetadataRepository;

    @Mock
    private FeedFanoutService feedFanoutService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ListOperations<String, String> listOperations;

    @InjectMocks
    private PostServiceImpl postService;

    // ==================== TEST DATA ====================
    private User testUser;
    private User otherUser;
    private Post testPost;
    private PostResponse testPostResponse;
    private final String TEST_USER_ID = "user-001";
    private final String OTHER_USER_ID = "user-002";
    private final String TEST_USERNAME = "testuser";
    private final String OTHER_USERNAME = "otheruser";
    private final String TEST_POST_ID = "post-001";
    private final ZonedDateTime NOW = ZonedDateTime.now();

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .username(TEST_USERNAME)
                .email("test@email.com")
                .fullName("Test User")
                .avatarUrl("https://cdn.vibecart.com/avatar1.jpg")
                .build();
        testUser.setId(TEST_USER_ID);

        otherUser = User.builder()
                .username(OTHER_USERNAME)
                .email("other@email.com")
                .fullName("Other User")
                .avatarUrl("https://cdn.vibecart.com/avatar2.jpg")
                .build();
        otherUser.setId(OTHER_USER_ID);

        testPost = Post.builder()
                .creator(testUser)
                .content("Test post content")
                .mediaUrls("url1.jpg,url2.jpg")
                .taggedProducts(new HashSet<>())
                .build();
        testPost.setId(TEST_POST_ID);
        testPost.setCreatedAt(NOW);
        testPost.setUpdatedAt(NOW);

        testPostResponse = new PostResponse(
                TEST_POST_ID,
                TEST_USER_ID,
                TEST_USERNAME,
                "Test User",
                "https://cdn.vibecart.com/avatar1.jpg",
                "Test post content",
                List.of("url1.jpg", "url2.jpg"),
                List.of(),
                5L,
                3L,
                false,
                NOW,
                NOW
        );
    }

    /**
     * Helper: stub the common toPostResponse chain (likeCount, commentCount, likedByMe lookup).
     */
    private void stubToPostResponse(Post post, String currentUsername, PostResponse response) {
        lenient().when(postLikeRepository.countByIdPostId(post.getId())).thenReturn(5L);
        lenient().when(postCommentRepository.countByPostId(post.getId())).thenReturn(3L);

        if (currentUsername != null) {
            lenient().when(userRepository.findByUsername(currentUsername)).thenReturn(Optional.of(testUser));
            lenient().when(postLikeRepository.existsByIdPostIdAndIdUserId(post.getId(), testUser.getId())).thenReturn(false);
        }

        lenient().when(postMapper.toPostResponse(eq(post), anyLong(), anyLong(), anyBoolean())).thenReturn(response);
    }

    /**
     * Helper: stub media validation cho bất kỳ S3 key nào đều trả về media VERIFIED thuộc TEST_USERNAME.
     */
    private void stubMediaValidation() {
        MediaMetadata verifiedMedia = MediaMetadata.builder()
                .s3Key("any")
                .uploadedBy(TEST_USERNAME)
                .fileSize(1024)
                .status("VERIFIED")
                .build();
        lenient().when(mediaMetadataRepository.findByS3Key(anyString())).thenReturn(Optional.of(verifiedMedia));
    }

    // ==================== createPost ====================
    @Nested
    @DisplayName("createPost")
    class CreatePostTests {

        @Test
        void createPost_success_returnsPostResponse() {
            PostRequest request = new PostRequest("Test post content", List.of("url1.jpg", "url2.jpg"), null);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            stubMediaValidation();
            when(postRepository.save(any(Post.class))).thenReturn(testPost);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PostResponse result = postService.createPost(request, TEST_USERNAME);

            assertNotNull(result);
            assertEquals(TEST_POST_ID, result.id());
            assertEquals("Test post content", result.content());
            verify(postRepository, times(1)).save(any(Post.class));
            verify(feedFanoutService).fanoutNewPost(TEST_USER_ID, TEST_POST_ID);
        }

        @Test
        void createPost_withTaggedProducts_success() {
            Set<String> productIds = Set.of("prod-1", "prod-2");
            PostRequest request = new PostRequest("Tagged post", List.of("url1.jpg"), productIds);

            Product prod1 = Product.builder().status(ProductStatus.ACTIVE).creatorId(TEST_USER_ID).build();
            prod1.setDeleted(false);
            Product prod2 = Product.builder().status(ProductStatus.ACTIVE).creatorId(TEST_USER_ID).build();
            prod2.setDeleted(false);

            when(productRepository.findById("prod-1")).thenReturn(Optional.of(prod1));
            when(productRepository.findById("prod-2")).thenReturn(Optional.of(prod2));

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            stubMediaValidation();
            when(postRepository.save(any(Post.class))).thenReturn(testPost);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PostResponse result = postService.createPost(request, TEST_USERNAME);

            assertNotNull(result);
            verify(postRepository).save(argThat(post ->
                    post.getTaggedProducts() != null && post.getTaggedProducts().size() == 2
            ));
        }

        @Test
        void createPost_mediaLimitExceeded_throwsAppException() {
            List<String> tooManyUrls = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                tooManyUrls.add("url" + i + ".jpg");
            }
            PostRequest request = new PostRequest("Content", tooManyUrls, null);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

            AppException exception = assertThrows(AppException.class,
                    () -> postService.createPost(request, TEST_USERNAME));
            assertEquals(ErrorCode.MAX_MEDIA_EXCEEDED, exception.getErrorCode());
            verify(postRepository, never()).save(any(Post.class));
        }

        @Test
        void createPost_productLimitExceeded_throwsAppException() {
            Set<String> tooManyProducts = new HashSet<>();
            for (int i = 0; i < 6; i++) {
                tooManyProducts.add("prod-" + i);
            }
            PostRequest request = new PostRequest("Content", null, tooManyProducts);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));

            AppException exception = assertThrows(AppException.class,
                    () -> postService.createPost(request, TEST_USERNAME));
            assertEquals(ErrorCode.MAX_PRODUCTS_EXCEEDED, exception.getErrorCode());
            verify(postRepository, never()).save(any(Post.class));
        }

        @Test
        void createPost_userNotFound_throwsAppException() {
            PostRequest request = new PostRequest("Content", null, null);

            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> postService.createPost(request, "nonexistent"));
            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
            verify(postRepository, never()).save(any(Post.class));
        }

        @Test
        void createPost_nullMediaUrls_success() {
            PostRequest request = new PostRequest("Content only", null, null);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(postRepository.save(any(Post.class))).thenReturn(testPost);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PostResponse result = postService.createPost(request, TEST_USERNAME);

            assertNotNull(result);
            verify(postRepository).save(argThat(post -> post.getMediaUrls() == null));
        }

        @Test
        void createPost_emptyMediaUrls_success() {
            PostRequest request = new PostRequest("Content only", List.of(), null);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(postRepository.save(any(Post.class))).thenReturn(testPost);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PostResponse result = postService.createPost(request, TEST_USERNAME);

            assertNotNull(result);
            verify(postRepository).save(argThat(post -> post.getMediaUrls() == null));
        }

        @Test
        void createPost_exactlyMaxMedia_success() {
            List<String> maxUrls = new ArrayList<>();
            for (int i = 0; i < 10; i++) {
                maxUrls.add("url" + i + ".jpg");
            }
            PostRequest request = new PostRequest("Content", maxUrls, null);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            stubMediaValidation();
            when(postRepository.save(any(Post.class))).thenReturn(testPost);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PostResponse result = postService.createPost(request, TEST_USERNAME);

            assertNotNull(result);
            verify(postRepository, times(1)).save(any(Post.class));
        }

        @Test
        void createPost_exactlyMaxProducts_success() {
            Set<String> maxProducts = new HashSet<>();
            for (int i = 0; i < 5; i++) {
                maxProducts.add("prod-" + i);
                Product prod = Product.builder().status(ProductStatus.ACTIVE).creatorId(TEST_USER_ID).build();
                prod.setDeleted(false);
                when(productRepository.findById("prod-" + i)).thenReturn(Optional.of(prod));
            }
            PostRequest request = new PostRequest("Content", null, maxProducts);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(postRepository.save(any(Post.class))).thenReturn(testPost);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PostResponse result = postService.createPost(request, TEST_USERNAME);

            assertNotNull(result);
            verify(postRepository, times(1)).save(any(Post.class));
        }
    }

    // ==================== getPosts ====================
    @Nested
    @DisplayName("getPosts")
    class GetPostsTests {

        @Test
        void getPosts_withoutFilter_returnsAllPosts() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Post> postPage = new PageImpl<>(List.of(testPost), pageable, 1);

            when(postRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(postPage);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PageResponse<PostResponse> result = postService.getPosts(0, 10, null, TEST_USERNAME);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertEquals(0, result.getPage());
            assertEquals(10, result.getSize());
            assertEquals(1, result.getTotalElements());
            assertTrue(result.isLast());
            verify(postRepository).findAllByOrderByCreatedAtDesc(pageable);
            verify(postRepository, never()).findByCreatorIdOrderByCreatedAtDesc(anyString(), any());
        }

        @Test
        void getPosts_withBlankCreatorId_returnsAllPosts() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Post> postPage = new PageImpl<>(List.of(testPost), pageable, 1);

            when(postRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(postPage);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PageResponse<PostResponse> result = postService.getPosts(0, 10, "   ", TEST_USERNAME);

            assertNotNull(result);
            verify(postRepository).findAllByOrderByCreatedAtDesc(pageable);
            verify(postRepository, never()).findByCreatorIdOrderByCreatedAtDesc(anyString(), any());
        }

        @Test
        void getPosts_withCreatorIdFilter_returnsFilteredPosts() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Post> postPage = new PageImpl<>(List.of(testPost), pageable, 1);

            when(postRepository.findByCreatorIdOrderByCreatedAtDesc(TEST_USER_ID, pageable)).thenReturn(postPage);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PageResponse<PostResponse> result = postService.getPosts(0, 10, TEST_USER_ID, TEST_USERNAME);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            verify(postRepository).findByCreatorIdOrderByCreatedAtDesc(TEST_USER_ID, pageable);
            verify(postRepository, never()).findAllByOrderByCreatedAtDesc(any());
        }

        @Test
        void getPosts_emptyResult_returnsEmptyPage() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Post> emptyPage = new PageImpl<>(List.of(), pageable, 0);

            when(postRepository.findAllByOrderByCreatedAtDesc(pageable)).thenReturn(emptyPage);

            PageResponse<PostResponse> result = postService.getPosts(0, 10, null, TEST_USERNAME);

            assertNotNull(result);
            assertTrue(result.getContent().isEmpty());
            assertEquals(0, result.getTotalElements());
        }
    }

    // ==================== getPost ====================
    @Nested
    @DisplayName("getPost")
    class GetPostTests {

        @Test
        void getPost_success_returnsPostResponse() {
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PostResponse result = postService.getPost(TEST_POST_ID, TEST_USERNAME);

            assertNotNull(result);
            assertEquals(TEST_POST_ID, result.id());
            assertEquals("Test post content", result.content());
            verify(postRepository).findById(TEST_POST_ID);
        }

        @Test
        void getPost_notFound_throwsAppException() {
            when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> postService.getPost("nonexistent", TEST_USERNAME));
            assertEquals(ErrorCode.POST_NOT_FOUND, exception.getErrorCode());
        }

        @Test
        void getPost_withNullUsername_success() {
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));
            lenient().when(postLikeRepository.countByIdPostId(TEST_POST_ID)).thenReturn(5L);
            lenient().when(postCommentRepository.countByPostId(TEST_POST_ID)).thenReturn(3L);

            PostResponse anonymousResponse = new PostResponse(
                    TEST_POST_ID, TEST_USER_ID, TEST_USERNAME,
                    "Test User",
                    "https://cdn.vibecart.com/avatar1.jpg",
                    "Test post content", List.of("url1.jpg", "url2.jpg"),
                    List.of(), 5L, 3L, false, NOW, NOW
            );
            when(postMapper.toPostResponse(eq(testPost), anyLong(), anyLong(), eq(false))).thenReturn(anonymousResponse);

            PostResponse result = postService.getPost(TEST_POST_ID, null);

            assertNotNull(result);
            assertFalse(result.likedByMe());
        }
    }

    // ==================== updatePost ====================
    @Nested
    @DisplayName("updatePost")
    class UpdatePostTests {

        @Test
        void updatePost_success_returnsUpdatedResponse() {
            PostRequest request = new PostRequest("Updated content", List.of("new-url.jpg"), Set.of("prod-1"));
            Post updatedPost = Post.builder()
                    .creator(testUser)
                    .content("Updated content")
                    .mediaUrls("new-url.jpg")
                    .build();
            updatedPost.setId(TEST_POST_ID);
            updatedPost.setCreatedAt(NOW);
            updatedPost.setUpdatedAt(NOW);

            PostResponse updatedResponse = new PostResponse(
                    TEST_POST_ID, TEST_USER_ID, TEST_USERNAME,
                    "Test User",
                    "https://cdn.vibecart.com/avatar1.jpg",
                    "Updated content", List.of("new-url.jpg"),
                    List.of("prod-1"), 5L, 3L, false, NOW, NOW
            );

            Product prod1 = Product.builder().status(ProductStatus.ACTIVE).creatorId(TEST_USER_ID).build();
            prod1.setDeleted(false);
            when(productRepository.findById("prod-1")).thenReturn(Optional.of(prod1));

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));
            stubMediaValidation();
            when(postRepository.save(any(Post.class))).thenReturn(updatedPost);
            stubToPostResponse(updatedPost, TEST_USERNAME, updatedResponse);

            PostResponse result = postService.updatePost(TEST_POST_ID, request, TEST_USERNAME);

            assertNotNull(result);
            assertEquals("Updated content", result.content());
            verify(postRepository).save(any(Post.class));
        }

        @Test
        void updatePost_postNotFound_throwsAppException() {
            PostRequest request = new PostRequest("Updated content", null, null);

            when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> postService.updatePost("nonexistent", request, TEST_USERNAME));
            assertEquals(ErrorCode.POST_NOT_FOUND, exception.getErrorCode());
            verify(postRepository, never()).save(any(Post.class));
        }

        @Test
        void updatePost_accessDenied_notOwner_throwsAppException() {
            PostRequest request = new PostRequest("Updated content", null, null);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));

            AppException exception = assertThrows(AppException.class,
                    () -> postService.updatePost(TEST_POST_ID, request, OTHER_USERNAME));
            assertEquals(ErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
            verify(postRepository, never()).save(any(Post.class));
        }

        @Test
        void updatePost_mediaLimitExceeded_throwsAppException() {
            List<String> tooManyUrls = new ArrayList<>();
            for (int i = 0; i < 11; i++) {
                tooManyUrls.add("url" + i + ".jpg");
            }
            PostRequest request = new PostRequest("Content", tooManyUrls, null);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));

            AppException exception = assertThrows(AppException.class,
                    () -> postService.updatePost(TEST_POST_ID, request, TEST_USERNAME));
            assertEquals(ErrorCode.MAX_MEDIA_EXCEEDED, exception.getErrorCode());
            verify(postRepository, never()).save(any(Post.class));
        }

        @Test
        void updatePost_productLimitExceeded_throwsAppException() {
            Set<String> tooManyProducts = new HashSet<>();
            for (int i = 0; i < 6; i++) {
                tooManyProducts.add("prod-" + i);
            }
            PostRequest request = new PostRequest("Content", null, tooManyProducts);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));

            AppException exception = assertThrows(AppException.class,
                    () -> postService.updatePost(TEST_POST_ID, request, TEST_USERNAME));
            assertEquals(ErrorCode.MAX_PRODUCTS_EXCEEDED, exception.getErrorCode());
            verify(postRepository, never()).save(any(Post.class));
        }

        @Test
        void updatePost_nullTaggedProducts_clearsProducts() {
            PostRequest request = new PostRequest("Updated content", null, null);

            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));
            when(postRepository.save(any(Post.class))).thenReturn(testPost);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            postService.updatePost(TEST_POST_ID, request, TEST_USERNAME);

            verify(postRepository).save(argThat(post ->
                    post.getTaggedProducts() != null && post.getTaggedProducts().isEmpty()
            ));
        }
    }

    // ==================== deletePost ====================
    @Nested
    @DisplayName("deletePost")
    class DeletePostTests {

        @Test
        void deletePost_byOwner_success() {
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));

            assertDoesNotThrow(() -> postService.deletePost(TEST_POST_ID, TEST_USERNAME, false));

            verify(postRepository).delete(testPost);
            verify(feedFanoutService).removeDeletedPost(TEST_USER_ID, TEST_POST_ID);
        }

        @Test
        void deletePost_byAdmin_success() {
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));

            assertDoesNotThrow(() -> postService.deletePost(TEST_POST_ID, OTHER_USERNAME, true));

            verify(postRepository).delete(testPost);
        }

        @Test
        void deletePost_accessDenied_notOwnerNotAdmin_throwsAppException() {
            when(postRepository.findById(TEST_POST_ID)).thenReturn(Optional.of(testPost));

            AppException exception = assertThrows(AppException.class,
                    () -> postService.deletePost(TEST_POST_ID, OTHER_USERNAME, false));
            assertEquals(ErrorCode.POST_ACCESS_DENIED, exception.getErrorCode());
            verify(postRepository, never()).delete(any(Post.class));
        }

        @Test
        void deletePost_postNotFound_throwsAppException() {
            when(postRepository.findById("nonexistent")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> postService.deletePost("nonexistent", TEST_USERNAME, false));
            assertEquals(ErrorCode.POST_NOT_FOUND, exception.getErrorCode());
            verify(postRepository, never()).delete(any(Post.class));
        }
    }

    // ==================== getFeed ====================
    @Nested
    @DisplayName("getFeed")
    class GetFeedTests {

        @Test
        void getFeed_cacheHit_returnsFeedFromRedis() {
            List<String> cachedPostIds = List.of(TEST_POST_ID);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.range("feed:timeline:" + TEST_USER_ID, 0, 9)).thenReturn(cachedPostIds);
            when(redisTemplate.expire(anyString(), anyLong(), any())).thenReturn(true);
            when(postRepository.findAllById(cachedPostIds)).thenReturn(List.of(testPost));
            when(listOperations.size("feed:timeline:" + TEST_USER_ID)).thenReturn(1L);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PageResponse<PostResponse> result = postService.getFeed(0, 10, TEST_USERNAME);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            assertTrue(result.isLast());
            // Should NOT fall back to DB feed query
            verify(postRepository, never()).findFeedByUserId(anyString(), any());
        }

        @Test
        void getFeed_cacheMiss_fallsBackToDbAndWarmsUp() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Post> feedPage = new PageImpl<>(List.of(testPost), pageable, 1);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.range("feed:timeline:" + TEST_USER_ID, 0, 9)).thenReturn(List.of());
            when(postRepository.findFeedByUserId(TEST_USER_ID, pageable)).thenReturn(feedPage);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PageResponse<PostResponse> result = postService.getFeed(0, 10, TEST_USERNAME);

            assertNotNull(result);
            assertEquals(1, result.getContent().size());
            verify(postRepository).findFeedByUserId(TEST_USER_ID, pageable);
            verify(feedFanoutService).warmUpTimeline(TEST_USER_ID);
        }

        @Test
        void getFeed_cacheNull_fallsBackToDbAndWarmsUp() {
            Pageable pageable = PageRequest.of(0, 10);
            Page<Post> feedPage = new PageImpl<>(List.of(testPost), pageable, 1);

            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(redisTemplate.opsForList()).thenReturn(listOperations);
            when(listOperations.range("feed:timeline:" + TEST_USER_ID, 0, 9)).thenReturn(null);
            when(postRepository.findFeedByUserId(TEST_USER_ID, pageable)).thenReturn(feedPage);
            stubToPostResponse(testPost, TEST_USERNAME, testPostResponse);

            PageResponse<PostResponse> result = postService.getFeed(0, 10, TEST_USERNAME);

            assertNotNull(result);
            verify(postRepository).findFeedByUserId(TEST_USER_ID, pageable);
            verify(feedFanoutService).warmUpTimeline(TEST_USER_ID);
        }

        @Test
        void getFeed_userNotFound_throwsAppException() {
            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> postService.getFeed(0, 10, "nonexistent"));
            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
        }
    }

    // ==================== toggleLike ====================
    @Nested
    @DisplayName("toggleLike")
    class ToggleLikeTests {

        @Test
        void toggleLike_newLike_returnsTrue() {
            PostLikeId likeId = PostLikeId.builder()
                    .postId(TEST_POST_ID)
                    .userId(TEST_USER_ID)
                    .build();

            when(postRepository.existsById(TEST_POST_ID)).thenReturn(true);
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(postLikeRepository.existsById(likeId)).thenReturn(false);
            when(postRepository.getReferenceById(TEST_POST_ID)).thenReturn(testPost);
            when(postLikeRepository.save(any(PostLike.class))).thenReturn(new PostLike());

            boolean result = postService.toggleLike(TEST_POST_ID, TEST_USERNAME);

            assertTrue(result);
            verify(postLikeRepository).save(any(PostLike.class));
            verify(postLikeRepository, never()).deleteById(any(PostLikeId.class));
        }

        @Test
        void toggleLike_existingLike_unlike_returnsFalse() {
            PostLikeId likeId = PostLikeId.builder()
                    .postId(TEST_POST_ID)
                    .userId(TEST_USER_ID)
                    .build();

            when(postRepository.existsById(TEST_POST_ID)).thenReturn(true);
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(postLikeRepository.existsById(likeId)).thenReturn(true);

            boolean result = postService.toggleLike(TEST_POST_ID, TEST_USERNAME);

            assertFalse(result);
            verify(postLikeRepository).deleteById(likeId);
            verify(postLikeRepository, never()).save(any(PostLike.class));
        }

        @Test
        void toggleLike_postNotFound_throwsAppException() {
            when(postRepository.existsById("nonexistent")).thenReturn(false);

            AppException exception = assertThrows(AppException.class,
                    () -> postService.toggleLike("nonexistent", TEST_USERNAME));
            assertEquals(ErrorCode.POST_NOT_FOUND, exception.getErrorCode());
            verify(postLikeRepository, never()).save(any(PostLike.class));
            verify(postLikeRepository, never()).deleteById(any(PostLikeId.class));
        }

        @Test
        void toggleLike_userNotFound_throwsAppException() {
            when(postRepository.existsById(TEST_POST_ID)).thenReturn(true);
            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> postService.toggleLike(TEST_POST_ID, "nonexistent"));
            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
        }
    }

    // ==================== isLiked ====================
    @Nested
    @DisplayName("isLiked")
    class IsLikedTests {

        @Test
        void isLiked_userHasLiked_returnsTrue() {
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(postLikeRepository.existsByIdPostIdAndIdUserId(TEST_POST_ID, TEST_USER_ID)).thenReturn(true);

            boolean result = postService.isLiked(TEST_POST_ID, TEST_USERNAME);

            assertTrue(result);
            verify(postLikeRepository).existsByIdPostIdAndIdUserId(TEST_POST_ID, TEST_USER_ID);
        }

        @Test
        void isLiked_userHasNotLiked_returnsFalse() {
            when(userRepository.findByUsername(TEST_USERNAME)).thenReturn(Optional.of(testUser));
            when(postLikeRepository.existsByIdPostIdAndIdUserId(TEST_POST_ID, TEST_USER_ID)).thenReturn(false);

            boolean result = postService.isLiked(TEST_POST_ID, TEST_USERNAME);

            assertFalse(result);
            verify(postLikeRepository).existsByIdPostIdAndIdUserId(TEST_POST_ID, TEST_USER_ID);
        }

        @Test
        void isLiked_userNotFound_throwsAppException() {
            when(userRepository.findByUsername("nonexistent")).thenReturn(Optional.empty());

            AppException exception = assertThrows(AppException.class,
                    () -> postService.isLiked(TEST_POST_ID, "nonexistent"));
            assertEquals(ErrorCode.USER_NOT_EXISTED, exception.getErrorCode());
        }
    }

    // ==================== getLikeCount ====================
    @Nested
    @DisplayName("getLikeCount")
    class GetLikeCountTests {

        @Test
        void getLikeCount_success_returnsCount() {
            when(postLikeRepository.countByIdPostId(TEST_POST_ID)).thenReturn(42L);

            long result = postService.getLikeCount(TEST_POST_ID);

            assertEquals(42L, result);
            verify(postLikeRepository).countByIdPostId(TEST_POST_ID);
        }

        @Test
        void getLikeCount_noLikes_returnsZero() {
            when(postLikeRepository.countByIdPostId(TEST_POST_ID)).thenReturn(0L);

            long result = postService.getLikeCount(TEST_POST_ID);

            assertEquals(0L, result);
        }
    }
}
