package com.vibecart.api.modules.social.entity;

import com.vibecart.api.common.entity.BaseEntity;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.iam.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;
import java.util.Set;

@Entity
@Table(name = "posts")
@SQLDelete(sql = "UPDATE posts SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Post extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private User creator;

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    private String content;

    @Column(name = "media_urls")
    private String mediaUrls;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "post_products",
        joinColumns = @JoinColumn(name = "post_id"),
        inverseJoinColumns = @JoinColumn(name = "product_id")
    )
    private Set<Product> taggedProducts;
}
