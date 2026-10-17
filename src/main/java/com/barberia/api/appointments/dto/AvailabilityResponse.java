package com.barberia.api.appointments.dto;
import java.time.LocalDate;
import java.util.*;
public record AvailabilityResponse(UUID branchId, UUID barberId, LocalDate date,
                                   String timeZone, List<SlotResponse> slots) { }
