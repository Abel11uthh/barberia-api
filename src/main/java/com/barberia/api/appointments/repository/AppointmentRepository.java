package com.barberia.api.appointments.repository;

import com.barberia.api.appointments.entity.Appointment;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    Page<Appointment> findByClientId(UUID clientId, Pageable pageable);
    Optional<Appointment> findByIdAndClientId(UUID id, UUID clientId);
    interface OccupiedSlot { Instant getStartsAt(); Instant getEndsAt(); }
    @Query("select a.startsAt as startsAt, a.endsAt as endsAt from Appointment a where a.barberId = :barberId and a.status = 'CONFIRMED' and a.startsAt < :end and a.endsAt > :start")
    List<OccupiedSlot> occupied(UUID barberId, Instant start, Instant end);
}
