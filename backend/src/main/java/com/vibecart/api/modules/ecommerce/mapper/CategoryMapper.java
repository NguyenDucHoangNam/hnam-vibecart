package com.vibecart.api.modules.ecommerce.mapper;

import com.vibecart.api.modules.ecommerce.dto.request.CategoryRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CategoryResponse;
import com.vibecart.api.modules.ecommerce.entity.Category;
import org.mapstruct.*;

import java.util.List;

@Mapper(componentModel = "spring", builder = @Builder(disableBuilder = true))
public interface CategoryMapper {

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "slug", ignore = true)
    @Mapping(target = "parent", ignore = true)
    @Mapping(target = "children", ignore = true)
    @Mapping(target = "sortOrder", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "createdBy", ignore = true)
    @Mapping(target = "updatedBy", ignore = true)
    @Mapping(target = "deleted", ignore = true)
    @Mapping(target = "deletedAt", ignore = true)
    Category toEntity(CategoryRequest request);

    @Mapping(target = "parentId", source = "parent.id")
    @Mapping(target = "children", source = "children")
    CategoryResponse toResponse(Category category);

    List<CategoryResponse> toResponseList(List<Category> categories);
}
