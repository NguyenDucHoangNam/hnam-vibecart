package com.vibecart.api.modules.notification.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "notifications")
@CompoundIndex(name = "recipient_created_idx", def = "{'recipient_id': 1, 'created_at': -1}")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Notification {

    @Id
    private String id;

    @Indexed
    @Field("recipient_id")
    private String recipientId;

    @Field("actor_id")
    private String actorId;

    @Field("actor_username")
    private String actorUsername;

    @Field("actor_full_name")
    private String actorFullName;

    @Field("actor_avatar_url")
    private String actorAvatarUrl;

    @Field("type")
    private String type;

    @Field("reference_id")
    private String referenceId;

    @Field("content")
    private String content;

    @Builder.Default
    @Field("is_read")
    private boolean isRead = false;

    @Builder.Default
    @Indexed(expireAfter = "90d")
    @Field("created_at")
    private Instant createdAt = Instant.now();
}
