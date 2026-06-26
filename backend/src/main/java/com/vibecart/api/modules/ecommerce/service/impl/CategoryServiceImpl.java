package com.vibecart.api.modules.ecommerce.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.dto.request.CategoryRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CategoryResponse;
import com.vibecart.api.modules.ecommerce.entity.Category;
import com.vibecart.api.modules.ecommerce.mapper.CategoryMapper;
import com.vibecart.api.modules.ecommerce.repository.CategoryRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import com.vibecart.api.modules.ecommerce.service.CategoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryServiceImpl implements CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final CategoryMapper categoryMapper;

    @Override
    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        log.info("Creating category: {}", request.name());

        String slug = generateSlug(request.name());

        Category category = Category.builder()
                .name(request.name())
                .slug(slug)
                .build();

        if (request.parentId() != null && !request.parentId().isBlank()) {
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            category.setParent(parent);
        }

        Category saved = categoryRepository.save(category);
        log.info("Category created with ID: {}", saved.getId());

        return categoryMapper.toResponse(saved);
    }

    @Override
    @Transactional(readOnly = true)
    public List<CategoryResponse> getTree() {
        List<Category> roots = categoryRepository.findByParentIsNullAndDeletedFalseOrderBySortOrderAsc();
        return roots.stream()
                .map(categoryMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryResponse getById(String id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
        return categoryMapper.toResponse(category);
    }

    @Override
    @Transactional
    public CategoryResponse update(String id, CategoryRequest request) {
        log.info("Updating category: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));

        category.setName(request.name());
        category.setSlug(generateSlug(request.name()));

        if (request.parentId() != null && !request.parentId().isBlank()) {
            if (request.parentId().equals(id)) {
                throw new AppException(ErrorCode.INVALID_INPUT);
            }
            Category parent = categoryRepository.findById(request.parentId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            category.setParent(parent);
        } else {
            category.setParent(null);
        }

        Category updated = categoryRepository.save(category);
        log.info("Category updated: {}", id);
        return categoryMapper.toResponse(updated);
    }

    @Override
    @Transactional
    public void delete(String id) {
        log.info("Deleting category: {}", id);

        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));


        if (categoryRepository.existsByParentId(id)) {
            throw new AppException(ErrorCode.CATEGORY_HAS_CHILDREN);
        }


        if (productRepository.countByCategoryId(id) > 0) {
            throw new AppException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }

        categoryRepository.delete(category);
        log.info("Category soft deleted: {}", id);
    }

    private String generateSlug(String name) {
        if (name == null || name.isBlank()) {
            return "";
        }
        

        String temp = java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD);
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\p{InCombiningDiacriticalMarks}+");
        String normalized = pattern.matcher(temp).replaceAll("");
        

        normalized = normalized.replace("đ", "d")
                               .replace("Đ", "d")
                               .replace("đ", "d");

        return normalized.toLowerCase()
                .trim()
                .replaceAll("[^a-z0-9\\s-]", "")
                .replaceAll("\\s+", "-")
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }
}
