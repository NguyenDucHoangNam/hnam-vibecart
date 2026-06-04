package com.vibecart.api.modules.ecommerce.entity;

import com.vibecart.api.common.entity.BaseEntity;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;

@Entity
@Table(name = "product_variants")
@SQLDelete(sql = "UPDATE product_variants SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductVariant extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sku_code", nullable = false, unique = true, length = 50)
    private String skuCode;

    @Column(name = "variant_name", nullable = false)
    private String variantName;

    @Column(name = "price", precision = 12, scale = 2, nullable = false)
    private BigDecimal price;

    @Column(name = "discount_price", precision = 12, scale = 2)
    @Builder.Default
    private BigDecimal discountPrice = BigDecimal.ZERO;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20)
    @Builder.Default
    private ProductStatus status = ProductStatus.ACTIVE;

    @OneToOne(mappedBy = "variant", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    private Inventory inventory;
}
