package com.vibecart.api.modules.ecommerce.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.dto.request.OrderItemRequest;
import com.vibecart.api.modules.ecommerce.dto.request.OrderStatusUpdateRequest;
import com.vibecart.api.modules.ecommerce.dto.request.PlaceOrderRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CheckoutResponse;
import com.vibecart.api.modules.ecommerce.dto.response.OrderResponse;
import com.vibecart.api.modules.ecommerce.entity.Inventory;
import com.vibecart.api.modules.ecommerce.entity.Order;
import com.vibecart.api.modules.ecommerce.entity.OrderItem;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import com.vibecart.api.modules.ecommerce.enums.OrderStatus;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
import com.vibecart.api.modules.ecommerce.repository.OrderItemRepository;
import com.vibecart.api.modules.ecommerce.repository.OrderRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductVariantRepository;
import com.vibecart.api.modules.ecommerce.service.CartService;
import com.vibecart.api.modules.ecommerce.service.InventoryService;
import com.vibecart.api.modules.ecommerce.service.PayOSService;
import com.vibecart.api.modules.ecommerce.service.VoucherService;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.ecommerce.messaging.producer.OrderPaidProducer;
import com.vibecart.api.modules.ecommerce.messaging.producer.OrderDeliveredProducer;
import com.vibecart.api.modules.ecommerce.messaging.producer.OrderCancelledProducer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;
    @Mock
    private OrderItemRepository orderItemRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private CartService cartService;
    @Mock
    private VoucherService voucherService;
    @Mock
    private PayOSService payOSService;
    @Mock
    private RedissonClient redissonClient;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private UserRepository userRepository;
    @Mock
    private OrderPaidProducer orderPaidProducer;
    @Mock
    private OrderDeliveredProducer orderDeliveredProducer;
    @Mock
    private OrderCancelledProducer orderCancelledProducer;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private OrderServiceImpl orderService;

    private final String userId = "user-123";
    private final String idempotencyKey = "idem-key-999";

    @BeforeEach
    void setUp() {
        lenient().when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void placeOrder_Success_DistributedLock_SplitsByCreator_ClearsCart() throws InterruptedException {
        OrderItemRequest itemReq1 = new OrderItemRequest("var-1", 2);
        OrderItemRequest itemReq2 = new OrderItemRequest("var-2", 1);
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(itemReq1, itemReq2), "123 Main St", "Alice", "0901234567", "Deliver after 5pm", null);

        Product productCreatorA = Product.builder().name("Shirt").creatorId("creator-A").build();
        productCreatorA.setId("spu-1");
        
        Inventory inv1 = Inventory.builder().quantity(10).reservedQuantity(0).build();
        
        ProductVariant variant1 = ProductVariant.builder()
                .product(productCreatorA)
                .variantName("Red - M")
                .price(BigDecimal.valueOf(150000))
                .status(ProductStatus.ACTIVE)
                .inventory(inv1)
                .build();
        variant1.setId("var-1");

        Product productCreatorB = Product.builder().name("Shoe").creatorId("creator-B").build();
        productCreatorB.setId("spu-2");
        
        Inventory inv2 = Inventory.builder().quantity(5).reservedQuantity(0).build();
        
        ProductVariant variant2 = ProductVariant.builder()
                .product(productCreatorB)
                .variantName("Blue - 42")
                .price(BigDecimal.valueOf(600000))
                .status(ProductStatus.ACTIVE)
                .inventory(inv2)
                .build();
        variant2.setId("var-2");

        when(productVariantRepository.findAllByIdWithInventoryAndProduct(anyList()))
                .thenReturn(List.of(variant1, variant2));

        RLock mockLock1 = mock(RLock.class);
        RLock mockLock2 = mock(RLock.class);
        when(redissonClient.getLock("lock:variant:var-1")).thenReturn(mockLock1);
        when(redissonClient.getLock("lock:variant:var-2")).thenReturn(mockLock2);
        when(mockLock1.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(mockLock2.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(mockLock1.isHeldByCurrentThread()).thenReturn(true);
        when(mockLock2.isHeldByCurrentThread()).thenReturn(true);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        User creatorA = User.builder().fullName("Creator A Studio").build();
        User creatorB = User.builder().fullName("Creator B Design").build();
        when(userRepository.findById("creator-A")).thenReturn(Optional.of(creatorA));
        when(userRepository.findById("creator-B")).thenReturn(Optional.of(creatorB));

        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order order = invocation.getArgument(0);
            order.setId("order-sub-" + order.getCreatorId());
            return order;
        });

        when(payOSService.createPaymentLink(anyLong(), anyLong(), anyString())).thenReturn("https://checkout.payos.vn/mock-payment");

        CheckoutResponse response = orderService.placeOrder(userId, request, idempotencyKey);

        assertNotNull(response);
        assertEquals(2, response.subOrders().size());

        OrderResponse orderA = response.subOrders().stream().filter(o -> "creator-A".equals(o.creatorId())).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(300000), orderA.totalAmount());

        OrderResponse orderB = response.subOrders().stream().filter(o -> "creator-B".equals(o.creatorId())).findFirst().orElseThrow();
        assertEquals(BigDecimal.valueOf(600000), orderB.totalAmount());

        verify(mockLock1).tryLock(3000, 5000, TimeUnit.MILLISECONDS);
        verify(mockLock2).tryLock(3000, 5000, TimeUnit.MILLISECONDS);
        verify(mockLock1).unlock();
        verify(mockLock2).unlock();

        verify(inventoryService).reserveStock("var-1", 2, userId);
        verify(inventoryService).reserveStock("var-2", 1, userId);
    }

    @Test
    void placeOrder_EnforcesMinimumFinalAmount_1000VND() throws InterruptedException {
        OrderItemRequest itemReq = new OrderItemRequest("var-cheap", 1);
        PlaceOrderRequest request = new PlaceOrderRequest(List.of(itemReq), "123 Main St", "Alice", "0901234567", "", null);

        Product product = Product.builder().name("Low price pin").creatorId("creator-A").build();
        product.setId("spu-1");
        
        Inventory inv = Inventory.builder().quantity(100).reservedQuantity(0).build();
        
        ProductVariant cheapVariant = ProductVariant.builder()
                .product(product)
                .variantName("Pin")
                .price(BigDecimal.valueOf(200))
                .status(ProductStatus.ACTIVE)
                .inventory(inv)
                .build();
        cheapVariant.setId("var-cheap");

        when(productVariantRepository.findAllByIdWithInventoryAndProduct(anyList()))
                .thenReturn(List.of(cheapVariant));

        RLock mockLock = mock(RLock.class);
        when(redissonClient.getLock("lock:variant:var-cheap")).thenReturn(mockLock);
        when(mockLock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
        when(mockLock.isHeldByCurrentThread()).thenReturn(true);
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class))).thenReturn(true);

        when(userRepository.findById("creator-A")).thenReturn(Optional.of(User.builder().fullName("Studio").build()));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId("o-cheap");
            return o;
        });

        CheckoutResponse response = orderService.placeOrder(userId, request, idempotencyKey);

        assertNotNull(response);
        OrderResponse subOrder = response.subOrders().get(0);
        assertEquals(BigDecimal.valueOf(1000), subOrder.finalAmount());
    }

    @Test
    void updateOrderStatus_Shipped_ThrowsException_WhenTrackingNumberIsMissing() {
        String orderId = "order-111";
        Order order = Order.builder().status(OrderStatus.PAID).build();
        order.setId(orderId);

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));

        OrderStatusUpdateRequest reqWithoutTracking = new OrderStatusUpdateRequest("SHIPPED", null);

        AppException exception = assertThrows(AppException.class, () ->
                orderService.updateOrderStatus(orderId, reqWithoutTracking)
        );

        assertEquals(ErrorCode.TRACKING_NUMBER_REQUIRED, exception.getErrorCode());
        assertEquals(OrderStatus.PAID, order.getStatus());
    }

    @Test
    void updateOrderStatus_Shipped_Succeeds_WhenTrackingNumberIsProvided() {
        String orderId = "order-111";
        Order order = Order.builder().status(OrderStatus.PAID).build();
        order.setId(orderId);

        when(orderRepository.findByIdWithItems(orderId)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(i -> i.getArgument(0));

        OrderStatusUpdateRequest reqWithTracking = new OrderStatusUpdateRequest("SHIPPED", "VNP123456789");

        OrderResponse response = orderService.updateOrderStatus(orderId, reqWithTracking);

        assertNotNull(response);
        assertEquals("SHIPPED", response.status());
        assertEquals("VNP123456789", order.getTrackingNumber());
    }

    @Test
    void confirmPayment_Idempotent_AtomicPaidTransition() {
        String orderCode = "VBC178000-1";
        Order order = Order.builder()
                .orderCode(orderCode)
                .userId(userId)
                .status(OrderStatus.PENDING)
                .items(List.of(OrderItem.builder().variantId("var-1").quantity(3).build()))
                .build();
        order.setId("order-success");

        when(valueOperations.setIfAbsent(eq("payment:webhook:processed:" + orderCode), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(orderRepository.findByOrderCodeWithItems(orderCode)).thenReturn(Optional.of(order));
        
        when(orderRepository.updateStatusAtomically("order-success", OrderStatus.PENDING, OrderStatus.PAID))
                .thenReturn(1);

        orderService.confirmPayment(orderCode, "ref-abc", "raw-json-data");

        verify(inventoryService).commitStock("var-1", 3, "system");
        verify(cartService).removeItems(userId, List.of("var-1"));
        verify(orderPaidProducer).sendOrderPaidEvent(any());
    }

    @Test
    void confirmPayment_SkipsProcessing_WhenAlreadyPaid() {
        String orderCode = "VBC178000-1";
        Order alreadyPaidOrder = Order.builder()
                .orderCode(orderCode)
                .status(OrderStatus.PAID)
                .build();
        alreadyPaidOrder.setId("order-success");

        when(valueOperations.setIfAbsent(eq("payment:webhook:processed:" + orderCode), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(orderRepository.findByOrderCodeWithItems(orderCode)).thenReturn(Optional.of(alreadyPaidOrder));

        orderService.confirmPayment(orderCode, "ref-abc", "raw-json-data");

        verify(orderRepository, never()).updateStatusAtomically(anyString(), any(), any());
        verify(inventoryService, never()).commitStock(anyString(), anyInt(), anyString());
    }

    @Test
    void cancelExpiredOrders_AtomicallyReleasesStock() {
        Order expiredOrder = Order.builder()
                .status(OrderStatus.PENDING)
                .userId(userId)
                .build();
        expiredOrder.setId("order-expired");

        when(orderRepository.findExpiredPendingOrders(any(ZonedDateTime.class)))
                .thenReturn(List.of(expiredOrder));

        when(orderRepository.updateStatusAtomically("order-expired", OrderStatus.PENDING, OrderStatus.CANCELLED))
                .thenReturn(1);

        OrderItem item = OrderItem.builder().variantId("var-1").quantity(4).build();
        when(orderItemRepository.findByOrderId("order-expired")).thenReturn(List.of(item));

        orderService.cancelExpiredOrders();

        verify(inventoryService).releaseStock("var-1", 4, "system");
        verify(orderCancelledProducer).sendOrderCancelledEvent(any());
    }
}
