package com.barberia.api.services.dto;

import com.barberia.api.services.entity.ServiceType;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ServiceRequest(@NotBlank @Size(max = 120) String name,
                             @Size(max = 1000) String description,
                             @NotNull @DecimalMin("0.01") @Digits(integer = 10, fraction = 2) BigDecimal price,
                             @NotNull ServiceType type) {
    public ServiceRequest { name = name == null ? null : name.strip(); }
}
