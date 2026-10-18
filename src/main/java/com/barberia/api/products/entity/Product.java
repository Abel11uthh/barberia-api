package com.barberia.api.products.entity;

import com.barberia.api.products.dto.ProductRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {
    @Id private UUID id;
    @Column(nullable = false, length = 64, unique = true) private String sku;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 1000) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Column(nullable = false) private boolean active;

    protected Product() { }
    public Product(ProductRequest request) { id = UUID.randomUUID(); active = true; update(request); }
    public void update(ProductRequest request) {
        sku = request.sku().strip().toUpperCase(Locale.ROOT);
        if (!sku.matches("[A-Z0-9][A-Z0-9._-]{0,63}")) throw new IllegalArgumentException("SKU inválido.");
        name = request.name().strip();
        description = request.description() == null ? null : request.description().strip();
        price = request.price().setScale(2, RoundingMode.UNNECESSARY);
    }
    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public String getSku() { return sku; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public boolean isActive() { return active; }
}
