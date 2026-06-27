package com.vibecart.api.modules.notification.entity;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import java.util.HashMap;
import java.util.Map;

@Document(collection = "notification_preferences")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPreference {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("user_id")
    private String userId;

    @Builder.Default
    @Field("preferences")
    private Map<String, ChannelPreference> preferences = new HashMap<>();

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ChannelPreference {
        @Builder.Default
        private boolean inApp = true;
        @Builder.Default
        private boolean sound = true;
        @Builder.Default
        private boolean push = true;
    }
}
