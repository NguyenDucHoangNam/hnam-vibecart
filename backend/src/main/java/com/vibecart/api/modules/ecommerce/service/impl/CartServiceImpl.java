package com.vibecart.api.modules.ecommerce.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.dto.request.CartMergeRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CartItemResponse;
import com.vibecart.api.modules.ecommerce.dto.response.CartResponse;
import com.vibecart.api.modules.ecommerce.entity.ProductImage;
import com.vibecart.api.modules.ecommerce.entity.ProductVariant;
import com.vibecart.api.modules.ecommerce.enums.ProductStatus;
import com.vibecart.api.modules.ecommerce.repository.ProductVariantRepository;
import com.vibecart.api.modules.ecommerce.service.CartService;
import com.vibecart.api.modules.iam.entity.User;
import com.vibecart.api.modules.iam.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Service
public class CartServiceImpl implements CartService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CartServiceImpl.class);

    private final StringRedisTemplate redisTemplate;
    private final ProductVariantRepository productVariantRepository;
    private final UserRepository userRepository;

    public CartServiceImpl(StringRedisTemplate redisTemplate,
                           ProductVariantRepository productVariantRepository,
                           UserRepository userRepository) {
        this.redisTemplate = redisTemplate;
        this.productVariantRepository = productVariantRepository;
        this.userRepository = userRepository;
    }

    private static final String CART_KEY_PREFIX = "cart:";
    private static final long CART_TTL_SECONDS = 2592000L; // 30 days
    private static final int MAX_CART_QUANTITY = 100;

    @Override
    public CartResponse getCart(String userId) {
        String cartKey = CART_KEY_PREFIX + userId;
        Map<Object, Object> cartEntries = redisTemplate.opsForHash().entries(cartKey);

        if (cartEntries.isEmpty()) {
            return new CartResponse(List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        }

        // Parse entries: variantId -> quantity
        Map<String, Integer> variantQuantityMap = new LinkedHashMap<>();
        for (Map.Entry<Object, Object> entry : cartEntries.entrySet()) {
            String variantId = entry.getKey().toString();
            int quantity = Integer.parseInt(entry.getValue().toString());
            variantQuantityMap.put(variantId, quantity);
        }

        List<String> variantIds = new ArrayList<>(variantQuantityMap.keySet());

        // Batch query variants with full details (product, images, inventory)
        List<ProductVariant> variants = productVariantRepository
                .findAllByIdWithFullDetails(variantIds);
        Map<String, ProductVariant> variantMap = variants.stream()
                .collect(Collectors.toMap(ProductVariant::getId, v -> v));

        // Collect unique creator IDs for batch user lookup
        Set<String> creatorIds = variants.stream()
                .map(v -> v.getProduct().getCreatorId())
                .collect(Collectors.toSet());
        Map<String, String> creatorNameMap = new HashMap<>();
        for (String creatorId : creatorIds) {
            userRepository.findById(creatorId)
                    .map(User::getFullName)
                    .ifPresent(name -> creatorNameMap.put(creatorId, name));
        }

        List<CartItemResponse> items = new ArrayList<>();
        List<String> variantIdsToRemove = new ArrayList<>();
        BigDecimal totalOriginalAmount = BigDecimal.ZERO;
        BigDecimal totalDiscountAmount = BigDecimal.ZERO;

        for (Map.Entry<String, Integer> entry : variantQuantityMap.entrySet()) {
            String variantId = entry.getKey();
            int quantity = entry.getValue();
            ProductVariant variant = variantMap.get(variantId);

            // Auto-remove deleted/inactive variants
            if (variant == null
                    || variant.getStatus() != ProductStatus.ACTIVE
                    || variant.getProduct().isDeleted()) {
                variantIdsToRemove.add(variantId);
                log.info("Auto-removing unavailable variant {} from cart of user {}", variantId, userId);
                continue;
            }

            String spuId = variant.getProduct().getId();
            String productName = variant.getProduct().getName();
            String variantName = variant.getVariantName();
            String creatorId = variant.getProduct().getCreatorId();
            String creatorName = creatorNameMap.getOrDefault(creatorId, creatorId);

            // Find thumbnail URL
            String thumbnailUrl = null;
            if (variant.getProduct().getImages() != null) {
                thumbnailUrl = variant.getProduct().getImages().stream()
                        .filter(ProductImage::isThumbnail)
                        .findFirst()
                        .map(ProductImage::getImageUrl)
                        .orElse(variant.getProduct().getImages().stream()
                                .findFirst()
                                .map(ProductImage::getImageUrl)
                                .orElse(null));
            }

            BigDecimal originalPrice = variant.getPrice();
            BigDecimal discountPrice = variant.getDiscountPrice() != null
                    ? variant.getDiscountPrice() : BigDecimal.ZERO;

            int availableStock = variant.getInventory() != null
                    ? variant.getInventory().getQuantity() - variant.getInventory().getReservedQuantity()
                    : 0;

            // Determine status
            String status;
            if (availableStock <= 0) {
                status = "OUT_OF_STOCK";
            } else if (availableStock < quantity) {
                status = "INSUFFICIENT_STOCK";
            } else {
                status = "AVAILABLE";
            }

            BigDecimal effectivePrice = discountPrice.compareTo(BigDecimal.ZERO) > 0
                    ? discountPrice : originalPrice;
            BigDecimal lineOriginal = originalPrice.multiply(BigDecimal.valueOf(quantity));
            BigDecimal lineDiscount = effectivePrice.multiply(BigDecimal.valueOf(quantity));

            totalOriginalAmount = totalOriginalAmount.add(lineOriginal);
            totalDiscountAmount = totalDiscountAmount.add(lineDiscount);

            items.add(new CartItemResponse(
                    variantId, spuId, productName, variantName,
                    thumbnailUrl, creatorId, creatorName,
                    quantity, originalPrice, discountPrice,
                    availableStock, status
            ));
        }

        // Clean up removed variants from Redis
        if (!variantIdsToRemove.isEmpty()) {
            redisTemplate.opsForHash().delete(cartKey, variantIdsToRemove.toArray());
        }

        BigDecimal totalSavingAmount = totalOriginalAmount.subtract(totalDiscountAmount);

        return new CartResponse(items, totalOriginalAmount, totalDiscountAmount, totalSavingAmount);
    }

    @Override
    public void addItem(String userId, String variantId, int quantity) {
        log.info("Adding variant {} to cart of user {}, quantity: {}", variantId, userId, quantity);

        // Validate variant exists and is ACTIVE
        ProductVariant variant = productVariantRepository.findById(variantId)
                .filter(v -> v.getStatus() == ProductStatus.ACTIVE)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        // Check available stock
        if (variant.getInventory() != null) {
            int availableStock = variant.getInventory().getQuantity()
                    - variant.getInventory().getReservedQuantity();
            if (availableStock <= 0) {
                throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
            }
        }

        String cartKey = CART_KEY_PREFIX + userId;

        // Get current quantity from Redis
        Object existingValue = redisTemplate.opsForHash().get(cartKey, variantId);
        int currentQty = existingValue != null ? Integer.parseInt(existingValue.toString()) : 0;
        int newQty = currentQty + quantity;

        if (newQty > MAX_CART_QUANTITY) {
            throw new AppException(ErrorCode.CART_QUANTITY_EXCEEDED);
        }

        redisTemplate.opsForHash().put(cartKey, variantId, String.valueOf(newQty));
        redisTemplate.expire(cartKey, CART_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void updateItemQuantity(String userId, String variantId, int quantity) {
        log.info("Updating variant {} in cart of user {} to quantity: {}", variantId, userId, quantity);

        String cartKey = CART_KEY_PREFIX + userId;

        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(cartKey, variantId);
            return;
        }

        if (quantity > MAX_CART_QUANTITY) {
            throw new AppException(ErrorCode.CART_QUANTITY_EXCEEDED);
        }

        redisTemplate.opsForHash().put(cartKey, variantId, String.valueOf(quantity));
        redisTemplate.expire(cartKey, CART_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void removeItem(String userId, String variantId) {
        log.info("Removing variant {} from cart of user {}", variantId, userId);
        String cartKey = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().delete(cartKey, variantId);
    }

    @Override
    public void mergeCart(String userId, CartMergeRequest request) {
        log.info("Merging {} items into cart of user {}", request.items().size(), userId);

        String cartKey = CART_KEY_PREFIX + userId;

        for (CartMergeRequest.CartMergeItem item : request.items()) {
            Object existingValue = redisTemplate.opsForHash().get(cartKey, item.variantId());
            int existingQty = existingValue != null ? Integer.parseInt(existingValue.toString()) : 0;
            int newQty = existingQty + item.quantity();

            // Silently cap at MAX_CART_QUANTITY
            newQty = Math.min(newQty, MAX_CART_QUANTITY);

            redisTemplate.opsForHash().put(cartKey, item.variantId(), String.valueOf(newQty));
        }

        redisTemplate.expire(cartKey, CART_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public void removeItems(String userId, List<String> variantIds) {
        if (variantIds == null || variantIds.isEmpty()) {
            return;
        }
        log.info("Removing {} items from cart of user {}", variantIds.size(), userId);
        String cartKey = CART_KEY_PREFIX + userId;
        redisTemplate.opsForHash().delete(cartKey, variantIds.toArray());
    }

    @Override
    public void clearCart(String userId) {
        log.info("Clearing cart for user: {}", userId);
        String cartKey = CART_KEY_PREFIX + userId;
        redisTemplate.delete(cartKey);
    }
}
