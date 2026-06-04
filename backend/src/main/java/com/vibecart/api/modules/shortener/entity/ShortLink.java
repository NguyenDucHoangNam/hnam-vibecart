package com.vibecart.api.modules.shortener.entity;

import com.vibecart.api.common.entity.BaseEntity;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.iam.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.util.List;

@Entity
@Table(name = "short_links")
@SQLDelete(sql = "UPDATE short_links SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ShortLink extends BaseEntity {

    @Column(name = "short_code", length = 10, unique = true, nullable = false)
    private String shortCode;

    @Column(name = "original_url", columnDefinition = "TEXT", nullable = false)
    private String originalUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @OneToMany(mappedBy = "shortLink", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<ClickEvent> clickEvents;
}
