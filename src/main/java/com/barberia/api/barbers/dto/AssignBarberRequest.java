package com.barberia.api.barbers.dto;

import jakarta.validation.constraints.NotNull;
import java.util.UUID;

public record AssignBarberRequest(@NotNull UUID accountId, @NotNull UUID branchId) { }
