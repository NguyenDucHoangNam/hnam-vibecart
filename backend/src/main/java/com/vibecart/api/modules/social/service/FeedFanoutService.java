package com.vibecart.api.modules.social.service;
public interface FeedFanoutService {
    void fanoutNewPost(String creatorId, String postId);
    void removeDeletedPost(String creatorId, String postId);
    void onFollow(String followerId, String followingId);
    void onUnfollow(String followerId, String followingId);
    void warmUpTimeline(String userId);
}
