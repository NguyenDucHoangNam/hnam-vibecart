package com.vibecart.api.modules.social.dto.response;

import java.time.ZonedDateTime;

public record FollowResponse(
    String userId,
    String username,
    String fullName,
    String avatarUrl,
    boolean followedByMe,
    ZonedDateTime followedAt
) {}
