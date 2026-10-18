package com.barberia.api.products.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "inventory_movements")
public class InventoryMovement {
    @Id private UUID id;
    @Column(nullable = false, updatable = false) private UUID productId;
    @Column(nullable = false, updatable = false) private UUID branchId;
    @Column(nullable = false, updatable = false) private UUID actorId;
    @Column(nullable = false, length = 10, updatable = false) private String kind;
    @Column(nullable = false, updatable = false) private long delta;
    @Column(nullable = false, updatable = false) private long quantityBefore;
    @Column(nullable = false, updatable = false) private long quantityAfter;
    @Column(nullable = false, length = 500, updatable = false) private String reason;
    @Column(nullable = false, updatable = false) private Instant occurredAt;

    protected InventoryMovement() { }
    public InventoryMovement(UUID productId, UUID branchId, UUID actorId, String kind, long delta,
                             long quantityBefore, long quantityAfter, String reason, Instant occurredAt) {
        id = UUID.randomUUID(); this.productId = productId; this.branchId = branchId; this.actorId = actorId;
        this.kind = kind; this.delta = delta; this.quantityBefore = quantityBefore;
        this.quantityAfter = quantityAfter; this.reason = reason; this.occurredAt = occurredAt;
    }
    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public UUID getBranchId() { return branchId; }
    public UUID getActorId() { return actorId; }
    public String getKind() { return kind; }
    public long getDelta() { return delta; }
    public long getQuantityBefore() { return quantityBefore; }
    public long getQuantityAfter() { return quantityAfter; }
    public String getReason() { return reason; }
    public Instant getOccurredAt() { return occurredAt; }
}
