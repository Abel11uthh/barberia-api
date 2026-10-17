package com.barberia.api.accounts.service;

import com.barberia.api.accounts.dto.BookingStatusResponse;
import com.barberia.api.accounts.dto.ReactivateRequest;
import com.barberia.api.accounts.entity.Account;
import com.barberia.api.accounts.entity.AppointmentClientEvent;
import com.barberia.api.accounts.entity.Role;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.accounts.repository.AppointmentClientEventRepository;
import com.barberia.api.shared.error.BusinessConflictException;
import com.barberia.api.shared.error.NotFoundException;
import java.time.Clock;
import java.util.UUID;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

@Service
public class ClientIncidentService {
    private final AccountRepository accounts;
    private final AppointmentClientEventRepository events;
    private final Clock clock;

    public ClientIncidentService(AccountRepository accounts, AppointmentClientEventRepository events, Clock clock) {
        this.accounts = accounts;
        this.events = events;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('CLIENT')")
    public BookingStatusResponse ownStatus(UUID accountId) {
        return status(accounts.findById(accountId).orElseThrow(NotFoundException::new));
    }

    // Shared commercial gate for reservations and a future purchase service.
    // Called within the caller's transaction. A shared row lock allows
    // concurrent reservations but excludes incident/reactivation updates.
    @Transactional(propagation = Propagation.MANDATORY)
    public Account requireCommercialAccess(UUID accountId) {
        Account account = accounts.findForBooking(accountId)
                .orElseThrow(() -> new BadCredentialsException("Cuenta no disponible."));
        if (!account.isActive()) throw new BadCredentialsException("Cuenta no disponible.");
        if (account.isCommercialBlocked())
            throw new BusinessConflictException("La cuenta está bloqueada para nuevas reservas y compras.");
        return account;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public BookingStatusResponse record(UUID accountId, UUID appointmentId, UUID actorId, String kind) {
        if (accountId == null) return null; // Los invitados no tienen contador.
        if (!kind.equals("CLIENT_CANCELLED") && !kind.equals("NO_SHOW") && !kind.equals("COMPLETED"))
            throw new IllegalArgumentException("Evento no admitido.");
        Account account = accounts.findForUpdate(accountId).orElseThrow(NotFoundException::new);
        if (kind.equals("COMPLETED")) account.resetAppointmentIncidents();
        else account.addAppointmentIncident();
        long sequence = account.nextAppointmentEventSequence();
        events.save(new AppointmentClientEvent(accountId, sequence, kind, appointmentId, actorId,
                clock.instant(), null, account.getAppointmentIncidentCount(), account.isCommercialBlocked()));
        return status(account);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BookingStatusResponse reactivate(UUID actorId, UUID accountId, ReactivateRequest request) {
        if (actorId.equals(accountId)) throw new AccessDeniedException("No se permite reactivación propia.");
        Account account = accounts.findForUpdate(accountId).orElseThrow(NotFoundException::new);
        if (!account.getRoles().contains(Role.CLIENT)) throw new NotFoundException();
        if (!account.isCommercialBlocked()) throw new BusinessConflictException("La cuenta no está bloqueada.");
        account.reactivateCommercialAccess();
        long sequence = account.nextAppointmentEventSequence();
        events.save(new AppointmentClientEvent(accountId, sequence, "ADMIN_REACTIVATED", null, actorId,
                clock.instant(), request.reason(), 0, false));
        return status(account);
    }

    private BookingStatusResponse status(Account account) {
        int count = account.getAppointmentIncidentCount();
        String warning = count == 2 && !account.isCommercialBlocked()
                ? "Advertencia: una incidencia adicional bloqueará nuevas reservas y compras." : null;
        return new BookingStatusResponse(account.isCommercialBlocked(), count, warning);
    }
}
