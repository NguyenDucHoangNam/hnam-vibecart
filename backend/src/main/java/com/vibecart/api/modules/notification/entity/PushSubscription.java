package com.vibecart.api.modules.notification.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.Instant;

@Document(collection = "push_subscriptions")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushSubscription {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private String userId;

    @Field("endpoint")
    private String endpoint;

    @Field("p256dh_key")
    private String p256dhKey;

    @Field("auth_key")
    private String authKey;

    @Builder.Default
    @Field("created_at")
    private Instant createdAt = Instant.now();
}
