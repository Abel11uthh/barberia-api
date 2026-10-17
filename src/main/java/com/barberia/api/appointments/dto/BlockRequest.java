package com.barberia.api.appointments.dto;
import jakarta.validation.constraints.*;
import java.time.Instant;
import java.util.UUID;
public record BlockRequest(@NotNull UUID barberId, @NotNull Instant startsAt, @NotNull Instant endsAt,
                           @NotBlank @Size(max=500) String reason) {
    public BlockRequest { reason=reason==null ? null : reason.strip(); }
}
