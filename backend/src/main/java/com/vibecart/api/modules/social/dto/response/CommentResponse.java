package com.vibecart.api.modules.social.dto.response;

import java.time.ZonedDateTime;
import java.util.List;

public record CommentResponse(
    String id,
    String postId,
    String userId,
    String username,
    String userAvatarUrl,
    String content,
    String parentId,
    List<CommentResponse> replies,
    ZonedDateTime createdAt,
    ZonedDateTime updatedAt
) {}
