package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.modules.ecommerce.dto.request.CategoryRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CategoryResponse;
import java.util.List;

public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    List<CategoryResponse> getTree();
    CategoryResponse getById(String id);
    CategoryResponse update(String id, CategoryRequest request);
    void delete(String id);
}
