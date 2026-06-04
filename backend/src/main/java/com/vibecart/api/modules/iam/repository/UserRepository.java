package com.vibecart.api.modules.iam.repository;

import com.vibecart.api.modules.iam.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, String> {
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    Optional<User> findByOauthProviderAndOauthId(String oauthProvider, String oauthId);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);

    // Bypasses soft-delete filters to check if a username is taken anywhere
    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE username = :username", nativeQuery = true)
    boolean existsByUsernameAnywhere(@Param("username") String username);

    // Bypasses soft-delete filters to check if an email is taken anywhere
    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    boolean existsByEmailAnywhere(@Param("email") String email);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM user_roles WHERE user_id = :userId", nativeQuery = true)
    void hardDeleteUserRolesByUserId(@Param("userId") String userId);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM users WHERE id = :userId", nativeQuery = true)
    void hardDeleteUserByUserId(@Param("userId") String userId);

    @Modifying
    @Query(value = "DELETE FROM users WHERE username LIKE :pattern", nativeQuery = true)
    void hardDeleteByUsernameLike(@Param("pattern") String pattern);

    @Query(
        "SELECT DISTINCT u FROM User u LEFT JOIN u.roles r " +
        "WHERE (:search IS NULL OR LOWER(u.username) LIKE :search " +
        "   OR LOWER(u.email) LIKE :search " +
        "   OR LOWER(u.fullName) LIKE :search) " +
        "AND (:status IS NULL OR u.status = :status) " +
        "AND (:role IS NULL OR r.name = :role)"
    )
    Page<User> searchUsers(
        @Param("search") String search,
        @Param("status") String status,
        @Param("role") String role,
        Pageable pageable
    );
}
