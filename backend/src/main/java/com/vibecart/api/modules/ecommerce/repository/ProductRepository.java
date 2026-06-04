package com.vibecart.api.modules.ecommerce.repository;

import com.vibecart.api.modules.ecommerce.entity.Product;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {

    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.category " +
            "LEFT JOIN FETCH p.images " +
            "WHERE p.id = :id")
    Optional<Product> findByIdWithImages(@Param("id") String id);

    @Query("SELECT DISTINCT p FROM Product p " +
            "LEFT JOIN FETCH p.variants v " +
            "LEFT JOIN FETCH v.inventory " +
            "WHERE p.id = :id")
    Optional<Product> findByIdWithVariants(@Param("id") String id);

    default Optional<Product> findByIdWithDetails(String id) {
        Optional<Product> productOpt = findByIdWithImages(id);
        if (productOpt.isPresent()) {
            findByIdWithVariants(id);
        }
        return productOpt;
    }

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.creatorId = :creatorId")
    Page<Product> findByCreatorId(@Param("creatorId") String creatorId, Pageable pageable);

    @Query("SELECT p FROM Product p LEFT JOIN FETCH p.category WHERE p.category.id = :categoryId")
    Page<Product> findByCategoryId(@Param("categoryId") String categoryId, Pageable pageable);

    Page<Product> findByStatus(String status, Pageable pageable);

    long countByCategoryId(String categoryId);

    @Query("SELECT p FROM Product p " +
           "LEFT JOIN p.category c " +
           "WHERE (:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:categoryId IS NULL OR :categoryId = '' OR c.id = :categoryId OR c.parent.id = :categoryId) " +
           "AND (:minPrice IS NULL OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE AND (CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) >= :minPrice)) " +
           "AND (:maxPrice IS NULL OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE AND (CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) <= :maxPrice)) " +
           "AND p.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE")
    Page<Product> searchProducts(
            @Param("query") String query,
            @Param("categoryId") String categoryId,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            Pageable pageable);

    @Query("SELECT p FROM Product p " +
           "LEFT JOIN p.category c " +
           "WHERE (:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:categoryId IS NULL OR :categoryId = '' OR c.id = :categoryId OR c.parent.id = :categoryId) " +
           "AND (:minPrice IS NULL OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE AND (CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) >= :minPrice)) " +
           "AND (:maxPrice IS NULL OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE AND (CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) <= :maxPrice)) " +
           "AND p.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE " +
           "ORDER BY (SELECT MIN(CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE) ASC")
    Page<Product> searchProductsOrderByPriceAsc(
            @Param("query") String query,
            @Param("categoryId") String categoryId,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            Pageable pageable);

    @Query("SELECT p FROM Product p " +
           "LEFT JOIN p.category c " +
           "WHERE (:query IS NULL OR :query = '' OR LOWER(p.name) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.description) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (:categoryId IS NULL OR :categoryId = '' OR c.id = :categoryId OR c.parent.id = :categoryId) " +
           "AND (:minPrice IS NULL OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE AND (CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) >= :minPrice)) " +
           "AND (:maxPrice IS NULL OR EXISTS (SELECT 1 FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE AND (CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) <= :maxPrice)) " +
           "AND p.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE " +
           "ORDER BY (SELECT MAX(CASE WHEN (v.discountPrice > 0 AND v.discountPrice < v.price) THEN v.discountPrice ELSE v.price END) FROM ProductVariant v WHERE v.product = p AND v.status = com.vibecart.api.modules.ecommerce.enums.ProductStatus.ACTIVE) DESC")
    Page<Product> searchProductsOrderByPriceDesc(
            @Param("query") String query,
            @Param("categoryId") String categoryId,
            @Param("minPrice") java.math.BigDecimal minPrice,
            @Param("maxPrice") java.math.BigDecimal maxPrice,
            Pageable pageable);
}
