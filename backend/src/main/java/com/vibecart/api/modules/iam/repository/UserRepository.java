package com.vibecart.api.modules.iam.repository;

import com.vibecart.api.modules.iam.entity.User;
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


    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE username = :username", nativeQuery = true)
    boolean existsByUsernameAnywhere(@Param("username") String username);


    @Query(value = "SELECT COUNT(*) > 0 FROM users WHERE email = :email", nativeQuery = true)
    boolean existsByEmailAnywhere(@Param("email") String email);

    @Modifying(clearAutomatically = true)
    @Query(value = "DELETE FROM users WHERE id = :userId", nativeQuery = true)
    void hardDeleteUserByUserId(@Param("userId") String userId);

    @Modifying
    @Query(value = "DELETE FROM users WHERE username LIKE :pattern", nativeQuery = true)
    void hardDeleteByUsernameLike(@Param("pattern") String pattern);
}
