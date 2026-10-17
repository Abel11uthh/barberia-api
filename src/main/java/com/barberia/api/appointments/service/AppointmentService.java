package com.barberia.api.appointments.service;

import com.barberia.api.accounts.entity.Role;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.appointments.dto.*;
import com.barberia.api.appointments.entity.*;
import com.barberia.api.appointments.repository.AppointmentRepository;
import com.barberia.api.barbers.entity.BarberProfile;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.entity.Branch;
import com.barberia.api.branches.repository.BranchRepository;
import com.barberia.api.services.entity.CatalogService;
import com.barberia.api.services.entity.ServiceType;
import com.barberia.api.services.repository.ServiceRepository;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.pagination.*;
import java.sql.SQLException;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final BarberRepository barbers;
    private final BranchRepository branches;
    private final AccountRepository accounts;
    private final ServiceRepository services;
    private final SlotRules rules;
    private final Clock clock;

    public AppointmentService(AppointmentRepository appointments, BarberRepository barbers,
            BranchRepository branches, AccountRepository accounts, ServiceRepository services,
            SlotRules rules, Clock clock) {
        this.appointments = appointments; this.barbers = barbers; this.branches = branches;
        this.accounts = accounts; this.services = services; this.rules = rules; this.clock = clock;
    }

    public AvailabilityResponse availability(UUID branchId, UUID barberId, LocalDate date) {
        var barber = barbers.findAvailableById(barberId).orElseThrow(NotFoundException::new);
        if (!barber.getBranch().getId().equals(branchId)) throw new NotFoundException();
        return new AvailabilityResponse(branchId, barberId, date, SlotRules.ZONE.getId(),
                freeSlots(barber.getBranch(), barberId, date));
    }

    private List<SlotResponse> freeSlots(Branch branch, UUID barberId, LocalDate date) {
        var hours = branch.getWeeklyHours().stream().filter(h -> h.getDay() == date.getDayOfWeek())
                .findFirst().orElseThrow(() -> new BusinessConflictException("Horario no configurado."));
        var occupied = appointments.occupied(barberId, date.atStartOfDay(SlotRules.ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(SlotRules.ZONE).toInstant());
        return rules.candidates(date, hours.toResponse()).stream()
                .filter(slot -> occupied.stream().noneMatch(o -> SlotRules.overlaps(slot.startsAt(),
                        slot.endsAt(), o.getStartsAt(), o.getEndsAt()))).toList();
    }

    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public AppointmentResponse book(UUID clientId, BookingRequest request) {
        Instant start = rules.start(request.date(), request.time());
        if (start.isBefore(clock.instant())) throw new IllegalArgumentException("Fecha pasada.");
        if (new HashSet<>(request.addOnIds()).size() != request.addOnIds().size()
                || request.addOnIds().contains(request.primaryServiceId()))
            throw new IllegalArgumentException("Servicios repetidos.");

        // Shared locks allow competing bookings while keeping catalog/state stable until commit.
        UUID branchId = barbers.findBranchId(request.barberId()).orElseThrow(NotFoundException::new);
        var branch = branches.findForBooking(branchId).orElseThrow(NotFoundException::new);
        BarberProfile barber = barbers.findForBooking(request.barberId()).orElseThrow(NotFoundException::new);
        var barberAccount = accounts.findForBooking(barber.getAccount().getId()).orElseThrow(NotFoundException::new);
        if (!branch.isActive() || !barber.isActive() || !barberAccount.isActive()) throw new NotFoundException();
        var client = accounts.findForBooking(clientId).orElseThrow(() -> new BadCredentialsException("Cuenta no disponible."));
        if (!client.isActive()) throw new BadCredentialsException("Cuenta no disponible.");
        if (!client.getRoles().contains(Role.CLIENT)) throw new AccessDeniedException("Permiso insuficiente.");

        List<UUID> selected = new ArrayList<>();
        selected.add(request.primaryServiceId()); selected.addAll(request.addOnIds());
        Map<UUID, CatalogService> catalog = new HashMap<>();
        for (UUID id : selected.stream().sorted().toList()) {
            var service = services.findForBooking(id).orElseThrow(NotFoundException::new);
            if (!service.isActive()) throw new NotFoundException();
            catalog.put(id, service);
        }
        if (catalog.get(request.primaryServiceId()).getType() != ServiceType.HAIRCUT
                || request.addOnIds().stream().anyMatch(id -> catalog.get(id).getType() != ServiceType.ADD_ON))
            throw new IllegalArgumentException("Tipo de servicio incorrecto.");
        if (freeSlots(branch, barber.getId(), request.date()).stream().noneMatch(s -> s.startsAt().equals(start)))
            throw new BusinessConflictException("El horario no está disponible.");

        var appointment = new Appointment(clientId, barber.getId(), branchId, start,
                selected.stream().map(id -> new BookedService(catalog.get(id))).toList(), clock.instant());
        try {
            appointments.saveAndFlush(appointment);
        } catch (DataIntegrityViolationException exception) {
            for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
                if (cause instanceof SQLException sql && "23505".equals(sql.getSQLState()))
                    throw new BusinessConflictException("El horario no está disponible.");
            }
            throw exception;
        }
        return AppointmentResponse.from(appointment);
    }

    @PreAuthorize("hasRole('CLIENT')")
    public PageResponse<AppointmentResponse> own(UUID clientId, int page, int size, String sort) {
        return PageResponse.from(appointments.findByClientId(clientId,
                CatalogPage.of(page, size, sort, Map.of("startsAt", "startsAt", "id", "id")))
                .map(AppointmentResponse::from));
    }

    @PreAuthorize("hasRole('CLIENT')")
    public AppointmentResponse ownDetail(UUID clientId, UUID id) {
        return AppointmentResponse.from(appointments.findByIdAndClientId(id, clientId)
                .orElseThrow(NotFoundException::new));
    }
}
