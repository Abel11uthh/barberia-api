package com.barberia.api.payments.service;

import com.barberia.api.appointments.repository.AppointmentRepository;
import com.barberia.api.payments.dto.*;
import com.barberia.api.payments.entity.AppointmentCharge;
import com.barberia.api.payments.repository.ChargeRepository;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.security.StaffAccess;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeService {
    private final AppointmentRepository appointments;
    private final ChargeRepository charges;
    private final StaffAccess access;
    private final Clock clock;
    public ChargeService(AppointmentRepository appointments, ChargeRepository charges, StaffAccess access, Clock clock) {
        this.appointments=appointments; this.charges=charges; this.access=access; this.clock=clock;
    }
    @Transactional
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public ChargeResponse charge(UUID actor, UUID id, ChargeRequest request) {
        var appointment=appointments.findForUpdate(id).orElseThrow(NotFoundException::new);
        access.appointment(actor,appointment,true);
        if (!appointment.getStatus().equals("COMPLETED"))
            throw new BusinessConflictException("Solo se cobran citas completadas.");
        if (charges.findByAppointmentId(id).isPresent()) throw new BusinessConflictException("La cita ya está cobrada.");
        var charge=new AppointmentCharge(id,appointment.getBranchId(),actor,appointment.getTotal(),request.method(),clock.instant());
        return ChargeResponse.from(charges.saveAndFlush(charge));
    }
    @Transactional
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public ChargeResponse detail(UUID actor, UUID id) {
        var appointment=appointments.findById(id).orElseThrow(NotFoundException::new);
        access.appointment(actor,appointment,false);
        return ChargeResponse.from(charges.findByAppointmentId(id).orElseThrow(NotFoundException::new));
    }
    @Transactional(readOnly=true)
    @PreAuthorize("hasRole('CLIENT')")
    public OwnPaymentResponse own(UUID actor, UUID id) {
        appointments.findByIdAndClientId(id,actor).orElseThrow(NotFoundException::new);
        return charges.findByAppointmentId(id).map(charge -> new OwnPaymentResponse(true,charge.getAmount(),"MXN",charge.getPaidAt()))
                .orElseGet(() -> new OwnPaymentResponse(false,null,"MXN",null));
    }
}
