package com.vibecart.api.modules.social.repository;

import com.vibecart.api.modules.social.entity.PostLike;
import com.vibecart.api.modules.social.entity.PostLikeId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostLikeRepository extends JpaRepository<PostLike, PostLikeId> {
    boolean existsByIdPostIdAndIdUserId(String postId, String userId);
    long countByIdPostId(String postId);
    void deleteByIdPostIdAndIdUserId(String postId, String userId);
    @Query("SELECT pl.id.postId, COUNT(pl) FROM PostLike pl WHERE pl.id.postId IN :postIds GROUP BY pl.id.postId")
    List<Object[]> countByPostIds(@Param("postIds") List<String> postIds);
    @Query("SELECT pl.id.postId FROM PostLike pl WHERE pl.id.userId = :userId AND pl.id.postId IN :postIds")
    List<String> findLikedPostIds(@Param("userId") String userId, @Param("postIds") List<String> postIds);
}
