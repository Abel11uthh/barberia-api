package com.barberia.api.appointments.service;
import com.barberia.api.appointments.repository.AppointmentRepository;
import com.barberia.api.shared.error.BusinessConflictException;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class AgendaAvailability {
    private final AppointmentRepository appointments;
    public AgendaAvailability(AppointmentRepository appointments) { this.appointments=appointments; }
    public void requireFree(UUID barber, Instant start, Instant end) {
        if(!appointments.occupied(barber,start,end).isEmpty())
            throw new BusinessConflictException("El horario no está disponible.");
    }
}
