package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.modules.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.vibecart.api.modules.ecommerce.dto.request.PlaceOrderRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CheckoutResponse;
import com.vibecart.api.modules.ecommerce.dto.response.OrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
public interface OrderService {
    CheckoutResponse placeOrder(String userId, PlaceOrderRequest request, String idempotencyKey);
    OrderResponse getOrderById(String userId, String orderId);
    Page<OrderResponse> getMyOrders(String userId, String status, Pageable pageable);
    OrderResponse updateOrderStatus(String orderId, OrderStatusUpdateRequest request);
    OrderResponse cancelOrder(String userId, String orderId);
    void confirmPayment(String orderCode, String transactionId, String rawResponse);
    void cancelExpiredOrders();
    Page<com.vibecart.api.modules.ecommerce.dto.response.OrderResponse> getCreatorOrders(String creatorId, String status, Pageable pageable);
}
