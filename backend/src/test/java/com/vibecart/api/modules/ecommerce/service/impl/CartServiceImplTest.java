package com.vibecart.api.modules.ecommerce.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.dto.request.CartMergeRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CartResponse;
import com.vibecart.api.modules.ecommerce.entity.Inventory;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
import com.vibecart.api.modules.ecommerce.repository.ProductVariantRepository;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CartServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private ProductVariantRepository productVariantRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CartServiceImpl cartService;

    private final String userId = "user-123";
    private final String cartKey = "cart:user-123";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
    }

    @Test
    void getCart_EmptyCart_ReturnsEmptyResponse() {
        when(hashOperations.entries(cartKey)).thenReturn(Collections.emptyMap());

        CartResponse response = cartService.getCart(userId);

        assertNotNull(response);
        assertTrue(response.items().isEmpty());
        assertEquals(BigDecimal.ZERO, response.totalOriginalAmount());
        assertEquals(BigDecimal.ZERO, response.totalDiscountAmount());
    }

    @Test
    void getCart_Success_WithActiveAndUnavailableItems() {
        Map<Object, Object> cartEntries = new HashMap<>();
        cartEntries.put("var-active", "2");
        cartEntries.put("var-inactive", "5");
        when(hashOperations.entries(cartKey)).thenReturn(cartEntries);

        Product creatorProduct = Product.builder().name("Mock Shirt").creatorId("creator-abc").build();
        creatorProduct.setId("spu-1");
        creatorProduct.setDeleted(false);

        Inventory activeInventory = Inventory.builder().quantity(10).reservedQuantity(2).build();
        
        ProductVariant activeVariant = ProductVariant.builder()
                .product(creatorProduct)
                .variantName("Blue - M")
                .price(BigDecimal.valueOf(200000))
                .discountPrice(BigDecimal.valueOf(180000))
                .status(ProductStatus.ACTIVE)
                .inventory(activeInventory)
                .build();
        activeVariant.setId("var-active");

        Product deletedProduct = Product.builder().name("Old Hat").creatorId("creator-abc").build();
        deletedProduct.setId("spu-2");
        deletedProduct.setDeleted(true);

        ProductVariant inactiveVariant = ProductVariant.builder()
                .product(deletedProduct)
                .status(ProductStatus.ACTIVE)
                .build();
        inactiveVariant.setId("var-inactive");

        List<ProductVariant> dbVariants = List.of(activeVariant, inactiveVariant);
        when(productVariantRepository.findAllByIdWithFullDetails(anyList())).thenReturn(dbVariants);

        User creator = User.builder().fullName("Creative Studio").build();
        creator.setId("creator-abc");
        when(userRepository.findById("creator-abc")).thenReturn(Optional.of(creator));

        CartResponse response = cartService.getCart(userId);

        assertNotNull(response);
        assertEquals(1, response.items().size());
        assertEquals("var-active", response.items().get(0).variantId());
        assertEquals(2, response.items().get(0).quantity());
        assertEquals(BigDecimal.valueOf(400000), response.totalOriginalAmount());
        assertEquals(BigDecimal.valueOf(360000), response.totalDiscountAmount());
        assertEquals(BigDecimal.valueOf(40000), response.totalSavingAmount());

        verify(hashOperations).delete(cartKey, "var-inactive");
    }

    @Test
    void addItem_Success_WithinLimit() {
        String variantId = "var-1";
        ProductVariant variant = ProductVariant.builder()
                .status(ProductStatus.ACTIVE)
                .build();
        variant.setId(variantId);

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(hashOperations.get(cartKey, variantId)).thenReturn("5");

        cartService.addItem(userId, variantId, 3);

        verify(hashOperations).put(cartKey, variantId, "8");
        verify(redisTemplate).expire(eq(cartKey), anyLong(), any(TimeUnit.class));
    }

    @Test
    void addItem_ThrowsException_WhenExceedingMaxLimit() {
        String variantId = "var-1";
        ProductVariant variant = ProductVariant.builder()
                .status(ProductStatus.ACTIVE)
                .build();
        variant.setId(variantId);

        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));
        when(hashOperations.get(cartKey, variantId)).thenReturn("95");

        AppException exception = assertThrows(AppException.class, () -> 
                cartService.addItem(userId, variantId, 10)
        );

        assertEquals(ErrorCode.CART_QUANTITY_EXCEEDED, exception.getErrorCode());
        verify(hashOperations, never()).put(anyString(), anyString(), anyString());
    }

    @Test
    void updateItemQuantity_Success_SetsNewValue() {
        String variantId = "var-1";

        cartService.updateItemQuantity(userId, variantId, 45);

        verify(hashOperations).put(cartKey, variantId, "45");
    }

    @Test
    void updateItemQuantity_DeletesItem_WhenQuantityIsZeroOrNegative() {
        String variantId = "var-1";

        cartService.updateItemQuantity(userId, variantId, 0);
        cartService.updateItemQuantity(userId, variantId, -5);

        verify(hashOperations, times(2)).delete(cartKey, variantId);
    }

    @Test
    void mergeCart_Success_CappingValuesAtLimit() {
        CartMergeRequest.CartMergeItem item1 = new CartMergeRequest.CartMergeItem("var-1", 10);
        CartMergeRequest.CartMergeItem item2 = new CartMergeRequest.CartMergeItem("var-2", 80);
        CartMergeRequest request = new CartMergeRequest(List.of(item1, item2));

        when(hashOperations.get(cartKey, "var-1")).thenReturn("15");
        when(hashOperations.get(cartKey, "var-2")).thenReturn("40");

        cartService.mergeCart(userId, request);

        verify(hashOperations).put(cartKey, "var-1", "25");
        verify(hashOperations).put(cartKey, "var-2", "100");
        verify(redisTemplate).expire(eq(cartKey), anyLong(), any(TimeUnit.class));
    }
}
