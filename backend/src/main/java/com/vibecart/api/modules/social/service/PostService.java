package com.vibecart.api.modules.social.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.request.PostRequest;
import com.vibecart.api.modules.social.dto.response.PostResponse;
public interface PostService {
    PostResponse createPost(PostRequest request, String username);
    PageResponse<PostResponse> getPosts(int page, int size, String creatorId, String currentUsername);
    PostResponse getPost(String postId, String currentUsername);
    PostResponse updatePost(String postId, PostRequest request, String username);
    void deletePost(String postId, String username, boolean isAdmin);
    PageResponse<PostResponse> getFeed(int page, int size, String username);
    boolean toggleLike(String postId, String username);
    boolean isLiked(String postId, String username);
    long getLikeCount(String postId);
}
