package com.barberia.api.appointments.repository;

import com.barberia.api.appointments.entity.Appointment;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.*;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.repository.*;

public interface AppointmentRepository extends JpaRepository<Appointment, UUID> {
    Page<Appointment> findByClientId(UUID clientId, Pageable pageable);
    Optional<Appointment> findByIdAndClientId(UUID id, UUID clientId);
    interface OccupiedSlot { Instant getStartsAt(); Instant getEndsAt(); }
    @Query(value="select starts_at as startsAt, ends_at as endsAt from agenda_occupations where barber_id=:barberId and active and starts_at<:end and ends_at>:start", nativeQuery=true)
    List<OccupiedSlot> occupied(UUID barberId, Instant start, Instant end);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Appointment a where a.id=:id")
    Optional<Appointment> findForUpdate(UUID id);
    @Query("select a from Appointment a where (:barberId is null or a.barberId=:barberId) and (:branchId is null or a.branchId=:branchId) and a.startsAt<:end and a.endsAt>:start and (:status is null or a.status=:status)")
    Page<Appointment> agenda(UUID barberId, UUID branchId, Instant start, Instant end, String status, Pageable pageable);
}
