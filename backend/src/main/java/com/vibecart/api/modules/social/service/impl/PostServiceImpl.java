package com.vibecart.api.modules.social.service.impl;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.common.entity.MediaMetadata;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.common.repository.MediaMetadataRepository;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
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
import com.vibecart.api.modules.social.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PostServiceImpl implements PostService {

    private final PostRepository postRepository;
    private final PostLikeRepository postLikeRepository;
    private final PostCommentRepository postCommentRepository;
    private final UserRepository userRepository;
    private final PostMapper postMapper;
    private final ProductRepository productRepository;
    private final MediaMetadataRepository mediaMetadataRepository;

    private static final int MAX_MEDIA_URLS = 10;
    private static final int MAX_TAGGED_PRODUCTS = 5;

    // ==================== TẠO BÀI VIẾT ====================
    @Override
    @Transactional
    public PostResponse createPost(PostRequest request, String username) {
        User creator = findUserByUsername(username);

        // Validate media limit
        if (request.mediaUrls() != null && request.mediaUrls().size() > MAX_MEDIA_URLS) {
            throw new AppException(ErrorCode.MAX_MEDIA_EXCEEDED);
        }

        // Validate tagged products limit
        if (request.taggedProductIds() != null && request.taggedProductIds().size() > MAX_TAGGED_PRODUCTS) {
            throw new AppException(ErrorCode.MAX_PRODUCTS_EXCEEDED);
        }

        // [SEC-4] Validate media URLs against MediaMetadata (chống chèn link ngoài & bypass S3)
        validateMediaUrls(request.mediaUrls(), username);

        // Build post entity
        Post post = Post.builder()
                .creator(creator)
                .content(request.content())
                .mediaUrls(joinMediaUrls(request.mediaUrls()))
                .build();

        // Handle tagged products via ManyToMany
        if (request.taggedProductIds() != null && !request.taggedProductIds().isEmpty()) {
            Set<Product> products = new HashSet<>();
            for (String id : request.taggedProductIds()) {
                Product p = productRepository.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
                if (p.isDeleted() || p.getStatus() != ProductStatus.ACTIVE) {
                    throw new AppException(ErrorCode.PRODUCT_INACTIVE);
                }
                if (!p.getCreatorId().equals(creator.getId())) {
                    throw new AppException(ErrorCode.PRODUCT_ACCESS_DENIED);
                }
                products.add(p);
            }
            post.setTaggedProducts(products);
        }

        Post savedPost = postRepository.save(post);
        log.info("Post created by {}: {}", username, savedPost.getId());

        return toSinglePostResponse(savedPost, username);
    }

    // ==================== LẤY DANH SÁCH BÀI VIẾT ====================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> getPosts(int page, int size, String creatorId, String currentUsername) {
        Pageable pageable = PageRequest.of(page, size);

        Page<Post> postPage;
        if (creatorId != null && !creatorId.isBlank()) {
            postPage = postRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId, pageable);
        } else {
            postPage = postRepository.findAllByOrderByCreatedAtDesc(pageable);
        }

        List<PostResponse> content = toBatchPostResponses(postPage.getContent(), currentUsername);

        return PageResponse.<PostResponse>builder()
                .content(content)
                .page(postPage.getNumber())
                .size(postPage.getSize())
                .totalElements(postPage.getTotalElements())
                .totalPages(postPage.getTotalPages())
                .last(postPage.isLast())
                .build();
    }

    // ==================== LẤY CHI TIẾT BÀI VIẾT ====================
    @Override
    @Transactional(readOnly = true)
    public PostResponse getPost(String postId, String currentUsername) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        return toSinglePostResponse(post, currentUsername);
    }

    // ==================== CẬP NHẬT BÀI VIẾT ====================
    @Override
    @Transactional
    public PostResponse updatePost(String postId, PostRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        // Verify ownership
        if (!post.getCreator().getUsername().equals(username)) {
            throw new AppException(ErrorCode.POST_ACCESS_DENIED);
        }

        // Validate media limit
        if (request.mediaUrls() != null && request.mediaUrls().size() > MAX_MEDIA_URLS) {
            throw new AppException(ErrorCode.MAX_MEDIA_EXCEEDED);
        }

        // Validate tagged products limit
        if (request.taggedProductIds() != null && request.taggedProductIds().size() > MAX_TAGGED_PRODUCTS) {
            throw new AppException(ErrorCode.MAX_PRODUCTS_EXCEEDED);
        }

        // Update fields
        post.setContent(request.content());

        // [SEC-4] Validate media URLs against MediaMetadata (chống chèn link ngoài & bypass S3)
        validateMediaUrls(request.mediaUrls(), username);
        post.setMediaUrls(joinMediaUrls(request.mediaUrls()));

        // Update tagged products
        if (request.taggedProductIds() != null) {
            Set<Product> products = new HashSet<>();
            for (String id : request.taggedProductIds()) {
                Product p = productRepository.findById(id)
                        .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
                if (p.isDeleted() || p.getStatus() != ProductStatus.ACTIVE) {
                    throw new AppException(ErrorCode.PRODUCT_INACTIVE);
                }
                if (!p.getCreatorId().equals(post.getCreator().getId())) {
                    throw new AppException(ErrorCode.PRODUCT_ACCESS_DENIED);
                }
                products.add(p);
            }
            post.setTaggedProducts(products);
        } else {
            post.setTaggedProducts(new HashSet<>());
        }

        Post updatedPost = postRepository.save(post);
        log.info("Post updated by {}: {}", username, postId);

        return toSinglePostResponse(updatedPost, username);
    }

    // ==================== XÓA BÀI VIẾT ====================
    @Override
    @Transactional
    public void deletePost(String postId, String username, boolean isAdmin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        // Check permission: owner or admin
        if (!isAdmin && !post.getCreator().getUsername().equals(username)) {
            throw new AppException(ErrorCode.POST_ACCESS_DENIED);
        }

        postRepository.delete(post); // Soft delete via @SQLDelete
        log.info("Post deleted by {}: {}", username, postId);
    }

    // ==================== NEWS FEED ====================
    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> getFeed(int page, int size, String username) {
        User user = findUserByUsername(username);
        Pageable pageable = PageRequest.of(page, size);

        // [PERF-1] Dùng Slice thay Page để triệt tiêu COUNT(*) chí mạng
        Slice<Post> feedSlice = postRepository.findFeedByUserId(user.getId(), pageable);

        List<PostResponse> content = toBatchPostResponses(feedSlice.getContent(), username);

        return PageResponse.<PostResponse>builder()
                .content(content)
                .page(feedSlice.getNumber())
                .size(feedSlice.getSize())
                .totalElements(-1) // Slice không tính tổng — Client dùng hasNext để cuộn vô hạn
                .totalPages(-1)    // Slice không tính tổng trang
                .last(!feedSlice.hasNext())
                .build();
    }

    // ==================== TOGGLE LIKE ====================
    @Override
    @Transactional
    public boolean toggleLike(String postId, String username) {
        // Verify post exists
        if (!postRepository.existsById(postId)) {
            throw new AppException(ErrorCode.POST_NOT_FOUND);
        }

        User user = findUserByUsername(username);
        PostLikeId likeId = PostLikeId.builder()
                .postId(postId)
                .userId(user.getId())
                .build();

        if (postLikeRepository.existsById(likeId)) {
            // Unlike
            postLikeRepository.deleteById(likeId);
            log.info("User {} unliked post {}", username, postId);
            return false;
        } else {
            // Like
            Post post = postRepository.getReferenceById(postId);
            PostLike like = PostLike.builder()
                    .id(likeId)
                    .post(post)
                    .user(user)
                    .build();
            postLikeRepository.save(like);
            log.info("User {} liked post {}", username, postId);
            return true;
        }
    }

    // ==================== CHECK LIKED ====================
    @Override
    @Transactional(readOnly = true)
    public boolean isLiked(String postId, String username) {
        User user = findUserByUsername(username);
        return postLikeRepository.existsByIdPostIdAndIdUserId(postId, user.getId());
    }

    // ==================== LIKE COUNT ====================
    @Override
    @Transactional(readOnly = true)
    public long getLikeCount(String postId) {
        return postLikeRepository.countByIdPostId(postId);
    }

    // ==================== HELPER METHODS ====================

    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }

    /**
     * [BATCH] Convert danh sách Post entities → PostResponses chỉ với 3 queries tổng cộng
     * thay vì 3N queries (N+1 problem).
     */
    private List<PostResponse> toBatchPostResponses(List<Post> posts, String currentUsername) {
        if (posts.isEmpty()) return List.of();

        List<String> postIds = posts.stream().map(Post::getId).toList();

        // 1. Batch query like counts
        Map<String, Long> likeCountMap = postLikeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        // 2. Batch query comment counts
        Map<String, Long> commentCountMap = postCommentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));

        // 3. Batch query likedByMe
        Set<String> likedPostIds = Set.of();
        if (currentUsername != null) {
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
            if (currentUser != null) {
                likedPostIds = new HashSet<>(postLikeRepository.findLikedPostIds(currentUser.getId(), postIds));
            }
        }

        // 4. Map all posts using pre-fetched data
        Set<String> finalLikedPostIds = likedPostIds;
        return posts.stream()
                .map(post -> postMapper.toPostResponse(
                        post,
                        likeCountMap.getOrDefault(post.getId(), 0L),
                        commentCountMap.getOrDefault(post.getId(), 0L),
                        finalLikedPostIds.contains(post.getId())
                ))
                .toList();
    }

    /** Convert single Post entity → PostResponse (dùng cho getPost, createPost, updatePost) */
    private PostResponse toSinglePostResponse(Post post, String currentUsername) {
        long likeCount = postLikeRepository.countByIdPostId(post.getId());
        long commentCount = postCommentRepository.countByPostId(post.getId());

        boolean likedByMe = false;
        if (currentUsername != null) {
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
            if (currentUser != null) {
                likedByMe = postLikeRepository.existsByIdPostIdAndIdUserId(post.getId(), currentUser.getId());
            }
        }

        return postMapper.toPostResponse(post, likeCount, commentCount, likedByMe);
    }

    /** List<String> → comma-separated String for DB storage */
    private String joinMediaUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        return String.join(",", urls);
    }

    /**
     * [SEC-4] Validate danh sách media URLs trước khi lưu bài viết.
     * Kiểm tra 3 lớp:
     * 1. S3 Key tồn tại trong bảng media_metadata
     * 2. Status = "VERIFIED" (đã qua xử lý/quét virus)
     * 3. uploadedBy == username (chống Creator A dùng file của Creator B)
     */
    private void validateMediaUrls(List<String> mediaUrls, String username) {
        if (mediaUrls == null || mediaUrls.isEmpty()) return;

        for (String url : mediaUrls) {
            String s3Key = extractS3Key(url);
            MediaMetadata media = mediaMetadataRepository.findByS3Key(s3Key)
                    .orElseThrow(() -> new AppException(ErrorCode.MEDIA_NOT_FOUND));

            if (!"VERIFIED".equals(media.getStatus())) {
                throw new AppException(ErrorCode.MEDIA_NOT_VERIFIED);
            }

            if (!media.getUploadedBy().equals(username)) {
                throw new AppException(ErrorCode.MEDIA_ACCESS_DENIED);
            }
        }
    }

    /**
     * Trích xuất S3 Key từ URL đầy đủ.
     * Ví dụ: "https://vibecart.s3.amazonaws.com/posts/abc123.jpg" → "posts/abc123.jpg"
     * Nếu URL không chứa domain prefix, coi toàn bộ chuỗi là S3 Key.
     */
    private String extractS3Key(String url) {
        if (url == null) return "";
        // Tìm phần sau domain (sau dấu "/" thứ 3)
        int schemeEnd = url.indexOf("://");
        if (schemeEnd != -1) {
            int pathStart = url.indexOf('/', schemeEnd + 3);
            if (pathStart != -1) {
                return url.substring(pathStart + 1);
            }
        }
        return url;
    }
}
