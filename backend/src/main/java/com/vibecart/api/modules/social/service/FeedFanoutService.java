package com.vibecart.api.modules.social.service;

/**
 * Service quản lý Fan-out on Write cho News Feed.
 * <p>
 * Đẩy postId vào Redis Timeline (List) của follower khi có sự kiện
 * tạo bài, xóa bài, follow, unfollow. Tất cả phương thức chạy bất đồng bộ
 * để không ảnh hưởng thời gian phản hồi API chính.
 */
public interface FeedFanoutService {

    /**
     * Fan-out bài viết mới vào timeline của tất cả follower + chính creator.
     *
     * @param creatorId ID của người đăng bài
     * @param postId    ID bài viết vừa tạo
     */
    void fanoutNewPost(String creatorId, String postId);

    /**
     * Xóa bài viết đã bị xóa khỏi timeline của tất cả follower + creator.
     *
     * @param creatorId ID của người sở hữu bài viết
     * @param postId    ID bài viết vừa bị xóa
     */
    void removeDeletedPost(String creatorId, String postId);

    /**
     * Backfill: khi user A follow user B, lấy N bài gần nhất của B
     * đẩy vào timeline của A rồi re-sort theo thời gian.
     *
     * @param followerId  ID người vừa follow (A)
     * @param followingId ID người được follow (B)
     */
    void onFollow(String followerId, String followingId);

    /**
     * Cleanup: khi user A unfollow user B, xóa toàn bộ bài của B
     * khỏi timeline của A (chạy async).
     *
     * @param followerId  ID người vừa unfollow (A)
     * @param followingId ID người bị unfollow (B)
     */
    void onUnfollow(String followerId, String followingId);

    /**
     * Khởi tạo (warm-up) timeline cho user chưa có cache trong Redis.
     * Lấy bài viết từ DB (feed query cũ) rồi nạp vào Redis List.
     *
     * @param userId ID người dùng cần warm-up
     */
    void warmUpTimeline(String userId);
}
