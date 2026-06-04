package com.vibecart.api.modules.ecommerce.repository;

import com.vibecart.api.modules.ecommerce.entity.ProductImage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ProductImageRepository extends JpaRepository<ProductImage, String> {
    List<ProductImage> findByProductIdOrderBySortOrderAsc(String productId);

    void deleteByProductId(String productId);
}
