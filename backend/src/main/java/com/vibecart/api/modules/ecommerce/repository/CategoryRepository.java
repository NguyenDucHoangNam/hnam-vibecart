package com.vibecart.api.modules.ecommerce.repository;

import com.vibecart.api.modules.ecommerce.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CategoryRepository extends JpaRepository<Category, String> {
    List<Category> findByParentIsNullAndDeletedFalseOrderBySortOrderAsc();

    List<Category> findByParentIdAndDeletedFalseOrderBySortOrderAsc(String parentId);

    Optional<Category> findBySlug(String slug);

    boolean existsByParentId(String parentId);
}
