package com.barberia.api.appointments.dto;

import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;

public record BookingRequest(@NotNull UUID barberId, @NotNull LocalDate date,
                             @NotNull LocalTime time, @NotNull UUID primaryServiceId,
                             List<@NotNull UUID> addOnIds) {
    public BookingRequest {
        addOnIds = addOnIds == null ? List.of() : Collections.unmodifiableList(new ArrayList<>(addOnIds));
    }
}
