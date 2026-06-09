package com.vibecart.api.modules.social.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.request.PostRequest;
import com.vibecart.api.modules.social.dto.response.PostResponse;

/**
 * Service quản lý bài viết trong mạng xã hội.
 */
public interface PostService {

    /** Tạo bài viết mới (ROLE_CREATOR only) */
    PostResponse createPost(PostRequest request, String username);

    /** Lấy danh sách bài viết (public, có phân trang) */
    PageResponse<PostResponse> getPosts(int page, int size, String creatorId, String currentUsername);

    /** Lấy chi tiết 1 bài viết (public) */
    PostResponse getPost(String postId, String currentUsername);

    /** Cập nhật bài viết (owner only) */
    PostResponse updatePost(String postId, PostRequest request, String username);

    /** Xóa bài viết (owner hoặc ADMIN) */
    void deletePost(String postId, String username, boolean isAdmin);

    /** News Feed: bài viết từ các creator mà user follow */
    PageResponse<PostResponse> getFeed(int page, int size, String username);

    /** Toggle like/unlike */
    boolean toggleLike(String postId, String username);

    /** Kiểm tra user đã like chưa */
    boolean isLiked(String postId, String username);

    /** Đếm lượt like */
    long getLikeCount(String postId);
}
