package com.vibecart.api.modules.ecommerce.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.dto.request.OrderItemRequest;
import com.vibecart.api.modules.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.vibecart.api.modules.ecommerce.dto.request.PlaceOrderRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CheckoutResponse;
import com.vibecart.api.modules.ecommerce.dto.response.OrderItemResponse;
import com.vibecart.api.modules.ecommerce.dto.response.OrderResponse;
import com.vibecart.api.modules.ecommerce.entity.Order;
import com.vibecart.api.modules.ecommerce.entity.OrderItem;
import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import com.vibecart.api.modules.ecommerce.enums.OrderStatus;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
import com.vibecart.api.modules.ecommerce.event.OrderPaidEvent;
import com.vibecart.api.modules.ecommerce.event.OrderPaidProducer;
import com.vibecart.api.modules.ecommerce.event.OrderDeliveredEvent;
import com.vibecart.api.modules.ecommerce.event.OrderDeliveredProducer;
import com.vibecart.api.modules.ecommerce.event.OrderCancelledEvent;
import com.vibecart.api.modules.ecommerce.event.OrderCancelledProducer;
import com.vibecart.api.modules.ecommerce.mapper.OrderMapper;
import com.vibecart.api.modules.ecommerce.repository.OrderItemRepository;
import com.vibecart.api.modules.ecommerce.repository.OrderRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductVariantRepository;
import com.vibecart.api.modules.ecommerce.service.CartService;
import com.vibecart.api.modules.ecommerce.service.InventoryService;
import com.vibecart.api.modules.ecommerce.service.OrderService;
import com.vibecart.api.modules.ecommerce.service.PayOSService;
import com.vibecart.api.modules.ecommerce.service.VoucherService;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Implementation của {@link OrderService} xử lý đơn hàng: đặt, thanh toán, hủy, cập nhật trạng thái.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class OrderServiceImpl implements OrderService {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final ProductVariantRepository productVariantRepository;
    private final InventoryService inventoryService;
    private final CartService cartService;
    private final VoucherService voucherService;
    private final OrderMapper orderMapper;
    private final PayOSService payOSService;
    private final RedissonClient redissonClient;
    private final StringRedisTemplate redisTemplate;
    private final UserRepository userRepository;
    private final OrderPaidProducer orderPaidProducer;
    private final OrderDeliveredProducer orderDeliveredProducer;
    private final OrderCancelledProducer orderCancelledProducer;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private static final String LOCK_PREFIX = "lock:variant:";
    private static final String IDEMPOTENCY_PREFIX = "lock:order_request:";
    private static final long LOCK_WAIT_MS = 3000;
    private static final long LOCK_LEASE_MS = 5000;
    private static final int ORDER_TIMEOUT_MINUTES = 15;



    @Override
    public CheckoutResponse placeOrder(String userId, PlaceOrderRequest request, String idempotencyKey) {
        log.info("Place order request from user: {}, idempotencyKey: {}", userId, idempotencyKey);


        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            String idempotencyRedisKey = IDEMPOTENCY_PREFIX + userId + ":" + idempotencyKey;
            Boolean isNew = redisTemplate.opsForValue()
                    .setIfAbsent(idempotencyRedisKey, "processing", Duration.ofSeconds(30));
            if (Boolean.FALSE.equals(isNew)) {
                throw new AppException(ErrorCode.DUPLICATE_ORDER_REQUEST);
            }
        }

        List<OrderItemRequest> items = request.items();
        List<String> variantIds = items.stream()
                .map(OrderItemRequest::variantId)
                .distinct()
                .toList();


        List<ProductVariant> variants = productVariantRepository
                .findAllByIdWithInventoryAndProduct(variantIds);


        Map<String, Integer> quantityMap = items.stream()
                .collect(Collectors.toMap(
                        OrderItemRequest::variantId,
                        OrderItemRequest::quantity,
                        Integer::sum));


        validateVariants(variants, variantIds, quantityMap);


        Map<String, List<ProductVariant>> creatorGroupMap = variants.stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getCreatorId()));


        List<String> sortedVariantIds = new ArrayList<>(variantIds);
        Collections.sort(sortedVariantIds);


        long sessionTime = System.currentTimeMillis();
        int randomSuffix = new java.util.Random().nextInt(100);
        String checkoutSessionId = String.valueOf(sessionTime * 100 + randomSuffix);


        List<RLock> acquiredLocks = new ArrayList<>();
        try {
            for (String variantId : sortedVariantIds) {
                RLock lock = redissonClient.getLock(LOCK_PREFIX + variantId);
                boolean acquired = lock.tryLock(LOCK_WAIT_MS, LOCK_LEASE_MS, TimeUnit.MILLISECONDS);
                if (!acquired) {
                    log.warn("Failed to acquire lock for variant: {}. System busy.", variantId);
                    throw new AppException(ErrorCode.SYSTEM_BUSY);
                }
                acquiredLocks.add(lock);
            }


            List<Order> orders = transactionTemplate
                    .execute(status -> createOrdersTransactional(userId, request, creatorGroupMap, quantityMap, checkoutSessionId));


            BigDecimal totalSessionAmount = orders.stream()
                    .map(Order::getFinalAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

            String globalPaymentUrl = null;
            try {
                long numericSessionId = Long.parseLong(checkoutSessionId);
                long amountLong = totalSessionAmount.longValue();
                String description = "VBC Session " + checkoutSessionId;
                globalPaymentUrl = payOSService.createPaymentLink(numericSessionId, amountLong, description);
                
                log.info("Aggregated PayOS payment link created for session: {}, URL: {}", checkoutSessionId, globalPaymentUrl);
            } catch (Exception e) {
                log.error("Failed to create aggregated PayOS payment link for session: {}", checkoutSessionId, e);
            }


            List<OrderResponse> subOrderResponses = new ArrayList<>();
            for (Order order : orders) {
                OrderResponse response = mapOrderToResponse(order, globalPaymentUrl);
                subOrderResponses.add(response);
            }

            return new CheckoutResponse(checkoutSessionId, subOrderResponses, ZonedDateTime.now());

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Lock acquisition interrupted for user: {}", userId, e);
            throw new AppException(ErrorCode.SYSTEM_BUSY);
        } finally {

            for (RLock lock : acquiredLocks) {
                try {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    log.error("Failed to release lock: {}", lock.getName(), e);
                }
            }
        }
    }

    /**
     * Core transactional order creation — one sub-order per creator.
     * This transaction must be as SHORT as possible.
     * NO external API calls (PayOS) inside this transaction!
     */
    @Transactional
    protected List<Order> createOrdersTransactional(
            String userId,
            PlaceOrderRequest request,
            Map<String, List<ProductVariant>> creatorGroupMap,
            Map<String, Integer> quantityMap,
            String checkoutSessionId) {

        List<Order> createdOrders = new ArrayList<>();
        List<String> allOrderedVariantIds = new ArrayList<>();
        int orderIndex = 0;


        BigDecimal grandTotal = BigDecimal.ZERO;
        for (List<ProductVariant> grpVariants : creatorGroupMap.values()) {
            for (ProductVariant variant : grpVariants) {
                int qty = quantityMap.get(variant.getId());
                BigDecimal price = variant.getPrice();
                BigDecimal dp = variant.getDiscountPrice();
                BigDecimal effectivePrice = (dp != null && dp.compareTo(BigDecimal.ZERO) > 0) ? dp : price;
                grandTotal = grandTotal.add(effectivePrice.multiply(BigDecimal.valueOf(qty)));
            }
        }


        BigDecimal globalVoucherDiscount = BigDecimal.ZERO;
        com.vibecart.api.modules.ecommerce.entity.Voucher validatedVoucher = null;
        if (request.voucherCode() != null && !request.voucherCode().isBlank()) {
            validatedVoucher = voucherService.validateVoucher(request.voucherCode(), grandTotal);
            globalVoucherDiscount = voucherService.calculateDiscount(validatedVoucher, grandTotal);
            log.info("Voucher {} validated, global discount: {}", request.voucherCode(), globalVoucherDiscount);
        }

        for (Map.Entry<String, List<ProductVariant>> entry : creatorGroupMap.entrySet()) {
            String creatorId = entry.getKey();
            List<ProductVariant> creatorVariants = entry.getValue();
            orderIndex++;


            String uniquePart = UUID.randomUUID().toString().replace("-", "").substring(0, 8).toUpperCase();
            String orderCode = "VBC" + uniquePart + "-" + orderIndex;


            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal productDiscount = BigDecimal.ZERO;

            for (ProductVariant variant : creatorVariants) {
                int qty = quantityMap.get(variant.getId());
                BigDecimal price = variant.getPrice();
                BigDecimal discountPrice = variant.getDiscountPrice();

                BigDecimal lineTotal = price.multiply(BigDecimal.valueOf(qty));
                totalAmount = totalAmount.add(lineTotal);

                if (discountPrice != null && discountPrice.compareTo(BigDecimal.ZERO) > 0) {
                    BigDecimal lineSaving = price.subtract(discountPrice)
                            .multiply(BigDecimal.valueOf(qty));
                    productDiscount = productDiscount.add(lineSaving);
                }
            }


            BigDecimal subOrderVoucherDiscount = BigDecimal.ZERO;
            if (globalVoucherDiscount.compareTo(BigDecimal.ZERO) > 0 && grandTotal.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal subOrderNetAmount = totalAmount.subtract(productDiscount);
                subOrderVoucherDiscount = globalVoucherDiscount
                        .multiply(subOrderNetAmount)
                        .divide(grandTotal, 2, java.math.RoundingMode.HALF_UP);
            }

            BigDecimal combinedDiscount = productDiscount.add(subOrderVoucherDiscount);
            BigDecimal finalAmount = totalAmount.subtract(combinedDiscount);
            if (finalAmount.compareTo(BigDecimal.valueOf(1000)) < 0) {
                finalAmount = BigDecimal.valueOf(1000);
            }


            for (ProductVariant variant : creatorVariants) {
                int qty = quantityMap.get(variant.getId());
                inventoryService.reserveStock(variant.getId(), qty, userId);
                allOrderedVariantIds.add(variant.getId());
            }


            Order order = Order.builder()
                    .orderCode(orderCode)
                    .userId(userId)
                    .creatorId(creatorId)
                    .recipientName(request.recipientName())
                    .recipientPhone(request.recipientPhone())
                    .shippingAddress(request.shippingAddress())
                    .customerNote(request.customerNote())
                    .totalAmount(totalAmount)
                    .discountAmount(combinedDiscount)
                    .finalAmount(finalAmount)
                    .status(OrderStatus.PENDING)
                    .paymentLinkId(checkoutSessionId)
                    .build();

            Order savedOrder = orderRepository.save(order);


            List<OrderItem> orderItems = new ArrayList<>();
            for (ProductVariant variant : creatorVariants) {
                int qty = quantityMap.get(variant.getId());
                BigDecimal effectivePrice = (variant.getDiscountPrice() != null
                        && variant.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0)
                                ? variant.getDiscountPrice()
                                : variant.getPrice();

                OrderItem item = OrderItem.builder()
                        .order(savedOrder)
                        .variantId(variant.getId())
                        .productName(variant.getProduct().getName())
                        .variantName(variant.getVariantName())
                        .price(variant.getPrice())
                        .discountPrice(variant.getDiscountPrice() != null
                                ? variant.getDiscountPrice()
                                : BigDecimal.ZERO)
                        .quantity(qty)
                        .build();
                orderItems.add(item);
            }
            orderItemRepository.saveAll(orderItems);
            savedOrder.setItems(orderItems);

            createdOrders.add(savedOrder);
        }


        if (validatedVoucher != null) {
            voucherService.markVoucherUsed(validatedVoucher.getId());
            log.info("Voucher {} marked as used", request.voucherCode());
        }



        return createdOrders;
    }



    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(String userId, String orderId) {
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));


        boolean isOwner = order.getUserId().equals(userId);
        boolean isCreatorOwner = order.getCreatorId() != null && order.getCreatorId().equals(userId);
        if (!isOwner && !isCreatorOwner && !SecurityUtils.isAdmin()) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        return mapOrderToResponse(order, null);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getMyOrders(String userId, String status, Pageable pageable) {
        Page<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status);
            orders = orderRepository.findByUserIdAndStatus(userId, orderStatus, pageable);
        } else {
            orders = orderRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        }

        return orders.map(order -> {
            OrderResponse response = mapOrderToResponse(order, null);
            return response;
        });
    }



    @Override
    @Transactional
    public OrderResponse updateOrderStatus(String orderId, OrderStatusUpdateRequest request) {
        log.info("Updating order {} status to: {}", orderId, request.newStatus());

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));

        OrderStatus currentStatus = order.getStatus();
        OrderStatus targetStatus;
        try {
            targetStatus = OrderStatus.valueOf(request.newStatus());
        } catch (IllegalArgumentException e) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }


        if (!currentStatus.canTransitionTo(targetStatus)) {
            log.warn("Invalid state transition: {} -> {} for order: {}",
                    currentStatus, targetStatus, orderId);
            throw new AppException(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }


        if (targetStatus == OrderStatus.CANCELLED) {
            handleCancellationStock(order, currentStatus);
        }

        if (targetStatus == OrderStatus.SHIPPED) {
            if (request.trackingNumber() == null || request.trackingNumber().isBlank()) {
                throw new AppException(ErrorCode.TRACKING_NUMBER_REQUIRED);
            }
            order.setTrackingNumber(request.trackingNumber());
        }

        order.setStatus(targetStatus);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} status updated: {} -> {}", orderId, currentStatus, targetStatus);

        if (targetStatus == OrderStatus.DELIVERED) {
            try {
                orderDeliveredProducer.sendOrderDeliveredEvent(OrderDeliveredEvent.builder()
                        .orderId(orderId)
                        .userId(order.getUserId())
                        .finalAmount(order.getFinalAmount())
                        .timestamp(ZonedDateTime.now())
                        .build());
            } catch (Exception e) {
                log.error("Failed to publish ORDER_DELIVERED event for order: {}", orderId, e);
            }
        } else if (targetStatus == OrderStatus.CANCELLED) {
            try {
                orderCancelledProducer.sendOrderCancelledEvent(OrderCancelledEvent.builder()
                        .orderId(orderId)
                        .userId(order.getUserId())
                        .timestamp(ZonedDateTime.now())
                        .build());
            } catch (Exception e) {
                log.error("Failed to publish ORDER_CANCELLED event for order: {}", orderId, e);
            }
        }

        return mapOrderToResponse(updatedOrder, null);
    }

    @Override
    @Transactional
    public OrderResponse cancelOrder(String userId, String orderId) {
        log.info("Cancel order request: {} by user: {}", orderId, userId);

        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new AppException(ErrorCode.ORDER_NOT_FOUND));


        if (!order.getUserId().equals(userId)) {
            throw new AppException(ErrorCode.ORDER_ACCESS_DENIED);
        }

        OrderStatus currentStatus = order.getStatus();
        if (!currentStatus.canTransitionTo(OrderStatus.CANCELLED)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE_TRANSITION);
        }


        handleCancellationStock(order, currentStatus);

        order.setStatus(OrderStatus.CANCELLED);
        Order updatedOrder = orderRepository.save(order);
        log.info("Order {} cancelled successfully by user: {}", orderId, userId);

        try {
            orderCancelledProducer.sendOrderCancelledEvent(OrderCancelledEvent.builder()
                    .orderId(orderId)
                    .userId(order.getUserId())
                    .timestamp(ZonedDateTime.now())
                    .build());
        } catch (Exception e) {
            log.error("Failed to publish ORDER_CANCELLED event for order: {}", orderId, e);
        }

        return mapOrderToResponse(updatedOrder, null);
    }



    @Override
    @Transactional
    public void confirmPayment(String orderCode, String transactionId, String rawResponse) {
        log.info("Confirming payment for orderCode/checkoutSessionId: {}, transactionId: {}", orderCode, transactionId);


        String webhookKey = "payment:webhook:processed:" + orderCode;
        Boolean isNew = redisTemplate.opsForValue()
                .setIfAbsent(webhookKey, "1", Duration.ofMinutes(5));
        if (Boolean.FALSE.equals(isNew)) {
            log.info("Duplicate webhook for orderCode: {}. Skipping.", orderCode);
            return;
        }


        List<Order> orders = orderRepository.findByPaymentLinkIdWithItems(orderCode);


        if (orders.isEmpty()) {
            Optional<Order> optionalOrder = orderRepository.findByOrderCodeWithItems(orderCode);
            optionalOrder.ifPresent(orders::add);
        }

        if (orders.isEmpty()) {
            log.warn("No orders found for payment code (orderCode/paymentLinkId): {}. Ignoring webhook.", orderCode);
            return;
        }

        List<String> allVariantIds = new ArrayList<>();
        String userId = null;

        for (Order order : orders) {
            userId = order.getUserId();

            String currentStatus = order.getStatus().name();
            if (OrderStatus.PAID.name().equals(currentStatus)
                    || OrderStatus.SHIPPED.name().equals(currentStatus)
                    || OrderStatus.DELIVERED.name().equals(currentStatus)) {
                log.info("Order {} already in status {}. Skipping.", order.getOrderCode(), currentStatus);
                continue;
            }


            if (OrderStatus.CANCELLED.name().equals(currentStatus)) {
                log.warn("Payment received for cancelled order: {}. Manual refund may be required.", order.getOrderCode());
                continue;
            }


            int rowsUpdated = orderRepository.updateStatusAtomically(
                    order.getId(), OrderStatus.PENDING, OrderStatus.PAID);

            if (rowsUpdated == 0) {
                log.warn("Race condition: order {} status changed before payment confirmation.", order.getOrderCode());
                continue;
            }


            List<OrderItem> orderItems = order.getItems();
            if (orderItems == null) {
                orderItems = orderItemRepository.findByOrderId(order.getId());
            }
            for (OrderItem item : orderItems) {
                inventoryService.commitStock(item.getVariantId(), item.getQuantity(), "system");
                allVariantIds.add(item.getVariantId());
            }

            log.info("Payment confirmed for order: {}. Status: PAID", order.getOrderCode());


            try {
                User user = userRepository.findById(order.getUserId()).orElse(null);
                String userEmail = user != null ? user.getEmail() : "";

                OrderPaidEvent event = OrderPaidEvent.builder()
                        .eventType("ORDER_PAID")
                        .timestamp(ZonedDateTime.now())
                        .orderId(order.getId())
                        .userId(order.getUserId())
                        .userEmail(userEmail)
                        .finalAmount(order.getFinalAmount())
                        .paymentTransactionId(transactionId)
                        .build();
                orderPaidProducer.sendOrderPaidEvent(event);
            } catch (Exception e) {
                log.warn("Failed to publish ORDER_PAID event for order: {}", order.getOrderCode(), e);
            }
        }


        if (userId != null && !allVariantIds.isEmpty()) {
            try {
                cartService.removeItems(userId, allVariantIds);
                log.info("Cleared purchased items from cart for user: {}, items: {}", userId, allVariantIds.size());
            } catch (Exception e) {
                log.error("Failed to clear cart items for user: {}", userId, e);
            }
        }
    }



    @Override
    @Transactional
    public void cancelExpiredOrders() {
        ZonedDateTime cutoff = ZonedDateTime.now().minusMinutes(ORDER_TIMEOUT_MINUTES);
        List<Order> expiredOrders = orderRepository.findExpiredPendingOrders(cutoff);

        if (expiredOrders.isEmpty()) {
            return;
        }

        log.info("Found {} expired pending orders to cancel", expiredOrders.size());

        for (Order order : expiredOrders) {
            try {

                int rowsUpdated = orderRepository.updateStatusAtomically(
                        order.getId(), OrderStatus.PENDING, OrderStatus.CANCELLED);

                if (rowsUpdated > 0) {

                    List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
                    for (OrderItem item : items) {
                        inventoryService.releaseStock(
                                item.getVariantId(), item.getQuantity(), "system");
                    }
                    log.info("Auto-cancelled expired order: {} and released reserved stock",
                            order.getId());
                    try {
                        orderCancelledProducer.sendOrderCancelledEvent(OrderCancelledEvent.builder()
                                .orderId(order.getId())
                                .userId(order.getUserId())
                                .timestamp(ZonedDateTime.now())
                                .build());
                    } catch (Exception e) {
                        log.error("Failed to publish ORDER_CANCELLED event for order: {}", order.getId(), e);
                    }
                } else {
                    log.info("Order {} was already processed (likely paid). Skipping.",
                            order.getId());
                }
            } catch (Exception e) {
                log.error("Failed to cancel expired order: {}", order.getId(), e);
            }
        }
    }



    private void validateVariants(List<ProductVariant> variants,
            List<String> requestedIds,
            Map<String, Integer> quantityMap) {

        Set<String> foundIds = variants.stream()
                .map(ProductVariant::getId)
                .collect(Collectors.toSet());
        for (String id : requestedIds) {
            if (!foundIds.contains(id)) {
                throw new AppException(ErrorCode.PRODUCT_NOT_FOUND);
            }
        }


        for (ProductVariant variant : variants) {
            if (variant.getStatus() != ProductStatus.ACTIVE) {
                throw new AppException(ErrorCode.PRODUCT_INACTIVE);
            }
            int requestedQty = quantityMap.get(variant.getId());
            int availableQty = variant.getInventory() != null
                    ? variant.getInventory().getQuantity() - variant.getInventory().getReservedQuantity()
                    : 0;
            if (availableQty < requestedQty) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }
    }

    private void handleCancellationStock(Order order, OrderStatus fromStatus) {
        List<OrderItem> items = order.getItems();
        if (items == null) {
            items = orderItemRepository.findByOrderId(order.getId());
        }

        if (fromStatus == OrderStatus.PENDING) {

            for (OrderItem item : items) {
                inventoryService.releaseStock(
                        item.getVariantId(), item.getQuantity(), order.getUserId());
            }
        } else if (fromStatus == OrderStatus.PAID) {

            for (OrderItem item : items) {
                inventoryService.refundStock(
                        item.getVariantId(), item.getQuantity(),
                        "Refund: order " + order.getOrderCode(), order.getUserId());
            }
        }
    }

    private OrderResponse mapOrderToResponse(Order order, String paymentUrl) {

        String creatorName = null;
        if (order.getCreatorId() != null) {
            creatorName = userRepository.findById(order.getCreatorId())
                    .map(User::getFullName)
                    .orElse(order.getCreatorId());
        }

        List<OrderItem> items = order.getItems();
        if (items == null) {
            items = orderItemRepository.findByOrderId(order.getId());
        }

        List<OrderItemResponse> itemResponses = items.stream()
                .map(item -> new OrderItemResponse(
                        item.getVariantId(),
                        item.getProductName(),
                        item.getVariantName(),
                        item.getPrice(),
                        item.getDiscountPrice(),
                        item.getQuantity()))
                .toList();

        return new OrderResponse(
                order.getId(),
                order.getOrderCode(),
                order.getCreatorId(),
                creatorName,
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getShippingAddress(),
                itemResponses,
                order.getTotalAmount(),
                order.getDiscountAmount(),
                order.getFinalAmount(),
                order.getStatus().name(),
                paymentUrl,
                order.getCreatedAt(),
                order.getTrackingNumber());
    }

    @Override
    @Transactional(readOnly = true)
    public Page<OrderResponse> getCreatorOrders(String creatorId, String status, Pageable pageable) {
        Page<Order> orders;
        if (status != null && !status.isBlank()) {
            OrderStatus orderStatus = OrderStatus.valueOf(status);
            orders = orderRepository.findByCreatorIdAndStatusOrderByCreatedAtDesc(creatorId, orderStatus, pageable);
        } else {
            orders = orderRepository.findByCreatorIdOrderByCreatedAtDesc(creatorId, pageable);
        }

        return orders.map(order -> mapOrderToResponse(order, null));
    }

    private long extractOrderCodeAsLong(String orderCode) {
        try {
            String hexPart = orderCode.replace("VBC", "").split("-")[0];
            long parsed = Long.parseLong(hexPart, 16);

            return Math.abs(parsed) % 9007199254740991L;
        } catch (Exception e) {

            return Math.abs(orderCode.hashCode());
        }
    }

}
