package com.barberia.api.appointments.dto;

import jakarta.validation.constraints.*;
import java.util.*;

public record WalkInRequest(@NotNull UUID barberId, @NotBlank @Size(max=120) String guestName,
                            @NotBlank @Size(max=32) String guestPhone, @NotNull UUID primaryServiceId,
                            List<@NotNull UUID> addOnIds) {
    public WalkInRequest {
        guestName=guestName==null ? null : guestName.strip();
        guestPhone=guestPhone==null ? null : guestPhone.strip();
        addOnIds=addOnIds==null ? List.of() : Collections.unmodifiableList(new ArrayList<>(addOnIds));
    }
    @Override public String toString() { return "WalkInRequest[REDACTED]"; }
}
