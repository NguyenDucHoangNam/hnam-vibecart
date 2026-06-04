package com.vibecart.api.modules.shortener.repository;

import com.vibecart.api.modules.shortener.entity.ClickEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;

@Repository
public interface ClickEventRepository extends JpaRepository<ClickEvent, String> {
    long countByShortLinkCreatorId(String creatorId);
    long countByShortLinkCreatorIdAndClickTimeAfter(String creatorId, ZonedDateTime time);
}
