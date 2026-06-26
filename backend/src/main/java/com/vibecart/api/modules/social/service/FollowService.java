package com.vibecart.api.modules.social.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.response.FollowResponse;
public interface FollowService {
    boolean toggleFollow(String targetUserId, String currentUsername);
    PageResponse<FollowResponse> getFollowers(String userId, int page, int size, String currentUsername);
    PageResponse<FollowResponse> getFollowing(String userId, int page, int size, String currentUsername);
    boolean isFollowing(String targetUserId, String currentUsername);
    long getFollowerCount(String userId);
    long getFollowingCount(String userId);
}
