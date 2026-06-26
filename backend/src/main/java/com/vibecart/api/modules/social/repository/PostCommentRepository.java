package com.vibecart.api.modules.social.repository;

import com.vibecart.api.modules.social.entity.PostComment;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostCommentRepository extends JpaRepository<PostComment, String> {
    Page<PostComment> findByPostIdAndParentIsNullOrderByCreatedAtDesc(String postId, Pageable pageable);
    List<PostComment> findByParentIdOrderByCreatedAtAsc(String parentId);
    long countByPostId(String postId);
    @Query("SELECT c.post.id, COUNT(c) FROM PostComment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<String> postIds);
    List<PostComment> findByPostIdAndParentIsNotNullOrderByCreatedAtAsc(String postId);
    @Query("SELECT c FROM PostComment c WHERE c.post.id = :postId AND c.parent.id IN :parentIds ORDER BY c.createdAt ASC")
    List<PostComment> findRepliesByPostIdAndParentIds(@Param("postId") String postId, @Param("parentIds") List<String> parentIds);
}
