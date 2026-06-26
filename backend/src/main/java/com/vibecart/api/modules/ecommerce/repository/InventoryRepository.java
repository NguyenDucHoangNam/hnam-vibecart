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
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity + :qty " +
            "WHERE i.variant.id = :variantId AND (i.quantity - i.reservedQuantity) >= :qty")
    int reserveStock(@Param("variantId") String variantId, @Param("qty") int qty);
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty, i.reservedQuantity = i.reservedQuantity - :qty " +
            "WHERE i.variant.id = :variantId AND i.reservedQuantity >= :qty")
    int commitStock(@Param("variantId") String variantId, @Param("qty") int qty);
    @Modifying
    @Query("UPDATE Inventory i SET i.reservedQuantity = i.reservedQuantity - :qty " +
            "WHERE i.variant.id = :variantId AND i.reservedQuantity >= :qty")
    int releaseStock(@Param("variantId") String variantId, @Param("qty") int qty);
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :qty WHERE i.variant.id = :variantId")
    int importStock(@Param("variantId") String variantId, @Param("qty") int qty);
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity - :qty " +
            "WHERE i.variant.id = :variantId AND i.quantity >= :qty")
    int exportStock(@Param("variantId") String variantId, @Param("qty") int qty);
    @Modifying
    @Query("UPDATE Inventory i SET i.quantity = i.quantity + :adjustment " +
            "WHERE i.variant.id = :variantId AND i.quantity + :adjustment >= 0")
    int adjustStock(@Param("variantId") String variantId, @Param("adjustment") int adjustment);
}
