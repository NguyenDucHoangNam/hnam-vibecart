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
    boolean existsByIdFollowerIdAndIdFollowingId(String followerId, String followingId);
    Page<Follow> findByIdFollowingId(String followingId, Pageable pageable);
    Page<Follow> findByIdFollowerId(String followerId, Pageable pageable);
    long countByIdFollowingId(String followingId);
    long countByIdFollowerId(String followerId);
    void deleteByIdFollowerIdAndIdFollowingId(String followerId, String followingId);
    @Query("SELECT f.id.followingId FROM Follow f WHERE f.id.followerId = :followerId AND f.id.followingId IN :followingIds")
    List<String> findFollowingIdsIn(@Param("followerId") String followerId, @Param("followingIds") List<String> followingIds);
    @Query("SELECT f.id.followerId FROM Follow f WHERE f.id.followingId = :followingId")
    List<String> findAllFollowerIdsByFollowingId(@Param("followingId") String followingId);
}
