package com.vibecart.api.modules.social.service;

import com.vibecart.api.common.dto.PageResponse;
import com.vibecart.api.modules.social.dto.response.FollowResponse;

public interface FollowService {

    /** Toggle follow/unfollow */
    boolean toggleFollow(String targetUserId, String currentUsername);

    /** Danh sách followers (ai follow user này) */
    PageResponse<FollowResponse> getFollowers(String userId, int page, int size, String currentUsername);

    /** Danh sách following (user này follow ai) */
    PageResponse<FollowResponse> getFollowing(String userId, int page, int size, String currentUsername);

    /** Kiểm tra đã follow chưa */
    boolean isFollowing(String targetUserId, String currentUsername);

    /** Đếm followers */
    long getFollowerCount(String userId);

    /** Đếm following */
    long getFollowingCount(String userId);
}
