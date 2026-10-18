package com.barberia.api.sales.entity;
import com.barberia.api.products.entity.Product;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;
@Embeddable
public class SaleLine {
    @Column(nullable=false, updatable=false) private UUID productId;
    @Column(nullable=false, updatable=false, length=120) private String name;
    @Column(nullable=false, updatable=false, length=64) private String sku;
    @Column(nullable=false, updatable=false, precision=12, scale=2) private BigDecimal unitPrice;
    @Column(nullable=false, updatable=false) private long quantity;
    protected SaleLine() { }
    public SaleLine(Product product, long quantity) {
        productId=product.getId(); name=product.getName(); sku=product.getSku(); unitPrice=product.getPrice(); this.quantity=quantity;
    }
    public UUID getProductId() { return productId; }
    public String getName() { return name; }
    public String getSku() { return sku; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public long getQuantity() { return quantity; }
    public BigDecimal total() { return unitPrice.multiply(BigDecimal.valueOf(quantity)); }
}
