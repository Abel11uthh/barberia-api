package com.barberia.api.appointments.service;

import com.barberia.api.branches.dto.HoursRequest;
import com.barberia.api.appointments.dto.SlotResponse;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class SlotRules {
    public static final ZoneId ZONE = ZoneId.of("America/Mexico_City");
    private final Clock clock;
    public SlotRules(Clock clock) { this.clock = clock; }

    public Instant start(LocalDate date, LocalTime time) {
        if (time.getMinute() != 0 || time.getSecond() != 0 || time.getNano() != 0)
            throw new IllegalArgumentException("Se requiere una hora exacta.");
        var local = date.atTime(time);
        var offsets = ZONE.getRules().getValidOffsets(local);
        if (offsets.size() != 1) throw new IllegalArgumentException("Hora local ambigua o inexistente.");
        return local.toInstant(offsets.getFirst());
    }

    public List<SlotResponse> candidates(LocalDate date, HoursRequest hours) {
        if (hours.closed()) return List.of();
        List<SlotResponse> result = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            var local = date.atTime(hour, 0);
            var end = local.plusHours(1);
            if (local.isBefore(date.atTime(hours.opensAt())) || end.isAfter(date.atTime(hours.closesAt()))) continue;
            if (hours.breakStart() != null && local.isBefore(date.atTime(hours.breakEnd()))
                    && end.isAfter(date.atTime(hours.breakStart()))) continue;
            if (ZONE.getRules().getValidOffsets(local).size() != 1
                    || ZONE.getRules().getValidOffsets(end).size() != 1) continue;
            Instant instant = start(date, local.toLocalTime());
            Instant endInstant = end.atZone(ZONE).toInstant();
            if (!instant.isBefore(clock.instant()) && Duration.between(instant, endInstant).equals(Duration.ofHours(1)))
                result.add(new SlotResponse(instant, endInstant));
        }
        return List.copyOf(result);
    }
    public static boolean overlaps(Instant start, Instant end, Instant otherStart, Instant otherEnd) {
        return start.isBefore(otherEnd) && end.isAfter(otherStart);
    }
}
