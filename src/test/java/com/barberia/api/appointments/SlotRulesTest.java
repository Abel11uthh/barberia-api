package com.barberia.api.appointments;

import com.barberia.api.appointments.service.SlotRules;
import com.barberia.api.branches.dto.HoursRequest;
import java.time.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SlotRulesTest {
    private final LocalDate date = LocalDate.of(2030, 1, 7);
    private final SlotRules rules = new SlotRules(Clock.fixed(Instant.parse("2030-01-07T13:00:00Z"), ZoneOffset.UTC));
    private HoursRequest hours(int breakHour) {
        return new HoursRequest(DayOfWeek.MONDAY, false, LocalTime.of(8, 0), LocalTime.of(18, 0),
                LocalTime.of(breakHour, 0), LocalTime.of(breakHour + 1, 0));
    }
    @Test void principalBreakAndClosingUseHalfOpenIntervals() {
        var slots = rules.candidates(date, hours(10));
        assertThat(slots).hasSize(9);
        assertThat(slots.stream().map(s -> s.startsAt().atZone(SlotRules.ZONE).getHour()).toList())
                .contains(9, 11, 17).doesNotContain(10, 18);
        assertThat(slots.getLast().endsAt().atZone(SlotRules.ZONE).getHour()).isEqualTo(18);
    }
    @Test void secondBranchHasDifferentBreak() {
        assertThat(rules.candidates(date, hours(11)).stream()
                .map(s -> s.startsAt().atZone(SlotRules.ZONE).getHour()).toList()).contains(10, 12).doesNotContain(11);
    }
    @Test void closedDayHasNoSlots() {
        assertThat(rules.candidates(date, new HoursRequest(DayOfWeek.MONDAY, true, null, null, null, null))).isEmpty();
    }
    @Test void pastStartsExcludedAndExactNowAllowed() {
        var atNine = new SlotRules(Clock.fixed(Instant.parse("2030-01-07T15:00:00Z"), ZoneOffset.UTC));
        assertThat(atNine.candidates(date, hours(10)).getFirst().startsAt()).isEqualTo(Instant.parse("2030-01-07T15:00:00Z"));
        assertThat(atNine.candidates(date.minusDays(1), hours(10))).isEmpty();
    }
    @Test void partialOpeningClosingAndBreakExcludeWholeOverlappingBlocks() {
        var hours = new HoursRequest(DayOfWeek.MONDAY, false, LocalTime.of(8, 30), LocalTime.of(17, 30),
                LocalTime.of(10, 30), LocalTime.of(11, 30));
        assertThat(rules.candidates(date, hours).stream().map(s -> s.startsAt().atZone(SlotRules.ZONE).getHour()).toList())
                .containsExactly(9, 12, 13, 14, 15, 16);
    }
    @Test void nonExactHourRejectedAndLocalTimeConvertedUnambiguously() {
        assertThatThrownBy(() -> rules.start(date, LocalTime.of(9, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.start(date, LocalTime.of(9, 0, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> rules.start(date, LocalTime.of(9, 0, 0, 1))).isInstanceOf(IllegalArgumentException.class);
        assertThat(rules.start(date, LocalTime.of(9, 0))).isEqualTo(Instant.parse("2030-01-07T15:00:00Z"));
    }
    @Test void adjacentIntervalsDoNotOverlap() {
        Instant start = Instant.parse("2030-01-07T15:00:00Z");
        assertThat(SlotRules.overlaps(start, start.plusSeconds(3600), start.plusSeconds(3600), start.plusSeconds(7200))).isFalse();
        assertThat(SlotRules.overlaps(start, start.plusSeconds(3600), start.plusSeconds(3599), start.plusSeconds(7200))).isTrue();
    }
}
