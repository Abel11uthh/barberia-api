package com.barberia.api.products.dto;

import jakarta.validation.constraints.*;
import java.util.UUID;

public record MovementRequest(@NotNull UUID branchId, @NotNull Long quantity,
                              @NotBlank @Size(max = 500) String reason) { }
