package com.barberia.api.appointments.dto;

import com.barberia.api.appointments.entity.Appointment;
import com.barberia.api.accounts.dto.BookingStatusResponse;
import com.barberia.api.services.entity.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record AppointmentResponse(UUID id, UUID barberId, UUID branchId, Instant startsAt,
                                  Instant endsAt, String timeZone, String status,
                                  List<ServiceSnapshot> services, BigDecimal total, String currency,
                                  String origin, Instant startedAt, Instant completedAt, Instant noShowAt,
                                  Instant cancelledAt, String cancellationReason, String cancellationOrigin,
                                  BookingStatusResponse bookingStatus) {
    public record ServiceSnapshot(UUID serviceId, String name, ServiceType type, BigDecimal price) { }
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getBarberId(), appointment.getBranchId(),
                appointment.getStartsAt(), appointment.getEndsAt(), "America/Mexico_City", appointment.getStatus(),
                appointment.getServices().stream().map(s -> new ServiceSnapshot(s.getServiceId(), s.getName(),
                        s.getType(), s.getPrice())).toList(), appointment.getTotal(), "MXN",
                appointment.getOrigin(), appointment.getStartedAt(), appointment.getCompletedAt(), appointment.getNoShowAt(),
                appointment.getCancelledAt(), appointment.getCancellationReason(), appointment.getCancellationOrigin(), null);
    }
    public static AppointmentResponse from(Appointment appointment, BookingStatusResponse bookingStatus) {
        var response = from(appointment);
        return new AppointmentResponse(response.id(), response.barberId(), response.branchId(), response.startsAt(),
                response.endsAt(), response.timeZone(), response.status(), response.services(), response.total(),
                response.currency(), response.origin(), response.startedAt(), response.completedAt(), response.noShowAt(),
                response.cancelledAt(), response.cancellationReason(), response.cancellationOrigin(), bookingStatus);
    }
}
