package com.barberia.api.payments.repository;
import com.barberia.api.payments.entity.AppointmentCharge;
import java.util.*;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ChargeRepository extends JpaRepository<AppointmentCharge,UUID> {
    Optional<AppointmentCharge> findByAppointmentId(UUID appointmentId);
}
