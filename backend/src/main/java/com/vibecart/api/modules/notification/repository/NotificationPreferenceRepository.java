package com.vibecart.api.modules.notification.repository;

import com.vibecart.api.modules.notification.entity.NotificationPreference;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationPreferenceRepository extends MongoRepository<NotificationPreference, String> {
    Optional<NotificationPreference> findByUserId(String userId);
}
