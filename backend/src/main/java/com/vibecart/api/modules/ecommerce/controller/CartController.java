package com.vibecart.api.modules.ecommerce.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.ecommerce.dto.request.CartItemRequest;
import com.vibecart.api.modules.ecommerce.dto.request.CartMergeRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CartResponse;
import com.vibecart.api.modules.ecommerce.service.CartService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import com.vibecart.api.modules.iam.repository.UserRepository;

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
    private final UserRepository userRepository;

    @GetMapping
    public ResponseEntity<ApiResponse<CartResponse>> getCart() {
        String userId = getCurrentUserId();
        CartResponse cart = cartService.getCart(userId);
        return ResponseEntity.ok(
                ApiResponse.<CartResponse>builder().code(1000).message("Lấy giỏ hàng thành công").result(cart).build());
    }

    @PostMapping("/items")
    public ResponseEntity<ApiResponse<Void>> addItem(@Valid @RequestBody CartItemRequest request) {
        String userId = getCurrentUserId();
        cartService.addItem(userId, request.variantId(), request.quantity());
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Đã thêm sản phẩm vào giỏ hàng thành công").build());
    }

    @PutMapping("/items/{variantId}")
    public ResponseEntity<ApiResponse<Void>> updateQuantity(
            @PathVariable String variantId,
            @RequestBody Map<String, Integer> body) {
        String userId = getCurrentUserId();
        int quantity = body.getOrDefault("quantity", 0);
        cartService.updateItemQuantity(userId, variantId, quantity);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Cập nhật số lượng sản phẩm thành công").build());
    }

    @DeleteMapping("/items/{variantId}")
    public ResponseEntity<ApiResponse<Void>> removeItem(@PathVariable String variantId) {
        String userId = getCurrentUserId();
        cartService.removeItem(userId, variantId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Đã xóa sản phẩm khỏi giỏ hàng").build());
    }

    @PostMapping("/merge")
    public ResponseEntity<ApiResponse<Void>> mergeCart(@Valid @RequestBody CartMergeRequest request) {
        String userId = getCurrentUserId();
        cartService.mergeCart(userId, request);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Gộp giỏ hàng thành công").build());
    }

    @DeleteMapping("/clear")
    public ResponseEntity<ApiResponse<Void>> clearCart() {
        String userId = getCurrentUserId();
        cartService.clearCart(userId);
        return ResponseEntity.ok(
                ApiResponse.<Void>builder().code(1000).message("Đã xóa sạch giỏ hàng").build());
    }

    private String getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(com.vibecart.api.modules.iam.entity.User::getId)
                .orElse(username);
    }
}
