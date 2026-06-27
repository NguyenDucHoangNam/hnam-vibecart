package com.vibecart.api.modules.notification.repository;

import com.vibecart.api.modules.notification.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface NotificationRepository extends MongoRepository<Notification, String> {

    Page<Notification> findByRecipientIdOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    Page<Notification> findByRecipientIdAndIsReadFalseOrderByCreatedAtDesc(String recipientId, Pageable pageable);

    long countByRecipientIdAndIsReadFalse(String recipientId);

    void deleteByRecipientId(String recipientId);

    boolean existsByActorIdAndRecipientIdAndTypeAndCreatedAtAfter(
            String actorId, String recipientId, String type, Instant after);
}
