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
import com.vibecart.api.modules.social.service.FeedFanoutService;
import com.vibecart.api.modules.social.service.PostService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.concurrent.TimeUnit;
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
    private final FeedFanoutService feedFanoutService;
    private final StringRedisTemplate redisTemplate;

    private static final int MAX_MEDIA_URLS = 10;
    private static final int MAX_TAGGED_PRODUCTS = 5;
    private static final String TIMELINE_KEY_PREFIX = "feed:timeline:";
    private static final long TIMELINE_TTL_DAYS = 30;

    @Override
    @Transactional
    public PostResponse createPost(PostRequest request, String username) {
        User creator = findUserByUsername(username);

        if (request.mediaUrls() != null && request.mediaUrls().size() > MAX_MEDIA_URLS) {
            throw new AppException(ErrorCode.MAX_MEDIA_EXCEEDED);
        }

        if (request.taggedProductIds() != null && request.taggedProductIds().size() > MAX_TAGGED_PRODUCTS) {
            throw new AppException(ErrorCode.MAX_PRODUCTS_EXCEEDED);
        }

        validateMediaUrls(request.mediaUrls(), username);

        Post post = Post.builder()
                .creator(creator)
                .content(request.content())
                .mediaUrls(joinMediaUrls(request.mediaUrls()))
                .build();

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

        feedFanoutService.fanoutNewPost(creator.getId(), savedPost.getId());

        return toSinglePostResponse(savedPost, username);
    }

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

    @Override
    @Transactional(readOnly = true)
    public PostResponse getPost(String postId, String currentUsername) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));
        return toSinglePostResponse(post, currentUsername);
    }

    @Override
    @Transactional
    public PostResponse updatePost(String postId, PostRequest request, String username) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));

        if (!post.getCreator().getUsername().equals(username)) {
            throw new AppException(ErrorCode.POST_ACCESS_DENIED);
        }

        if (request.mediaUrls() != null && request.mediaUrls().size() > MAX_MEDIA_URLS) {
            throw new AppException(ErrorCode.MAX_MEDIA_EXCEEDED);
        }

        if (request.taggedProductIds() != null && request.taggedProductIds().size() > MAX_TAGGED_PRODUCTS) {
            throw new AppException(ErrorCode.MAX_PRODUCTS_EXCEEDED);
        }

        post.setContent(request.content());

        validateMediaUrls(request.mediaUrls(), username);
        post.setMediaUrls(joinMediaUrls(request.mediaUrls()));

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

    @Override
    @Transactional
    public void deletePost(String postId, String username, boolean isAdmin) {
        Post post = postRepository.findById(postId)
                .orElseThrow(() -> new AppException(ErrorCode.POST_NOT_FOUND));


        if (!isAdmin && !post.getCreator().getUsername().equals(username)) {
            throw new AppException(ErrorCode.POST_ACCESS_DENIED);
        }

        String creatorId = post.getCreator().getId();
        postRepository.delete(post);
        log.info("Post deleted by {}: {}", username, postId);

        feedFanoutService.removeDeletedPost(creatorId, postId);
    }


    @Override
    @Transactional(readOnly = true)
    public PageResponse<PostResponse> getFeed(int page, int size, String username) {
        User user = findUserByUsername(username);
        String timelineKey = TIMELINE_KEY_PREFIX + user.getId();

        long start = (long) page * size;
        long end = start + size - 1;
        List<String> postIds = redisTemplate.opsForList().range(timelineKey, start, end);

        if (postIds != null && !postIds.isEmpty()) {
            redisTemplate.expire(timelineKey, TIMELINE_TTL_DAYS, TimeUnit.DAYS);

            List<Post> posts = postRepository.findAllById(postIds);

            Map<String, Post> postMap = posts.stream()
                    .collect(Collectors.toMap(Post::getId, p -> p));
            List<Post> orderedPosts = postIds.stream()
                    .map(postMap::get)
                    .filter(Objects::nonNull)
                    .toList();

            List<PostResponse> content = toBatchPostResponses(orderedPosts, username);

            Long totalSize = redisTemplate.opsForList().size(timelineKey);
            boolean isLast = totalSize == null || end >= totalSize - 1;

            return PageResponse.<PostResponse>builder()
                    .content(content)
                    .page(page)
                    .size(size)
                    .totalElements(totalSize != null ? totalSize : -1)
                    .totalPages(totalSize != null ? (int) Math.ceil((double) totalSize / size) : -1)
                    .last(isLast)
                    .build();
        }

        log.info("Timeline cache miss for user {}, falling back to DB query", user.getId());
        Pageable pageable = PageRequest.of(page, size);
        Slice<Post> feedSlice = postRepository.findFeedByUserId(user.getId(), pageable);

        List<PostResponse> content = toBatchPostResponses(feedSlice.getContent(), username);

        feedFanoutService.warmUpTimeline(user.getId());

        return PageResponse.<PostResponse>builder()
                .content(content)
                .page(feedSlice.getNumber())
                .size(feedSlice.getSize())
                .totalElements(-1)
                .totalPages(-1)
                .last(!feedSlice.hasNext())
                .build();
    }


    @Override
    @Transactional
    public boolean toggleLike(String postId, String username) {
        if (!postRepository.existsById(postId)) {
            throw new AppException(ErrorCode.POST_NOT_FOUND);
        }

        User user = findUserByUsername(username);
        PostLikeId likeId = PostLikeId.builder()
                .postId(postId)
                .userId(user.getId())
                .build();

        if (postLikeRepository.existsById(likeId)) {
            postLikeRepository.deleteById(likeId);
            log.info("User {} unliked post {}", username, postId);
            return false;
        } else {
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


    @Override
    @Transactional(readOnly = true)
    public boolean isLiked(String postId, String username) {
        User user = findUserByUsername(username);
        return postLikeRepository.existsByIdPostIdAndIdUserId(postId, user.getId());
    }


    @Override
    @Transactional(readOnly = true)
    public long getLikeCount(String postId) {
        return postLikeRepository.countByIdPostId(postId);
    }



    private User findUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_EXISTED));
    }
    private List<PostResponse> toBatchPostResponses(List<Post> posts, String currentUsername) {
        if (posts.isEmpty()) return List.of();

        List<String> postIds = posts.stream().map(Post::getId).toList();


        Map<String, Long> likeCountMap = postLikeRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));


        Map<String, Long> commentCountMap = postCommentRepository.countByPostIds(postIds).stream()
                .collect(Collectors.toMap(
                        row -> (String) row[0],
                        row -> (Long) row[1]
                ));


        Set<String> likedPostIds = Set.of();
        if (currentUsername != null) {
            User currentUser = userRepository.findByUsername(currentUsername).orElse(null);
            if (currentUser != null) {
                likedPostIds = new HashSet<>(postLikeRepository.findLikedPostIds(currentUser.getId(), postIds));
            }
        }


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
    private String joinMediaUrls(List<String> urls) {
        if (urls == null || urls.isEmpty()) return null;
        return String.join(",", urls);
    }
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
    private String extractS3Key(String url) {
        if (url == null || url.isBlank()) return "";

        // If it's not a URL (no scheme), treat it as a raw key
        int schemeEnd = url.indexOf("://");
        if (schemeEnd == -1) return url;

        // Extract the path portion after the host
        int pathStart = url.indexOf('/', schemeEnd + 3);
        if (pathStart == -1) return url;

        String path = url.substring(pathStart + 1);

        // For S3 path-style URLs (endpoint/bucket/key), the first segment is the bucket name.
        // Key format is always: folder/year/month/uuid.ext (at least 4 segments).
        // Bucket name is a single segment, so if path has > 4 segments, strip the first one.
        String[] segments = path.split("/");
        if (segments.length > 4) {
            return path.substring(path.indexOf('/') + 1);
        }

        return path;
    }
}
