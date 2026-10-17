package com.barberia.api.branches.service;

import com.barberia.api.branches.dto.HoursRequest;
import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;

public final class WeeklyHoursRules {
    private WeeklyHoursRules() { }
    public static void validate(List<HoursRequest> hours) {
        if (hours == null || hours.size() != 7) { throw new IllegalArgumentException(); }
        var days = EnumSet.noneOf(DayOfWeek.class);
        for (var h : hours) {
            if (h == null || h.day() == null || h.closed() == null || !days.add(h.day())) { throw new IllegalArgumentException(); }
            if (h.closed()) {
                if (h.opensAt() != null || h.closesAt() != null || h.breakStart() != null || h.breakEnd() != null) { throw new IllegalArgumentException(); }
            } else {
                if (h.opensAt() == null || h.closesAt() == null || !h.opensAt().isBefore(h.closesAt())) { throw new IllegalArgumentException(); }
                if (h.breakStart() != null || h.breakEnd() != null) {
                    if (h.breakStart() == null || h.breakEnd() == null || !h.breakStart().isBefore(h.breakEnd())
                            || h.breakStart().isBefore(h.opensAt()) || h.breakEnd().isAfter(h.closesAt())) { throw new IllegalArgumentException(); }
                }
            }
        }
    }
}
