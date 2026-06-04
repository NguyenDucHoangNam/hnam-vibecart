package com.vibecart.api.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.*;

@Entity
@Table(name = "media_metadata")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MediaMetadata extends BaseEntity {

    @Column(name = "s3_key", unique = true, nullable = false)
    private String s3Key;

    @Column(name = "uploaded_by", length = 50, nullable = false)
    private String uploadedBy;

    @Column(name = "file_size", nullable = false)
    private long fileSize;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";
}
