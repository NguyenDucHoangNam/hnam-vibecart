package com.vibecart.api.modules.social.repository;

import com.vibecart.api.modules.social.entity.Follow;
import com.vibecart.api.modules.social.entity.FollowId;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FollowRepository extends JpaRepository<Follow, FollowId> {

    /** Kiểm tra đã follow chưa */
    boolean existsByIdFollowerIdAndIdFollowingId(String followerId, String followingId);

    /** Danh sách followers của 1 user (ai follow user này) */
    Page<Follow> findByIdFollowingId(String followingId, Pageable pageable);

    /** Danh sách following của 1 user (user này follow ai) */
    Page<Follow> findByIdFollowerId(String followerId, Pageable pageable);

    /** Đếm followers */
    long countByIdFollowingId(String followingId);

    /** Đếm following */
    long countByIdFollowerId(String followerId);

    /** Xóa follow (unfollow) */
    void deleteByIdFollowerIdAndIdFollowingId(String followerId, String followingId);

    /** [BATCH] Lấy danh sách followingId mà follower đang follow, trong phạm vi danh sách cho trước */
    @Query("SELECT f.id.followingId FROM Follow f WHERE f.id.followerId = :followerId AND f.id.followingId IN :followingIds")
    List<String> findFollowingIdsIn(@Param("followerId") String followerId, @Param("followingIds") List<String> followingIds);

    /** [FAN-OUT] Lấy toàn bộ follower IDs của một user (không phân trang) - phục vụ fan-out timeline */
    @Query("SELECT f.id.followerId FROM Follow f WHERE f.id.followingId = :followingId")
    List<String> findAllFollowerIdsByFollowingId(@Param("followingId") String followingId);
}
