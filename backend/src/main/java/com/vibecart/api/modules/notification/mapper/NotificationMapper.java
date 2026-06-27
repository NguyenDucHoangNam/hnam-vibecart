package com.vibecart.api.modules.notification.mapper;

import com.vibecart.api.modules.notification.dto.event.InAppNotificationEvent;
import com.vibecart.api.modules.notification.dto.response.NotificationResponse;
import com.vibecart.api.modules.notification.entity.Notification;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NotificationMapper {

    @Mapping(target = "actor.id", source = "actorId")
    @Mapping(target = "actor.username", source = "actorUsername")
    @Mapping(target = "actor.fullName", source = "actorFullName")
    @Mapping(target = "actor.avatarUrl", source = "actorAvatarUrl")
    NotificationResponse toResponse(Notification notification);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "isRead", constant = "false")
    @Mapping(target = "createdAt", expression = "java(java.time.Instant.now())")
    Notification fromEvent(InAppNotificationEvent event);
}
