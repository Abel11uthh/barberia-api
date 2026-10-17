package com.barberia.api.appointments.entity;

import com.barberia.api.services.entity.CatalogService;
import com.barberia.api.services.entity.ServiceType;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Embeddable
public class BookedService {
    @Column(nullable = false) private UUID serviceId;
    @Column(nullable = false, length = 120) private String name;
    @Column(nullable = false, precision = 12, scale = 2) private BigDecimal price;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 7) private ServiceType type;

    protected BookedService() { }
    public BookedService(CatalogService service) {
        serviceId = service.getId(); name = service.getName();
        price = service.getPrice(); type = service.getType();
    }
    public UUID getServiceId() { return serviceId; }
    public String getName() { return name; }
    public BigDecimal getPrice() { return price; }
    public ServiceType getType() { return type; }
}
