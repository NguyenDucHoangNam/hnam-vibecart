package com.vibecart.api.modules.ecommerce.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.ecommerce.dto.request.InventoryAdjustRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductRequest;
import com.vibecart.api.modules.ecommerce.dto.response.ProductResponse;
import com.vibecart.api.modules.ecommerce.service.ProductService;
import com.vibecart.api.modules.search.dto.response.SearchResultResponse;
import com.vibecart.api.modules.search.service.SearchService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
@RestController
@RequestMapping("/api/v1/products")
@RequiredArgsConstructor
@Slf4j
public class ProductController {

    private final ProductService productService;
    private final SearchService searchService;

    @PostMapping
    @PreAuthorize("hasRole('CREATOR')")
    public ResponseEntity<ApiResponse<ProductResponse>> create(@Valid @RequestBody ProductRequest request) {
        log.info("Creating product: {}", request.name());
        ProductResponse product = productService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<ProductResponse>builder().code(1000).message("Tạo sản phẩm thành công").result(product).build()
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ProductResponse>> getById(@PathVariable String id) {
        ProductResponse product = productService.getById(id);
        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder().code(1000).message("Lấy thông tin sản phẩm thành công").result(product).build()
        );
    }

    @GetMapping
    public ResponseEntity<ApiResponse<SearchResultResponse>> getAll(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String categoryId,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sort,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "12") int size) {


        String activeQuery = (q != null && !q.isBlank()) ? q : query;
        String activeCategoryId = (categoryId != null && !categoryId.isBlank()) ? categoryId : category;
        String activeSort = (sort != null && !sort.isBlank()) ? sort : sortBy;
        

        if (activeSort != null) {
            activeSort = switch (activeSort) {
                case "priceAsc" -> "price_asc";
                case "priceDesc" -> "price_desc";
                case "createdAt" -> "newest";
                case "name" -> "relevance";
                default -> activeSort;
            };
        }


        String userId = SecurityUtils.getOptionalUsername();

        SearchResultResponse result = searchService.search(
                activeQuery, activeCategoryId, minPrice, maxPrice,
                activeSort != null ? activeSort : "relevance", page, size, userId);

        return ResponseEntity.ok(
                ApiResponse.<SearchResultResponse>builder()
                        .code(1000)
                        .message("Lấy danh sách sản phẩm thành công")
                        .result(result)
                        .build()
        );
    }

    @GetMapping("/creator/{creatorId}")
    public ResponseEntity<ApiResponse<Page<ProductResponse>>> getByCreator(
            @PathVariable String creatorId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<ProductResponse> products = productService.getByCreator(creatorId, pageable);
        return ResponseEntity.ok(
                ApiResponse.<Page<ProductResponse>>builder().code(1000).message("Lấy sản phẩm của Creator thành công").result(products).build()
        );
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<ProductResponse>> update(@PathVariable String id, @Valid @RequestBody ProductRequest request) {
        ProductResponse product = productService.update(id, request);
        return ResponseEntity.ok(
                ApiResponse.<ProductResponse>builder().code(1000).message("Cập nhật sản phẩm thành công").result(product).build()
        );
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        productService.delete(id);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Xóa sản phẩm thành công").build()
        );
    }

    @PostMapping("/variants/{variantId}/inventory/adjust")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Void>> adjustInventory(
            @PathVariable String variantId,
            @Valid @RequestBody InventoryAdjustRequest request) {
        productService.adjustInventory(variantId, request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Điều chỉnh tồn kho thành công").build()
        );
    }

    @GetMapping("/variants/{variantId}/inventory/history")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<java.util.List<com.vibecart.api.modules.ecommerce.dto.response.InventoryHistoryResponse>>> getInventoryHistory(
            @PathVariable String variantId) {
        java.util.List<com.vibecart.api.modules.ecommerce.dto.response.InventoryHistoryResponse> history = productService.getInventoryHistory(variantId);
        return ResponseEntity.ok(
                ApiResponse.<java.util.List<com.vibecart.api.modules.ecommerce.dto.response.InventoryHistoryResponse>>builder()
                        .code(1000)
                        .message("Lấy lịch sử tồn kho thành công")
                        .result(history)
                        .build()
        );
    }
}
