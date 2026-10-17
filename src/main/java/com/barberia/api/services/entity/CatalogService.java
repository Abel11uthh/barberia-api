package com.barberia.api.services.entity;

import com.barberia.api.services.dto.ServiceRequest;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "catalog_services")
public class CatalogService {
    @Id private UUID id;
    @Column(nullable = false, length = 120) private String name;
    @Column(length = 1000) private String description;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 7) private ServiceType type;
    @Column(nullable = false) private boolean active;
    protected CatalogService() { }
    public CatalogService(ServiceRequest request) { id = UUID.randomUUID(); active = true; update(request); }
    public void update(ServiceRequest request) {
        name = request.name(); description = request.description(); price = request.price().setScale(2); type = request.type();
    }
    public void deactivate() { active = false; }
    public UUID getId() { return id; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public BigDecimal getPrice() { return price; }
    public ServiceType getType() { return type; }
    public boolean isActive() { return active; }
}
