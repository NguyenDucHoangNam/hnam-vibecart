package com.vibecart.api.modules.notification.repository;

import com.vibecart.api.modules.notification.entity.PushSubscription;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PushSubscriptionRepository extends MongoRepository<PushSubscription, String> {
    List<PushSubscription> findByUserId(String userId);
    Optional<PushSubscription> findByUserIdAndEndpoint(String userId, String endpoint);
    void deleteByUserIdAndEndpoint(String userId, String endpoint);
}
