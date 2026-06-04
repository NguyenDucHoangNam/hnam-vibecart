package com.vibecart.api.modules.social.repository;

import com.vibecart.api.modules.social.entity.Post;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PostRepository extends JpaRepository<Post, String> {

    /** Tất cả bài viết, mới nhất trước */
    Page<Post> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Bài viết theo creator, mới nhất trước */
    Page<Post> findByCreatorIdOrderByCreatedAtDesc(String creatorId, Pageable pageable);

    /** News Feed: bài viết từ các creator mà user đang follow, hoặc bài viết của chính user.
     *  Dùng Slice thay vì Page để triệt tiêu COUNT(*) chí mạng khi bảng posts lớn. */
    @Query("SELECT p FROM Post p WHERE p.creator.id = :userId OR p.creator.id IN " +
           "(SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId) " +
           "ORDER BY p.createdAt DESC")
    Slice<Post> findFeedByUserId(@Param("userId") String userId, Pageable pageable);
}
