package com.vibecart.api.modules.ecommerce.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.ecommerce.dto.request.CategoryRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CategoryResponse;
import com.vibecart.api.modules.ecommerce.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller quản lý danh mục sản phẩm.
 */
@RestController
@RequestMapping("/api/v1/categories")
@RequiredArgsConstructor
public class CategoryController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CategoryController.class);

    private final CategoryService categoryService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> create(@Valid @RequestBody CategoryRequest request) {
        log.info("Creating category: {}", request.name());
        CategoryResponse category = categoryService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CategoryResponse>builder().code(1000).message("Tạo danh mục thành công").result(category).build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<CategoryResponse>>> getTree() {
        List<CategoryResponse> tree = categoryService.getTree();
        return ResponseEntity.ok(
                ApiResponse.<List<CategoryResponse>>builder().code(1000).message("Lấy cây danh mục thành công").result(tree).build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<CategoryResponse>> getById(@PathVariable String id) {
        CategoryResponse category = categoryService.getById(id);
        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder().code(1000).message("Lấy thông tin danh mục thành công").result(category).build()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<CategoryResponse>> update(@PathVariable String id, @Valid @RequestBody CategoryRequest request) {
        CategoryResponse category = categoryService.update(id, request);
        return ResponseEntity.ok(
                ApiResponse.<CategoryResponse>builder().code(1000).message("Cập nhật danh mục thành công").result(category).build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        categoryService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Xóa danh mục thành công").build()
        );
    }
}
