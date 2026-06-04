package com.vibecart.api.modules.ecommerce.repository;

import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductVariantRepository extends JpaRepository<ProductVariant, String> {
    List<ProductVariant> findByProductId(String productId);

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.inventory WHERE pv.id = :id")
    Optional<ProductVariant> findByIdWithInventory(@Param("id") String id);

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.inventory LEFT JOIN FETCH pv.product WHERE pv.id IN :ids")
    List<ProductVariant> findAllByIdWithInventoryAndProduct(@Param("ids") List<String> ids);

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.inventory LEFT JOIN FETCH pv.product p LEFT JOIN FETCH p.images WHERE pv.id IN :ids")
    List<ProductVariant> findAllByIdWithFullDetails(@Param("ids") List<String> ids);

    boolean existsBySkuCode(String skuCode);

    Optional<ProductVariant> findBySkuCode(String skuCode);

    @Query("SELECT pv FROM ProductVariant pv LEFT JOIN FETCH pv.inventory WHERE pv.product.id = :productId")
    List<ProductVariant> findByProductIdWithInventory(@Param("productId") String productId);
}
