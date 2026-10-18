package com.barberia.api.products.entity;

import java.io.Serializable;
import java.util.Objects;
import java.util.UUID;

public class ProductStockId implements Serializable {
    private UUID productId;
    private UUID branchId;

    public ProductStockId() { }
    public ProductStockId(UUID productId, UUID branchId) {
        this.productId = productId; this.branchId = branchId;
    }
    public UUID getProductId() { return productId; }
    public UUID getBranchId() { return branchId; }
    @Override public boolean equals(Object value) {
        return value instanceof ProductStockId other
                && Objects.equals(productId, other.productId) && Objects.equals(branchId, other.branchId);
    }
    @Override public int hashCode() { return Objects.hash(productId, branchId); }
}
