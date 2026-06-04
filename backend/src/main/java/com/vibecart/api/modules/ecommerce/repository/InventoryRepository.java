package com.vibecart.api.modules.ecommerce.repository;

import com.vibecart.api.modules.ecommerce.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, String> {

    Optional<Inventory> findByVariantId(String variantId);

    /**
     * Atomic reserve: increment reserved_quantity if available stock is sufficient.
     * Available stock = quantity - reserved_quantity
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity + :qty " +
            "WHERE i.variant.id = :variantId AND (i.quantity - i.reservedQuantity) >= :qty")
    int reserveStock(@Param("variantId") String variantId, @Param("qty") int qty);

    /**
     * Atomic commit: decrement both quantity and reserved_quantity (order paid).
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty, i.reservedQuantity = i.reservedQuantity - :qty " +
            "WHERE i.variant.id = :variantId AND i.reservedQuantity >= :qty")
    int commitStock(@Param("variantId") String variantId, @Param("qty") int qty);

    /**
     * Atomic release: decrement reserved_quantity (order cancelled from PENDING).
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity - :qty " +
            "WHERE i.variant.id = :variantId AND i.reservedQuantity >= :qty")
    int releaseStock(@Param("variantId") String variantId, @Param("qty") int qty);

    /**
     * Atomic import: increment quantity (restock or refund).
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :qty WHERE i.variant.id = :variantId")
    int importStock(@Param("variantId") String variantId, @Param("qty") int qty);

    /**
     * Atomic export: decrement quantity (Creator exports damaged/lost goods).
     * Safety check: quantity must remain >= 0 after export.
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty " +
            "WHERE i.variant.id = :variantId AND i.quantity >= :qty")
    int exportStock(@Param("variantId") String variantId, @Param("qty") int qty);

    /**
     * Admin adjustment with safety check.
     */
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :adjustment " +
            "WHERE i.variant.id = :variantId AND i.quantity + :adjustment >= 0")
    int adjustStock(@Param("variantId") String variantId, @Param("adjustment") int adjustment);
}
