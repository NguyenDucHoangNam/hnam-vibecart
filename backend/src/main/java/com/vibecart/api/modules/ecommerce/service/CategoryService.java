package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.modules.ecommerce.dto.request.CategoryRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CategoryResponse;
import java.util.List;

/**
 * Service quản lý danh mục sản phẩm dạng cây phân cấp.
 */
public interface CategoryService {
    CategoryResponse create(CategoryRequest request);
    List<CategoryResponse> getTree();
    CategoryResponse getById(String id);
    CategoryResponse update(String id, CategoryRequest request);
    void delete(String id);
}
