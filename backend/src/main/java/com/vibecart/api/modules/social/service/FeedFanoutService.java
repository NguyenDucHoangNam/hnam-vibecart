package com.vibecart.api.modules.social.service;

import com.vibecart.api.modules.social.enums.PostVisibility;

public interface FeedFanoutService {
    void fanoutNewPost(String creatorId, String postId, PostVisibility visibility);
    void removeDeletedPost(String creatorId, String postId);
    void onFollow(String followerId, String followingId);
    void onUnfollow(String followerId, String followingId);
    void warmUpTimeline(String userId);
}
