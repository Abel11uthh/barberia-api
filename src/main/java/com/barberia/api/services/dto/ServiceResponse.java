package com.barberia.api.services.dto;

import com.barberia.api.services.entity.CatalogService;
import com.barberia.api.services.entity.ServiceType;
import java.math.BigDecimal;
import java.util.UUID;

public record ServiceResponse(UUID id, String name, String description, BigDecimal price, String currency,
                              ServiceType type, boolean active) {
    public static ServiceResponse from(CatalogService service) {
        return new ServiceResponse(service.getId(), service.getName(), service.getDescription(), service.getPrice(),
                "MXN", service.getType(), service.isActive());
    }
}
