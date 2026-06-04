package com.vibecart.api.modules.social.mapper;

import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.social.dto.response.FollowResponse;
import org.mapstruct.*;

import java.time.ZonedDateTime;

@Mapper(componentModel = "spring")
public interface FollowMapper {

    @Mapping(target = "userId", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "fullName", source = "user.fullName")
    @Mapping(target = "avatarUrl", source = "user.avatarUrl")
    @Mapping(target = "followedByMe", source = "followedByMe")
    @Mapping(target = "followedAt", source = "followedAt")
    FollowResponse toFollowResponse(User user, boolean followedByMe, ZonedDateTime followedAt);
}
