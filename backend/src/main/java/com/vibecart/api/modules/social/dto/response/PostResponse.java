package com.vibecart.api.modules.social.dto.response;

import java.time.ZonedDateTime;
import java.util.List;

public record PostResponse(
    String id,
    String creatorId,
    String creatorUsername,
    String creatorFullName,
    String creatorAvatarUrl,
    String content,
    List<String> mediaUrls,
    List<String> taggedProductIds,
    long likeCount,
    long commentCount,
    boolean likedByMe,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {}
