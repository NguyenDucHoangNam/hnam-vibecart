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
}

