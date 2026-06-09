package com.vibecart.api.modules.ecommerce.service.impl;

import com.vibecart.api.common.exception.AppException;
import com.vibecart.api.common.exception.ErrorCode;
import com.vibecart.api.modules.ecommerce.entity.Inventory;
import com.vibecart.api.modules.ecommerce.entity.InventoryHistory;
import com.vibecart.api.modules.ecommerce.enums.TransactionType;
import com.vibecart.api.modules.ecommerce.repository.InventoryHistoryRepository;
import com.vibecart.api.modules.ecommerce.repository.InventoryRepository;
import com.vibecart.api.modules.ecommerce.service.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Implementation của {@link InventoryService} quản lý tồn kho sản phẩm.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryServiceImpl implements InventoryService {

    private final InventoryRepository inventoryRepository;
    private final InventoryHistoryRepository inventoryHistoryRepository;

    @Override
    @Transactional
    public void importStock(String variantId, int quantity, String reason, String userId) {
        log.info("Importing stock for variant {}: +{}, reason: {}", variantId, quantity, reason);

        int rowsUpdated = inventoryRepository.importStock(variantId, quantity);
        if (rowsUpdated == 0) {
            log.error("Import stock failed for variant: {}", variantId);
            throw new AppException(ErrorCode.INVENTORY_ADJUST_FAILED);
        }

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        saveHistory(inventory, TransactionType.IMPORT, quantity, reason, userId);
    }

    @Override
    @Transactional
    public void exportStock(String variantId, int quantity, String reason, String userId) {
        log.info("Exporting stock for variant {}: -{}, reason: {}", variantId, quantity, reason);

        int rowsUpdated = inventoryRepository.exportStock(variantId, quantity);
        if (rowsUpdated == 0) {
            log.error("Export stock failed for variant: {} (insufficient physical stock)", variantId);
            throw new AppException(ErrorCode.INVENTORY_ADJUST_FAILED);
        }

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        saveHistory(inventory, TransactionType.EXPORT, -quantity, reason, userId);
    }

    @Override
    @Transactional
    public void refundStock(String variantId, int quantity, String reason, String userId) {
        log.info("Refunding stock for variant {}: +{}, reason: {}", variantId, quantity, reason);

        int rowsUpdated = inventoryRepository.importStock(variantId, quantity);
        if (rowsUpdated == 0) {
            log.error("Refund stock failed for variant: {}", variantId);
            throw new AppException(ErrorCode.INVENTORY_ADJUST_FAILED);
        }

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        saveHistory(inventory, TransactionType.REFUND, quantity, reason, userId);
    }

    @Override
    @Transactional
    public void reserveStock(String variantId, int quantity, String userId) {
        log.info("Reserving stock for variant {}: {}", variantId, quantity);

        int rowsUpdated = inventoryRepository.reserveStock(variantId, quantity);
        if (rowsUpdated == 0) {
            log.warn("Insufficient stock to reserve for variant: {} (requested: {})", variantId, quantity);
            throw new AppException(ErrorCode.INSUFFICIENT_STOCK);
        }

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        saveHistory(inventory, TransactionType.RESERVE, -quantity,
                "Stock reserved for order", userId);
    }

    @Override
    @Transactional
    public void commitStock(String variantId, int quantity, String userId) {
        log.info("Committing stock for variant {}: {}", variantId, quantity);

        int rowsUpdated = inventoryRepository.commitStock(variantId, quantity);
        if (rowsUpdated == 0) {
            log.error("Commit stock failed for variant: {}", variantId);
            throw new AppException(ErrorCode.INVENTORY_ADJUST_FAILED);
        }

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        saveHistory(inventory, TransactionType.COMMIT, -quantity,
                "Stock committed after payment", userId);
    }

    @Override
    @Transactional
    public void releaseStock(String variantId, int quantity, String userId) {
        log.info("Releasing stock for variant {}: +{}", variantId, quantity);

        int rowsUpdated = inventoryRepository.releaseStock(variantId, quantity);
        if (rowsUpdated == 0) {
            log.error("Release stock failed for variant: {}", variantId);
            throw new AppException(ErrorCode.INVENTORY_ADJUST_FAILED);
        }

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        saveHistory(inventory, TransactionType.RELEASE, quantity,
                "Stock released (order cancelled/expired)", userId);
    }

    @Override
    @Transactional
    public void adjustStock(String variantId, int adjustment, String reason, String userId) {
        log.info("Adjusting stock for variant {}: {}, reason: {}", variantId, adjustment, reason);

        int rowsUpdated = inventoryRepository.adjustStock(variantId, adjustment);
        if (rowsUpdated == 0) {
            log.warn("Adjust stock failed for variant: {} (adjustment: {})", variantId, adjustment);
            throw new AppException(ErrorCode.INVENTORY_ADJUST_FAILED);
        }

        Inventory inventory = inventoryRepository.findByVariantId(variantId)
                .orElseThrow(() -> new AppException(ErrorCode.PRODUCT_NOT_FOUND));

        saveHistory(inventory, TransactionType.ADJUST, adjustment, reason, userId);
    }

    private void saveHistory(Inventory inventory, TransactionType type,
                             int quantityChanged, String reason, String createdBy) {
        InventoryHistory history = InventoryHistory.builder()
                .inventory(inventory)
                .transactionType(type)
                .quantityChanged(quantityChanged)
                .reason(reason)
                .createdBy(createdBy)
                .build();
        inventoryHistoryRepository.save(history);
        log.debug("Inventory history saved: type={}, qty={}, inventoryId={}",
                type, quantityChanged, inventory.getId());
    }
}
