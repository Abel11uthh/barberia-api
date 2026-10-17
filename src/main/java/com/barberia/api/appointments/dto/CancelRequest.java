package com.barberia.api.appointments.dto;
import jakarta.validation.constraints.Size;
public record CancelRequest(@Size(max=500) String reason) {
    public CancelRequest { reason=reason==null ? null : reason.strip(); }
}
