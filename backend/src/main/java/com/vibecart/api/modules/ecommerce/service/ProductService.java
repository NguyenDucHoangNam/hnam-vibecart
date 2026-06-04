package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.modules.ecommerce.dto.request.InventoryAdjustRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductRequest;
import com.vibecart.api.modules.ecommerce.dto.response.ProductResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import com.vibecart.api.modules.ecommerce.dto.response.InventoryHistoryResponse;
import java.util.List;

public interface ProductService {
    ProductResponse create(ProductRequest request);
    ProductResponse getById(String id);
    Page<ProductResponse> getAll(Pageable pageable);
    Page<ProductResponse> getByCreator(String creatorId, Pageable pageable);
    Page<ProductResponse> getByCategory(String categoryId, Pageable pageable);
    ProductResponse update(String id, ProductRequest request);
    void delete(String id);
    void adjustInventory(String variantId, InventoryAdjustRequest request);
    List<InventoryHistoryResponse> getInventoryHistory(String variantId);
}
