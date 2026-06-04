package com.vibecart.api.modules.search.repository;

import com.vibecart.api.modules.search.entity.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(String status);
    List<OutboxEvent> findByAggregateTypeAndStatusOrderByCreatedAtAsc(String aggregateType, String status);
}
