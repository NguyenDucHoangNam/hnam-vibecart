package com.vibecart.api.modules.shortener.repository;

import com.vibecart.api.modules.shortener.entity.Commission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CommissionRepository extends JpaRepository<Commission, String> {
    List<Commission> findByCreatorId(String creatorId);
    List<Commission> findByCreatorIdAndStatus(String creatorId, String status);
    Optional<Commission> findByOrderId(String orderId);
}
