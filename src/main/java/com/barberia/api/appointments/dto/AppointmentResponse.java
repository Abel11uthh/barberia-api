package com.barberia.api.appointments.dto;

import com.barberia.api.appointments.entity.Appointment;
import com.barberia.api.services.entity.ServiceType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

public record AppointmentResponse(UUID id, UUID barberId, UUID branchId, Instant startsAt,
                                  Instant endsAt, String timeZone, String status,
                                  List<ServiceSnapshot> services, BigDecimal total, String currency) {
    public record ServiceSnapshot(UUID serviceId, String name, ServiceType type, BigDecimal price) { }
    public static AppointmentResponse from(Appointment appointment) {
        return new AppointmentResponse(appointment.getId(), appointment.getBarberId(), appointment.getBranchId(),
                appointment.getStartsAt(), appointment.getEndsAt(), "America/Mexico_City", appointment.getStatus(),
                appointment.getServices().stream().map(s -> new ServiceSnapshot(s.getServiceId(), s.getName(),
                        s.getType(), s.getPrice())).toList(), appointment.getTotal(), "MXN");
    }
}
