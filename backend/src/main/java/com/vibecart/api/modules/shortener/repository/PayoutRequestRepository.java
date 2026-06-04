package com.vibecart.api.modules.shortener.repository;

import com.vibecart.api.modules.shortener.entity.PayoutRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PayoutRequestRepository extends JpaRepository<PayoutRequest, String> {
    List<PayoutRequest> findByCreatorIdOrderByCreatedAtDesc(String creatorId);
    List<PayoutRequest> findByStatusOrderByCreatedAtDesc(String status);
}
