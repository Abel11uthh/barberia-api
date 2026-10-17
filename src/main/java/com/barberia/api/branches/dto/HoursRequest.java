package com.barberia.api.branches.dto;

import jakarta.validation.constraints.NotNull;
import java.time.DayOfWeek;
import java.time.LocalTime;

public record HoursRequest(@NotNull DayOfWeek day, @NotNull Boolean closed,
                           LocalTime opensAt, LocalTime closesAt, LocalTime breakStart, LocalTime breakEnd) { }
