package com.vibecart.api.modules.ecommerce.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.ecommerce.dto.request.CartItemRequest;
import com.vibecart.api.modules.ecommerce.dto.request.CartMergeRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CartResponse;
import com.vibecart.api.modules.ecommerce.service.CartService;
import com.vibecart.api.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Controller quản lý giỏ hàng của người dùng.
 */
@RestController
@RequestMapping("/api/v1/cart")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
public class CartController {

    private final CartService cartService;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        String userId = SecurityUtils.getCurrentUserId();
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder().code(1000).message("Lấy giỏ hàng thành công").result(cart).build());
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Void>> addItem(@Valid @RequestBody CartItemRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        cartService.addItem(userId, request.variantId(), request.quantity());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Đã thêm sản phẩm vào giỏ hàng thành công").build());
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<ApiResponse<Void>> updateQuantity(
            @PathVariable String variantId,
            @RequestBody Map<String, Integer> body) {
        String userId = SecurityUtils.getCurrentUserId();
        int quantity = body.getOrDefault("quantity", 0);
        cartService.updateItemQuantity(userId, variantId, quantity);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Cập nhật số lượng sản phẩm thành công").build());
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(@PathVariable String variantId) {
        String userId = SecurityUtils.getCurrentUserId();
        cartService.removeItem(userId, variantId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Đã xóa sản phẩm khỏi giỏ hàng").build());
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<Void>> mergeCart(@Valid @RequestBody CartMergeRequest request) {
        String userId = SecurityUtils.getCurrentUserId();
        cartService.mergeCart(userId, request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Gộp giỏ hàng thành công").build());
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        String userId = SecurityUtils.getCurrentUserId();
        cartService.clearCart(userId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Đã xóa sạch giỏ hàng").build());
    }
}
