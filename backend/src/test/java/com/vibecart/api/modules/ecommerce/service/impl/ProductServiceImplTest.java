package com.vibecart.api.modules.ecommerce.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.dto.request.InventoryAdjustRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductImageRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductVariantRequest;
import com.vibecart.api.modules.ecommerce.dto.response.ProductResponse;
import com.vibecart.api.modules.ecommerce.entity.Category;
import com.vibecart.api.modules.ecommerce.entity.Inventory;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
import com.vibecart.api.modules.ecommerce.mapper.ProductMapper;
import com.vibecart.api.modules.ecommerce.repository.*;
import com.vibecart.api.modules.ecommerce.service.InventoryService;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import com.vibecart.api.modules.search.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;
    @Mock
    private ProductVariantRepository productVariantRepository;
    @Mock
    private ProductImageRepository productImageRepository;
    @Mock
    private InventoryRepository inventoryRepository;
    @Mock
    private CategoryRepository categoryRepository;
    @Mock
    private InventoryService inventoryService;
    @Mock
    private ProductMapper productMapper;
    @Mock
    private InventoryHistoryRepository inventoryHistoryRepository;
    @Mock
    private OutboxEventRepository outboxEventRepository;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private final String userId = "creator-123";
    private final String categoryId = "cat-leaf-abc";

    @BeforeEach
    void mockSecurityContext() {
        Authentication authentication = mock(Authentication.class);
        lenient().when(authentication.getName()).thenReturn("testuser");
        
        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        SecurityContextHolder.setContext(securityContext);

        User mockUser = User.builder().email("creator@vibecart.com").fullName("Main Creator").build();
        mockUser.setId(userId);
        lenient().when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(mockUser));
    }

    @Test
    void create_ThrowsCategoryNotLeaf_WhenCategoryHasChildren() {
        ProductRequest request = new ProductRequest("Pants", "A pair of pants", categoryId, List.of(), List.of());

        Category parentCategory = Category.builder().name("Clothes").build();
        parentCategory.setId(categoryId);
        
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(parentCategory));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(true);

        AppException exception = assertThrows(AppException.class, () -> productService.create(request));
        assertEquals(ErrorCode.CATEGORY_NOT_LEAF, exception.getErrorCode());
        verify(productRepository, never()).save(any(Product.class));
    }

    @Test
    void create_Success_SeedsInitialStockImport() {
        ProductVariantRequest variantReq = new ProductVariantRequest("SKU-1", "Red - XL", BigDecimal.valueOf(100000), BigDecimal.ZERO, 50);
        ProductImageRequest imageReq = new ProductImageRequest("image1.png", true, 0);
        ProductRequest request = new ProductRequest("Fancy SPU", "A fancy SPU product", categoryId, List.of(variantReq), List.of(imageReq));

        Category leafCategory = Category.builder().name("Pants").build();
        leafCategory.setId(categoryId);
        
        when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(leafCategory));
        when(categoryRepository.existsByParentId(categoryId)).thenReturn(false);

        Product savedProduct = Product.builder().name("Fancy SPU").creatorId(userId).category(leafCategory).build();
        savedProduct.setId("spu-id-1");
        
        when(productRepository.save(any(Product.class))).thenReturn(savedProduct);

        ProductVariant savedVariant = ProductVariant.builder().skuCode("SKU-1").variantName("Red - XL").product(savedProduct).build();
        savedVariant.setId("var-id-1");
        
        when(productVariantRepository.save(any(ProductVariant.class))).thenReturn(savedVariant);

        when(productRepository.findByIdWithDetails("spu-id-1")).thenReturn(Optional.of(savedProduct));

        ProductResponse response = productService.create(request);

        verify(productRepository).save(any(Product.class));
        verify(productVariantRepository).save(any(ProductVariant.class));
        verify(inventoryRepository).save(any(Inventory.class));

        verify(inventoryService).importStock(eq("var-id-1"), eq(50), anyString(), eq(userId));
        verify(outboxEventRepository).save(any());
    }

    @Test
    void delete_Success_CreatesOutboxDeleteSyncEvent() {
        String productId = "spu-999";
        Product product = Product.builder()
                .name("Unwanted item")
                .creatorId(userId)
                .build();
        product.setId(productId);

        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productVariantRepository.findByProductId(productId)).thenReturn(Collections.emptyList());

        productService.delete(productId);

        verify(productRepository).delete(product);
        verify(outboxEventRepository).save(argThat(event -> 
            "PRODUCT".equals(event.getAggregateType()) && 
            "PRODUCT_DELETED".equals(event.getEventType()) && 
            productId.equals(event.getAggregateId())
        ));
    }

    @Test
    void adjustInventory_Success_CallsWarehouseService() {
        String variantId = "var-888";
        InventoryAdjustRequest request = new InventoryAdjustRequest(20, "Restocking");

        Product mockProduct = Product.builder().creatorId(userId).build();
        ProductVariant variant = ProductVariant.builder()
                .product(mockProduct)
                .variantName("Hat")
                .build();
        variant.setId(variantId);
        
        when(productVariantRepository.findById(variantId)).thenReturn(Optional.of(variant));

        productService.adjustInventory(variantId, request);

        verify(inventoryService).adjustStock(variantId, 20, "Restocking", userId);
    }
}
