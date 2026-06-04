package com.vibecart.api.modules.iam.entity;

import com.vibecart.api.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

@Entity
@Table(name = "roles")
@SQLDelete(sql = "UPDATE roles SET deleted = true, deleted_at = CURRENT_TIMESTAMP WHERE id = ?")
@SQLRestriction("deleted = false")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Getter
@Setter
public class Role extends BaseEntity {

    @Column(name = "name", length = 50, unique = true, nullable = false)
    private String name;

    @Column(name = "description")
    private String description;
}
