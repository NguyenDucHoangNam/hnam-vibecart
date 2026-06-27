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

    @Query("SELECT p FROM Post p WHERE p.visibility = com.vibecart.api.modules.social.enums.PostVisibility.PUBLIC " +
           "OR p.creator.id = :currentUserId ORDER BY p.createdAt DESC")
    Page<Post> findAllPublicOrOwnPosts(@Param("currentUserId") String currentUserId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.visibility = com.vibecart.api.modules.social.enums.PostVisibility.PUBLIC " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findAllPublicPosts(Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.creator.id = :creatorId AND (" +
           "p.creator.id = :viewerId OR " +
           "p.visibility = com.vibecart.api.modules.social.enums.PostVisibility.PUBLIC OR " +
           "(p.visibility = com.vibecart.api.modules.social.enums.PostVisibility.FOLLOWERS AND :isFollower = true)" +
           ") ORDER BY p.createdAt DESC")
    Page<Post> findPostsForViewer(
            @Param("creatorId") String creatorId,
            @Param("viewerId") String viewerId,
            @Param("isFollower") boolean isFollower,
            Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.creator.id = :userId OR (p.creator.id IN " +
           "(SELECT f.following.id FROM Follow f WHERE f.follower.id = :userId) " +
           "AND p.visibility <> com.vibecart.api.modules.social.enums.PostVisibility.PRIVATE) " +
           "ORDER BY p.createdAt DESC")
    Slice<Post> findFeedByUserId(@Param("userId") String userId, Pageable pageable);

    @Query("SELECT p FROM Post p WHERE p.creator.id = :creatorId AND " +
           "p.visibility <> com.vibecart.api.modules.social.enums.PostVisibility.PRIVATE " +
           "ORDER BY p.createdAt DESC")
    Page<Post> findNonPrivatePostsByCreatorId(@Param("creatorId") String creatorId, Pageable pageable);
}
