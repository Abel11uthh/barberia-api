package com.barberia.api.products.repository;

import com.barberia.api.products.entity.InventoryMovement;
import java.util.UUID;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface InventoryMovementRepository extends JpaRepository<InventoryMovement, UUID> {
    @Query("select m from InventoryMovement m where m.productId = :productId and (:branchId is null or m.branchId = :branchId)")
    Page<InventoryMovement> history(UUID productId, UUID branchId, Pageable pageable);
}
