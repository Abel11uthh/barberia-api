package com.barberia.api.appointments.dto;
import java.util.UUID;
public record StaffAppointmentResponse(AppointmentResponse appointment, Customer customer, UUID createdBy, UUID cancelledBy) {
    public record Customer(String name, String phone, boolean guest) { }
}
