package com.barberia.api.products.dto;

import com.barberia.api.products.entity.InventoryMovement;
import java.time.Instant;
import java.util.UUID;

public record MovementResponse(UUID id, UUID productId, UUID branchId, UUID actorId, String kind,
                               long delta, long quantityBefore, long quantityAfter, String reason, Instant occurredAt) {
    public static MovementResponse from(InventoryMovement movement) {
        return new MovementResponse(movement.getId(), movement.getProductId(), movement.getBranchId(),
                movement.getActorId(), movement.getKind(), movement.getDelta(), movement.getQuantityBefore(),
                movement.getQuantityAfter(), movement.getReason(), movement.getOccurredAt());
    }
}
