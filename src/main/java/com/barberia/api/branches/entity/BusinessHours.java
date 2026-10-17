package com.barberia.api.branches.entity;

import com.barberia.api.branches.dto.HoursRequest;
import jakarta.persistence.*;
import java.time.DayOfWeek;
import java.time.LocalTime;

@Embeddable
public class BusinessHours {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 9)
    private DayOfWeek day;
    @Column(nullable = false)
    private boolean closed;
    private LocalTime opensAt;
    private LocalTime closesAt;
    private LocalTime breakStart;
    private LocalTime breakEnd;

    protected BusinessHours() { }
    public BusinessHours(HoursRequest input) {
        day = input.day(); closed = input.closed(); opensAt = input.opensAt(); closesAt = input.closesAt();
        breakStart = input.breakStart(); breakEnd = input.breakEnd();
    }
    public DayOfWeek getDay() { return day; }
    public HoursRequest toResponse() { return new HoursRequest(day, closed, opensAt, closesAt, breakStart, breakEnd); }
}
