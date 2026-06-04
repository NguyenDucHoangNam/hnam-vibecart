package com.vibecart.api.modules.ecommerce.dto.response;

import java.util.List;

public record CategoryResponse(
        String id,
        String name,
        String slug,
        String parentId,
        List<CategoryResponse> children
) {}
