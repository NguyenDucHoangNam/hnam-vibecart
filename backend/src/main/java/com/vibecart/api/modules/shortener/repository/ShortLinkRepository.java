package com.vibecart.api.modules.shortener.repository;

import com.vibecart.api.modules.shortener.entity.ShortLink;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ShortLinkRepository extends JpaRepository<ShortLink, String> {
    Optional<ShortLink> findByShortCode(String shortCode);
    List<ShortLink> findByCreatorIdOrderByCreatedAtDesc(String creatorId);
}
