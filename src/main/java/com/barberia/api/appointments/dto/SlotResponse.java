package com.barberia.api.appointments.dto;
import java.time.Instant;
public record SlotResponse(Instant startsAt, Instant endsAt) { }
