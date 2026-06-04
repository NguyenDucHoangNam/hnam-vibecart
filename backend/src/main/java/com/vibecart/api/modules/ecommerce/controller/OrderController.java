package com.vibecart.api.modules.ecommerce.controller;

import com.vibecart.api.common.dto.ApiResponse;
import com.vibecart.api.modules.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.vibecart.api.modules.ecommerce.dto.request.PlaceOrderRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CheckoutResponse;
import com.vibecart.api.modules.ecommerce.dto.response.OrderResponse;
import com.vibecart.api.modules.ecommerce.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import org.springframework.data.redis.core.StringRedisTemplate;
import com.vibecart.api.modules.iam.repository.UserRepository;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OrderController.class);

    private final OrderService orderService;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;

    @PostMapping
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<CheckoutResponse>> placeOrder(
            @Valid @RequestBody PlaceOrderRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @CookieValue(value = "affiliate_creator_id", required = false) String affiliateCreatorId) {
        String userId = getCurrentUserId();
        log.info("Place order from user: {}, affiliateCreatorId: {}", userId, affiliateCreatorId);
        CheckoutResponse checkout = orderService.placeOrder(userId, request, idempotencyKey);

        if (affiliateCreatorId != null && !affiliateCreatorId.isBlank() && checkout.subOrders() != null) {
            for (OrderResponse subOrder : checkout.subOrders()) {
                try {
                    String key = "referral:order:" + subOrder.orderId();
                    redisTemplate.opsForValue().set(key, affiliateCreatorId, 30, TimeUnit.DAYS);
                    log.info("Linked order {} to affiliate creator {} in Redis", subOrder.orderId(), affiliateCreatorId);
                } catch (Exception e) {
                    log.error("Failed to save affiliate referral to Redis for order: {}", subOrder.orderId(), e);
                }
            }
        }

        return ResponseEntity.status(HttpStatus.CREATED).body(
                ApiResponse.<CheckoutResponse>builder().code(1000)
                        .message("Đơn hàng đã được khởi tạo và phân tách thành công theo Creators")
                        .result(checkout).build()
        );
    }

    @GetMapping("/{orderId}")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> getOrder(@PathVariable String orderId) {
        String userId = getCurrentUserId();
        OrderResponse order = orderService.getOrderById(userId, orderId);
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder().code(1000).message("Lấy thông tin đơn hàng thành công").result(order).build()
        );
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<OrderResponse>>> getMyOrders(
            @RequestParam(required = false) String status,
            @PageableDefault(size = 10) Pageable pageable) {
        String userId = getCurrentUserId();
        Page<OrderResponse> orders = orderService.getMyOrders(userId, status, pageable);
        return ResponseEntity.ok(
                ApiResponse.<Page<OrderResponse>>builder().code(1000).message("Lấy lịch sử đơn hàng thành công").result(orders).build()
        );
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody OrderStatusUpdateRequest request) {
        OrderResponse order = orderService.updateOrderStatus(id, request);
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder().code(1000).message("Cập nhật trạng thái đơn hàng thành công").result(order).build()
        );
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasAnyRole('USER', 'CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<OrderResponse>> cancelOrder(@PathVariable String id) {
        String userId = getCurrentUserId();
        OrderResponse order = orderService.cancelOrder(userId, id);
        return ResponseEntity.ok(
                ApiResponse.<OrderResponse>builder().code(1000).message("Hủy đơn hàng thành công").result(order).build()
        );
    }

    @GetMapping("/creator")
    @PreAuthorize("hasAnyRole('CREATOR', 'ADMIN')")
    public ResponseEntity<ApiResponse<org.springframework.data.domain.Page<OrderResponse>>> getCreatorOrders(
            @RequestParam(required = false) String status,
            @org.springframework.data.web.PageableDefault(size = 10) org.springframework.data.domain.Pageable pageable) {
        String creatorId = getCurrentUserId();
        org.springframework.data.domain.Page<OrderResponse> orders = orderService.getCreatorOrders(creatorId, status, pageable);
        return ResponseEntity.ok(
                ApiResponse.<org.springframework.data.domain.Page<OrderResponse>>builder()
                        .code(1000)
                        .message("Lấy đơn hàng Creator thành công")
                        .result(orders)
                        .build()
        );
    }

    private String getCurrentUserId() {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        return userRepository.findByUsername(username)
                .map(com.vibecart.api.modules.iam.entity.User::getId)
                .orElse(username);
    }
}
