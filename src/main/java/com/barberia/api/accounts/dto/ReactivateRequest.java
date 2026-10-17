package com.barberia.api.accounts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ReactivateRequest(@NotBlank @Size(max = 500) String reason) {
    public ReactivateRequest { reason = reason == null ? null : reason.strip(); }
}
