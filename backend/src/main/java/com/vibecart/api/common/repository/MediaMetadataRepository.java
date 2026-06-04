package com.vibecart.api.common.repository;

import com.vibecart.api.common.entity.MediaMetadata;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface MediaMetadataRepository extends JpaRepository<MediaMetadata, String> {
    Optional<MediaMetadata> findByS3Key(String s3Key);
    List<MediaMetadata> findByStatusAndCreatedAtBefore(String status, ZonedDateTime dateTime);
}
