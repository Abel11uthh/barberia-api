package com.barberia.api.appointments.service;

import com.barberia.api.accounts.entity.Role;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.appointments.dto.*;
import com.barberia.api.appointments.entity.Appointment;
import com.barberia.api.appointments.repository.AppointmentRepository;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.entity.Branch;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.pagination.*;
import java.time.*;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class AppointmentService {
    private final AppointmentRepository appointments;
    private final BarberRepository barbers;
    private final AccountRepository accounts;
    private final BookingResources resources;
    private final AgendaAvailability availability;
    private final SlotRules rules;
    private final Clock clock;
    public AppointmentService(AppointmentRepository appointments, BarberRepository barbers, AccountRepository accounts,
            BookingResources resources, AgendaAvailability availability, SlotRules rules, Clock clock) {
        this.appointments=appointments; this.barbers=barbers; this.accounts=accounts;
        this.resources=resources; this.availability=availability; this.rules=rules; this.clock=clock;
    }
    public AvailabilityResponse availability(UUID branchId, UUID barberId, LocalDate date) {
        var barber=barbers.findAvailableById(barberId).orElseThrow(NotFoundException::new);
        if(!barber.getBranch().getId().equals(branchId)) throw new NotFoundException();
        return new AvailabilityResponse(branchId,barberId,date,SlotRules.ZONE.getId(),freeSlots(barber.getBranch(),barberId,date));
    }
    private List<SlotResponse> freeSlots(Branch branch, UUID barberId, LocalDate date) {
        var hours=branch.getWeeklyHours().stream().filter(h->h.getDay()==date.getDayOfWeek())
                .findFirst().orElseThrow(()->new BusinessConflictException("Horario no configurado."));
        var occupied=appointments.occupied(barberId,date.atStartOfDay(SlotRules.ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(SlotRules.ZONE).toInstant());
        return rules.candidates(date,hours.toResponse()).stream()
                .filter(slot->occupied.stream().noneMatch(o->SlotRules.overlaps(slot.startsAt(),slot.endsAt(),o.getStartsAt(),o.getEndsAt()))).toList();
    }
    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public AppointmentResponse book(UUID clientId, BookingRequest request) {
        Instant start=rules.start(request.date(),request.time());
        if(start.isBefore(clock.instant())) throw new IllegalArgumentException("Fecha pasada.");
        var barber=resources.activeBarber(request.barberId());
        var client=accounts.findForBooking(clientId).orElseThrow(()->new BadCredentialsException("Cuenta no disponible."));
        if(!client.isActive()) throw new BadCredentialsException("Cuenta no disponible.");
        if(!client.getRoles().contains(Role.CLIENT)) throw new AccessDeniedException("Permiso insuficiente.");
        var selected=resources.services(request.primaryServiceId(),request.addOnIds());
        if(freeSlots(barber.getBranch(),barber.getId(),request.date()).stream().noneMatch(s->s.startsAt().equals(start)))
            throw new BusinessConflictException("El horario no está disponible.");
        availability.requireFree(barber.getId(),start,start.plusSeconds(3600));
        var appointment=new Appointment(clientId,barber.getId(),barber.getBranch().getId(),start,selected,clock.instant());
        AgendaConflicts.flush(()->appointments.saveAndFlush(appointment));
        return AppointmentResponse.from(appointment);
    }
    @PreAuthorize("hasRole('CLIENT')")
    public PageResponse<AppointmentResponse> own(UUID clientId,int page,int size,String sort) {
        return PageResponse.from(appointments.findByClientId(clientId,
                CatalogPage.of(page,size,sort,Map.of("startsAt","startsAt","id","id"))).map(AppointmentResponse::from));
    }
    @PreAuthorize("hasRole('CLIENT')")
    public AppointmentResponse ownDetail(UUID clientId, UUID id) {
        return AppointmentResponse.from(appointments.findByIdAndClientId(id,clientId).orElseThrow(NotFoundException::new));
    }
}
