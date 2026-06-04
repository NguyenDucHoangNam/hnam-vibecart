package com.vibecart.api.modules.ecommerce.service;

import com.vibecart.api.modules.ecommerce.dto.request.CartMergeRequest;
import com.vibecart.api.modules.ecommerce.dto.response.CartResponse;
import java.util.List;

public interface CartService {
    CartResponse getCart(String userId);
    void addItem(String userId, String variantId, int quantity);
    void updateItemQuantity(String userId, String variantId, int quantity);
    void removeItem(String userId, String variantId);
    void mergeCart(String userId, CartMergeRequest request);
    void removeItems(String userId, List<String> variantIds);
    void clearCart(String userId);
}
