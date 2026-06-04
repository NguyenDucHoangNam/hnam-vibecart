package com.vibecart.api.modules.shortener.entity;

import com.vibecart.api.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "commissions")
@SQLDelete(sql = "UPDATE commissions SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Commission extends BaseEntity {

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "creator_id", nullable = false, length = 36)
    private String creatorId;

    @Column(name = "subtotal_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal subtotalAmount;

    @Column(name = "commission_rate", precision = 4, scale = 2, nullable = false)
    private BigDecimal commissionRate;

    @Column(name = "commission_amount", precision = 12, scale = 2, nullable = false)
    private BigDecimal commissionAmount;

    @Column(name = "status", nullable = false, length = 30)
    @Builder.Default
    private String status = "PENDING"; // PENDING, APPROVED, REJECTED
}
