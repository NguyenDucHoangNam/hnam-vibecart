package com.vibecart.api.modules.ecommerce.repository;

import com.vibecart.api.modules.ecommerce.entity.Order;
import com.vibecart.api.modules.ecommerce.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.id = :id")
    Optional<Order> findByIdWithItems(@Param("id") String id);

    Optional<Order> findByOrderCode(String orderCode);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.orderCode = :orderCode")
    Optional<Order> findByOrderCodeWithItems(@Param("orderCode") String orderCode);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.paymentLinkId = :paymentLinkId")
    List<Order> findByPaymentLinkIdWithItems(@Param("paymentLinkId") String paymentLinkId);

    Page<Order> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.userId = :userId AND o.status = :status ORDER BY o.createdAt DESC")
    Page<Order> findByUserIdAndStatus(@Param("userId") String userId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT o FROM Order o LEFT JOIN FETCH o.items WHERE o.status = com.vibecart.api.modules.ecommerce.enums.OrderStatus.PENDING AND o.createdAt < :cutoff")
    List<Order> findExpiredPendingOrders(@Param("cutoff") ZonedDateTime cutoff);

    @Modifying
    @Query("UPDATE Order o SET o.status = :newStatus, o.updatedAt = CURRENT_TIMESTAMP " +
            "WHERE o.id = :orderId AND o.status = :currentStatus")
    int updateStatusAtomically(@Param("orderId") String orderId,
                               @Param("currentStatus") OrderStatus currentStatus,
                               @Param("newStatus") OrderStatus newStatus);

    Page<Order> findByCreatorIdOrderByCreatedAtDesc(String creatorId, Pageable pageable);

    @Query("SELECT o FROM Order o WHERE o.creatorId = :creatorId AND o.status = :status ORDER BY o.createdAt DESC")
    Page<Order> findByCreatorIdAndStatusOrderByCreatedAtDesc(@Param("creatorId") String creatorId, @Param("status") OrderStatus status, Pageable pageable);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items WHERE o.creatorId = :creatorId ORDER BY o.createdAt DESC")
    List<Order> findByCreatorIdWithItems(@Param("creatorId") String creatorId);

    @Query("SELECT DISTINCT o FROM Order o LEFT JOIN FETCH o.items " +
           "WHERE o.creatorId = :creatorId AND o.createdAt >= :startDate AND o.createdAt <= :endDate " +
           "ORDER BY o.createdAt DESC")
    List<Order> findByCreatorIdAndDateRangeWithItems(
            @Param("creatorId") String creatorId,
            @Param("startDate") ZonedDateTime startDate,
            @Param("endDate") ZonedDateTime endDate);
}
