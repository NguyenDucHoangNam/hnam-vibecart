package com.vibecart.api.modules.ecommerce.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.dto.request.InventoryAdjustRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductImageRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductRequest;
import com.vibecart.api.modules.ecommerce.dto.request.ProductVariantRequest;
import com.vibecart.api.modules.ecommerce.dto.response.ProductResponse;
import com.vibecart.api.modules.ecommerce.dto.response.InventoryHistoryResponse;
import com.vibecart.api.modules.ecommerce.repository.InventoryHistoryRepository;
import com.vibecart.api.modules.ecommerce.entity.Category;
import com.vibecart.api.modules.ecommerce.entity.Inventory;
import com.vibecart.api.modules.ecommerce.entity.Product;
import com.vibecart.api.modules.ecommerce.entity.ProductImage;
import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
import com.vibecart.api.modules.ecommerce.event.ProductSyncProducer;
import com.vibecart.api.modules.ecommerce.mapper.ProductMapper;
import com.vibecart.api.modules.ecommerce.repository.CategoryRepository;
import com.vibecart.api.modules.ecommerce.repository.InventoryRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductImageRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductRepository;
import com.vibecart.api.modules.ecommerce.repository.ProductVariantRepository;
import com.vibecart.api.modules.ecommerce.service.InventoryService;
import com.vibecart.api.modules.ecommerce.service.ProductService;
import com.vibecart.api.modules.search.entity.OutboxEvent;
import com.vibecart.api.modules.search.repository.OutboxEventRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.domain.PageRequest;
import com.vibecart.api.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Implementation của {@link ProductService} quản lý sản phẩm, biến thể, tồn kho.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;
    private final ProductVariantRepository productVariantRepository;
    private final ProductImageRepository productImageRepository;
    private final InventoryRepository inventoryRepository;
    private final CategoryRepository categoryRepository;
    private final InventoryService inventoryService;
    private final ProductMapper productMapper;
    private final ProductSyncProducer productSyncProducer;
    private final InventoryHistoryRepository inventoryHistoryRepository;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Creating product '{}' by user: {}", request.name(), currentUserId);


        String categoryId = request.categoryId();
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));


        if (categoryRepository.existsByParentId(categoryId)) {
            throw new AppException(ErrorCode.CATEGORY_NOT_LEAF);
        }


        Product product = Product.builder()
                .name(request.name())
                .description(request.description())
                .category(category)
                .creatorId(currentUserId)
                .status(ProductStatus.ACTIVE)
                .build();

        Product savedProduct = productRepository.save(product);


        if (request.variants() != null) {
            for (ProductVariantRequest variantReq : request.variants()) {
                ProductVariant variant = ProductVariant.builder()
                        .product(savedProduct)
                        .skuCode(variantReq.skuCode())
                        .variantName(variantReq.variantName())
                        .price(variantReq.price())
                        .discountPrice(variantReq.discountPrice() != null
                                ? variantReq.discountPrice() : BigDecimal.ZERO)
                        .status(ProductStatus.ACTIVE)
                        .build();
                ProductVariant savedVariant = productVariantRepository.save(variant);

                int initialQty = variantReq.initialQuantity() != null
                        ? variantReq.initialQuantity() : 0;


                Inventory inventory = Inventory.builder()
                        .variant(savedVariant)
                        .quantity(0)
                        .reservedQuantity(0)
                        .build();
                inventoryRepository.save(inventory);

                if (initialQty > 0) {
                    inventoryService.importStock(savedVariant.getId(), initialQty,
                             "Nhập tồn kho ban đầu khi tạo sản phẩm", currentUserId);
                }
            }
        }


        if (request.images() != null) {
            for (ProductImageRequest imageReq : request.images()) {
                ProductImage image = ProductImage.builder()
                        .product(savedProduct)
                        .imageUrl(imageReq.imageUrl())
                        .thumbnail(imageReq.isThumbnail())
                        .sortOrder(imageReq.sortOrder() != null ? imageReq.sortOrder() : 0)
                        .build();
                productImageRepository.save(image);
            }
        }

        log.info("Product created successfully with ID: {}", savedProduct.getId());


        saveOutboxEvent("CREATED", savedProduct);

        return productMapper.toResponse(
                productRepository.findByIdWithDetails(savedProduct.getId())
                        .orElse(savedProduct));
    }

    @Override
    @Transactional(readOnly = true)
    public ProductResponse getById(String id) {
        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));
        return productMapper.toResponse(product);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getAll(Pageable pageable) {
        return productRepository.findAll(pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getByCreator(String creatorId, Pageable pageable) {
        return productRepository.findByCreatorId(creatorId, pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ProductResponse> getByCategory(String categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable)
                .map(productMapper::toResponse);
    }

    @Override
    @Transactional
    public ProductResponse update(String id, ProductRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Updating product {} by user: {}", id, currentUserId);

        Product product = productRepository.findByIdWithDetails(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));


        if (!product.getCreatorId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }


        product.setName(request.name());
        product.setDescription(request.description());


        if (request.categoryId() != null && !request.categoryId().equals(product.getCategory().getId())) {
            Category newCategory = categoryRepository.findById(request.categoryId())
                    .orElseThrow(() -> new AppException(ErrorCode.CATEGORY_NOT_FOUND));
            if (categoryRepository.existsByParentId(request.categoryId())) {
                throw new AppException(ErrorCode.CATEGORY_NOT_LEAF);
            }
            product.setCategory(newCategory);
        }

        Product updatedProduct = productRepository.save(product);


        if (request.variants() != null) {

            List<ProductVariant> existingVariants = productVariantRepository.findByProductIdWithInventory(id);
            Map<String, ProductVariant> existingBySkuCode = existingVariants.stream()
                    .collect(Collectors.toMap(ProductVariant::getSkuCode, v -> v));

            Set<String> requestedSkuCodes = new HashSet<>();

            for (ProductVariantRequest variantReq : request.variants()) {
                requestedSkuCodes.add(variantReq.skuCode());
                ProductVariant existingVariant = existingBySkuCode.get(variantReq.skuCode());

                if (existingVariant != null) {

                    existingVariant.setVariantName(variantReq.variantName());
                    existingVariant.setPrice(variantReq.price());
                    existingVariant.setDiscountPrice(variantReq.discountPrice() != null
                            ? variantReq.discountPrice() : BigDecimal.ZERO);
                    productVariantRepository.save(existingVariant);
                    log.debug("Updated existing variant skuCode={}", variantReq.skuCode());
                } else {

                    ProductVariant newVariant = ProductVariant.builder()
                            .product(updatedProduct)
                            .skuCode(variantReq.skuCode())
                            .variantName(variantReq.variantName())
                            .price(variantReq.price())
                            .discountPrice(variantReq.discountPrice() != null
                                    ? variantReq.discountPrice() : BigDecimal.ZERO)
                            .status(ProductStatus.ACTIVE)
                            .build();
                    ProductVariant savedVariant = productVariantRepository.save(newVariant);

                    int newInitialQty = variantReq.initialQuantity() != null
                            ? variantReq.initialQuantity() : 0;


                    Inventory inventory = Inventory.builder()
                            .variant(savedVariant)
                            .quantity(0)
                            .reservedQuantity(0)
                            .build();
                    inventoryRepository.save(inventory);

                    if (newInitialQty > 0) {
                        inventoryService.importStock(savedVariant.getId(), newInitialQty,
                                "Nhập tồn kho ban đầu cho phiên bản mới thêm khi cập nhật sản phẩm", currentUserId);
                    }
                    log.debug("Created new variant skuCode={} with initialQty={}", variantReq.skuCode(), newInitialQty);
                }
            }


            for (ProductVariant existing : existingVariants) {
                if (!requestedSkuCodes.contains(existing.getSkuCode())) {
                    productVariantRepository.delete(existing);
                    log.debug("Soft-deleted removed variant skuCode={}", existing.getSkuCode());
                }
            }
        }


        if (request.images() != null) {
            updatedProduct.getImages().clear();
            productRepository.save(updatedProduct);
            productRepository.flush();

            for (ProductImageRequest imageReq : request.images()) {
                ProductImage image = ProductImage.builder()
                        .product(updatedProduct)
                        .imageUrl(imageReq.imageUrl())
                        .thumbnail(imageReq.isThumbnail())
                        .sortOrder(imageReq.sortOrder() != null ? imageReq.sortOrder() : 0)
                        .build();
                updatedProduct.getImages().add(image);
            }
            productRepository.save(updatedProduct);
        }

        log.info("Product updated successfully: {}", id);


        saveOutboxEvent("UPDATED", updatedProduct);

        return productMapper.toResponse(
                productRepository.findByIdWithDetails(id).orElse(updatedProduct));
    }

    @Override
    @Transactional
    public void delete(String id) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Deleting product {} by user: {}", id, currentUserId);

        Product product = productRepository.findById(id)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));


        if (!product.getCreatorId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }


        List<ProductVariant> variants = productVariantRepository.findByProductId(id);
        for (ProductVariant variant : variants) {
            productVariantRepository.delete(variant);
        }


        productRepository.delete(product);
        log.info("Product soft deleted: {}", id);


        saveDeletedOutboxEvent(id);
    }

    @Override
    @Transactional
    public void adjustInventory(String variantId, InventoryAdjustRequest request) {
        String currentUserId = SecurityUtils.getCurrentUserId();
        log.info("Adjusting inventory for variant {}: {}, reason: {}",
                variantId, request.adjustmentQuantity(), request.reason());


        ProductVariant variant = productVariantRepository.findById(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));


        if (!variant.getProduct().getCreatorId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        inventoryService.adjustStock(variantId, request.adjustmentQuantity(),
                request.reason(), currentUserId);

        log.info("Inventory adjusted successfully for variant: {}", variantId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<InventoryHistoryResponse> getInventoryHistory(String variantId) {
        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        String currentUserId = SecurityUtils.getCurrentUserId();
        if (!inventory.getVariant().getProduct().getCreatorId().equals(currentUserId) && !SecurityUtils.isAdmin()) {
            throw new AppException(ErrorCode.UNAUTHORIZED);
        }

        return inventoryHistoryRepository.findByInventoryIdOrderByCreatedAtDesc(inventory.getId()).stream()
                .map(h -> new InventoryHistoryResponse(
                        h.getId(),
                        h.getTransactionType().name(),
                        h.getQuantityChanged(),
                        h.getReason(),
                        h.getCreatedBy(),
                        h.getCreatedAt()
                ))
                .toList();
    }

    private void saveOutboxEvent(String eventType, Product product) {
        try {

            String thumbnailUrl = null;
            if (product.getImages() != null) {
                thumbnailUrl = product.getImages().stream()
                        .filter(ProductImage::isThumbnail)
                        .map(ProductImage::getImageUrl)
                        .findFirst()
                        .orElse(product.getImages().isEmpty() ? null : product.getImages().get(0).getImageUrl());
            }


            BigDecimal minPrice = BigDecimal.ZERO;
            BigDecimal maxPrice = BigDecimal.ZERO;
            if (product.getVariants() != null && !product.getVariants().isEmpty()) {
                minPrice = product.getVariants().stream()
                        .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                ? v.getDiscountPrice() : v.getPrice())
                        .min(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
                maxPrice = product.getVariants().stream()
                        .map(v -> v.getDiscountPrice() != null && v.getDiscountPrice().compareTo(BigDecimal.ZERO) > 0
                                ? v.getDiscountPrice() : v.getPrice())
                        .max(BigDecimal::compareTo)
                        .orElse(BigDecimal.ZERO);
            }

            com.vibecart.api.modules.ecommerce.event.ProductSyncEvent syncEvent = 
                com.vibecart.api.modules.ecommerce.event.ProductSyncEvent.builder()
                    .eventType(eventType)
                    .timestamp(ZonedDateTime.now())
                    .productId(product.getId())
                    .name(product.getName())
                    .description(product.getDescription())
                    .categoryId(product.getCategory() != null ? product.getCategory().getId() : null)
                    .categoryName(product.getCategory() != null ? product.getCategory().getName() : null)
                    .creatorId(product.getCreatorId())
                    .thumbnailUrl(thumbnailUrl)
                    .minPrice(minPrice)
                    .maxPrice(maxPrice)
                    .status(product.getStatus().name())
                    .build();

            String payload = objectMapper.writeValueAsString(syncEvent);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("PRODUCT")
                    .aggregateId(product.getId())
                    .eventType("PRODUCT_" + eventType)
                    .payload(payload)
                    .status("PENDING")
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Recorded transactional outbox event '{}' for SPU product: {}", eventType, product.getId());
        } catch (Exception e) {
            log.error("Failed to write transactional outbox event for product ID: {}", product.getId(), e);
            throw new RuntimeException("Failed to register SPU sync outbox event", e);
        }
    }

    private void saveDeletedOutboxEvent(String productId) {
        try {
            com.vibecart.api.modules.ecommerce.event.ProductSyncEvent syncEvent = 
                com.vibecart.api.modules.ecommerce.event.ProductSyncEvent.builder()
                    .eventType("DELETED")
                    .timestamp(ZonedDateTime.now())
                    .productId(productId)
                    .build();

            String payload = objectMapper.writeValueAsString(syncEvent);

            OutboxEvent outboxEvent = OutboxEvent.builder()
                    .aggregateType("PRODUCT")
                    .aggregateId(productId)
                    .eventType("PRODUCT_DELETED")
                    .payload(payload)
                    .status("PENDING")
                    .build();

            outboxEventRepository.save(outboxEvent);
            log.info("Recorded transactional outbox delete event for product: {}", productId);
        } catch (Exception e) {
            log.error("Failed to write deleted outbox event for product ID: {}", productId, e);
            throw new RuntimeException("Failed to register SPU delete outbox event", e);
        }
    }

    private java.time.ZonedDateTime nowZoned() {
        return java.time.ZonedDateTime.now();
    }
}
