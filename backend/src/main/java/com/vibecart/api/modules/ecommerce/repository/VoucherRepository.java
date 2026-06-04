package com.vibecart.api.modules.ecommerce.repository;

import com.vibecart.api.modules.ecommerce.entity.Voucher;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface VoucherRepository extends JpaRepository<Voucher, String> {

    Optional<Voucher> findByCode(String code);

    /**
     * Atomic increment of used_count with limit check.
     */
    @Modifying
    @Query("UPDATE Voucher v SET v.usedCount = v.usedCount + 1 WHERE v.id = :id AND v.usedCount < v.usageLimit")
    int incrementUsedCount(@Param("id") String id);
}
