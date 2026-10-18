package com.barberia.api.shared.security;

import com.barberia.api.accounts.entity.Role;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.appointments.entity.Appointment;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.entity.Branch;
import com.barberia.api.branches.repository.BranchRepository;
import com.barberia.api.shared.error.*;
import java.util.UUID;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class StaffAccess {
    private final AccountRepository accounts;
    private final BarberRepository barbers;
    private final BranchRepository branches;
    public StaffAccess(AccountRepository accounts, BarberRepository barbers, BranchRepository branches) {
        this.accounts=accounts; this.barbers=barbers; this.branches=branches;
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Branch branch(UUID actor, UUID requested, boolean writing) {
        return authorize(actor, requested, null, writing);
    }
    @Transactional(propagation=Propagation.MANDATORY)
    public Branch appointment(UUID actor, Appointment appointment, boolean writing) {
        return authorize(actor, appointment.getBranchId(), appointment.getBarberId(), writing);
    }
    private Branch authorize(UUID actor, UUID requested, UUID appointmentBarber, boolean writing) {
        var account=accounts.findForBooking(actor).filter(a -> a.isActive())
                .orElseThrow(() -> new BadCredentialsException("Cuenta no disponible."));
        UUID branchId=requested;
        if (!account.getRoles().contains(Role.ADMIN)) {
            if (!account.getRoles().contains(Role.BARBER)) throw new AccessDeniedException("Acceso denegado.");
            var own=barbers.findByAccountId(actor).orElseThrow(NotFoundException::new);
            var profile=barbers.findForBooking(own.getId()).orElseThrow(NotFoundException::new);
            if (!profile.isActive()) throw new BusinessConflictException("El perfil está inactivo.");
            branchId=profile.getBranch().getId();
            if ((requested!=null && !requested.equals(branchId))
                    || (appointmentBarber!=null && !appointmentBarber.equals(profile.getId())))
                throw new NotFoundException();
        }
        if (branchId==null) throw new IllegalArgumentException("La sucursal es obligatoria.");
        var branch=branches.findForBooking(branchId).orElseThrow(NotFoundException::new);
        if (writing && !branch.isActive()) throw new BusinessConflictException("La sucursal está inactiva.");
        return branch;
    }
}
