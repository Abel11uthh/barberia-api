package com.barberia.api.products.entity;

import jakarta.persistence.*;
import java.util.UUID;

@Entity
@Table(name = "product_stocks")
@IdClass(ProductStockId.class)
public class ProductStock {
    @Id private UUID productId;
    @Id private UUID branchId;
    @Column(nullable = false) private long quantity;

    protected ProductStock() { }
    public ProductStock(UUID productId, UUID branchId) {
        this.productId = productId; this.branchId = branchId;
    }
    public long getQuantity() { return quantity; }
    public UUID getBranchId() { return branchId; }
    public void setQuantity(long quantity) { this.quantity = quantity; }
}
