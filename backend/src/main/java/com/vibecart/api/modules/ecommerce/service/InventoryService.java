package com.vibecart.api.modules.ecommerce.service;

public interface InventoryService {
    void importStock(String variantId, int quantity, String reason, String userId);
    void exportStock(String variantId, int quantity, String reason, String userId);
    void refundStock(String variantId, int quantity, String reason, String userId);
    void reserveStock(String variantId, int quantity, String userId);
    void commitStock(String variantId, int quantity, String userId);
    void releaseStock(String variantId, int quantity, String userId);
    void adjustStock(String variantId, int adjustment, String reason, String userId);
}
