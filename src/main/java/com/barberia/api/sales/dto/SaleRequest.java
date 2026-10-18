package com.barberia.api.sales.dto;
import com.barberia.api.shared.payment.PaymentMethod;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.util.*;
public record SaleRequest(UUID branchId, @NotNull PaymentMethod method,
        @NotEmpty @Size(max=100) List<@NotNull @Valid Item> items) {
    public record Item(@NotNull UUID productId, @NotNull @Positive Long quantity) { }
}
