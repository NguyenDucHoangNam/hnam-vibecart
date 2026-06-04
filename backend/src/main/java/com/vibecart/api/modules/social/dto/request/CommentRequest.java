package com.vibecart.api.modules.social.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CommentRequest(
    @NotBlank(message = "INVALID_INPUT")
    @Size(max = 500, message = "INVALID_INPUT")
    String content,

    String parentId
) {}
