package com.barberia.api.barbers.dto;

import com.barberia.api.auth.dto.RegisterRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record CreateBarberRequest(@NotNull @Valid RegisterRequest account, @NotNull UUID branchId) {
    @Override public String toString() { return "CreateBarberRequest[REDACTED]"; }
}
