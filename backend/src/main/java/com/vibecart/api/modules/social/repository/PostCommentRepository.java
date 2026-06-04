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

    /** Root comments (parent IS NULL), mới nhất trước, phân trang */
    Page<PostComment> findByPostIdAndParentIsNullOrderByCreatedAtDesc(String postId, Pageable pageable);

    /** Replies của 1 comment cha, cũ nhất trước */
    List<PostComment> findByParentIdOrderByCreatedAtAsc(String parentId);

    /** Đếm tổng comment của 1 bài viết */
    long countByPostId(String postId);

    /** [BATCH] Đếm comment theo danh sách post IDs — trả về [postId, count] */
    @Query("SELECT c.post.id, COUNT(c) FROM PostComment c WHERE c.post.id IN :postIds GROUP BY c.post.id")
    List<Object[]> countByPostIds(@Param("postIds") List<String> postIds);

    /** [BATCH] Lấy tất cả replies (parent IS NOT NULL) của 1 post, cũ nhất trước */
    List<PostComment> findByPostIdAndParentIsNotNullOrderByCreatedAtAsc(String postId);

    /** [PERF-2] Lấy replies theo batch parent IDs — chỉ load replies của root comments trong trang hiện tại */
    @Query("SELECT c FROM PostComment c WHERE c.post.id = :postId AND c.parent.id IN :parentIds ORDER BY c.createdAt ASC")
    List<PostComment> findRepliesByPostIdAndParentIds(@Param("postId") String postId, @Param("parentIds") List<String> parentIds);
}
