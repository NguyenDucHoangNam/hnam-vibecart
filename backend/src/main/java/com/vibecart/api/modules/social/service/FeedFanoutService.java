package com.vibecart.api.modules.social.service;

/**
 * Interface định nghĩa các dịch vụ cập nhật dòng thời gian (News Feed) theo mô
 * hình Fan-out on Write.
 * Chịu trách nhiệm đồng bộ và cập nhật danh sách bài viết trên timeline của
 * người theo dõi (followers)
 * khi có các sự thay đổi về bài viết hoặc quan hệ theo dõi giữa người dùng.
 */
public interface FeedFanoutService {

    /**
     * Fan-out bài viết mới vào timeline của tất cả follower + chính creator.
     */
    void fanoutNewPost(String creatorId, String postId);

    /**
     * Xóa bài viết đã bị xóa khỏi timeline của tất cả follower + creator.
     */
    void removeDeletedPost(String creatorId, String postId);

    /**
     * Backfill: khi user A follow user B, lấy N bài gần nhất của B
     * đẩy vào timeline của A rồi re-sort theo thời gian.
     */
    void onFollow(String followerId, String followingId);

    /**
     * Cleanup: khi user A unfollow user B, xóa toàn bộ bài của B
     * khỏi timeline của A (chạy async).
     */
    void onUnfollow(String followerId, String followingId);

    /**
     * Khởi tạo (warm-up) timeline cho user chưa có cache trong Redis.
     * Lấy bài viết từ DB (feed query cũ) rồi nạp vào Redis List.
     */
    void warmUpTimeline(String userId);
}
