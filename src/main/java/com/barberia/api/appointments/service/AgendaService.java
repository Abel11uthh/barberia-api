package com.barberia.api.appointments.service;

import com.barberia.api.accounts.entity.Role;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.accounts.service.ClientIncidentService;
import com.barberia.api.appointments.dto.*;
import com.barberia.api.appointments.entity.*;
import com.barberia.api.appointments.repository.*;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.shared.error.*;
import com.barberia.api.shared.pagination.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly=true)
public class AgendaService {
    private final AppointmentRepository appointments;
    private final BlockRepository blocks;
    private final AccountRepository accounts;
    private final ClientIncidentService incidents;
    private final BarberRepository barbers;
    private final BookingResources resources;
    private final AgendaAvailability availability;
    private final SlotRules rules;
    private final Clock clock;
    public AgendaService(AppointmentRepository appointments, BlockRepository blocks, AccountRepository accounts,
            ClientIncidentService incidents, BarberRepository barbers, BookingResources resources, AgendaAvailability availability, SlotRules rules, Clock clock) {
        this.appointments=appointments; this.blocks=blocks; this.accounts=accounts; this.barbers=barbers;
        this.incidents=incidents; this.resources=resources; this.availability=availability; this.rules=rules; this.clock=clock;
    }
    private boolean isAdmin(UUID actor) {
        var account=accounts.findById(actor).filter(a->a.isActive())
                .orElseThrow(()->new BadCredentialsException("Cuenta no disponible."));
        return account.getRoles().contains(Role.ADMIN);
    }
    private UUID staffBarber(UUID actor, UUID requested) {
        if(isAdmin(actor)) return requested;
        UUID own=barbers.findByAccountId(actor).orElseThrow(NotFoundException::new).getId();
        if(requested!=null && !own.equals(requested)) throw new NotFoundException();
        return own;
    }
    private void staffAppointment(UUID actor, Appointment appointment) { staffBarber(actor,appointment.getBarberId()); }
    private StaffAppointmentResponse response(Appointment a) {
        StaffAppointmentResponse.Customer customer;
        if(a.getClientId()==null) customer=new StaffAppointmentResponse.Customer(a.getGuestName(),a.getGuestPhone(),true);
        else {
            var account=accounts.findById(a.getClientId()).orElseThrow(NotFoundException::new);
            customer=new StaffAppointmentResponse.Customer(account.getName(),account.getPhone(),false);
        }
        Instant now=clock.instant();
        boolean review=(a.getStatus().equals("CONFIRMED")
                && now.isAfter(a.getEndsAt().plus(15,ChronoUnit.MINUTES)))
                || (a.getStatus().equals("IN_PROGRESS") && now.isAfter(a.getEndsAt()));
        return new StaffAppointmentResponse(AppointmentResponse.from(a),customer,a.getCreatedBy(),a.getCancelledBy(),review);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public StaffAppointmentResponse walkIn(UUID actor, WalkInRequest request) {
        staffBarber(actor,request.barberId());
        var barber=resources.activeBarber(request.barberId());
        var selected=resources.services(request.primaryServiceId(),request.addOnIds());
        Instant now=clock.instant(), start=now.truncatedTo(ChronoUnit.MINUTES), end=start.plusSeconds(3600);
        var hours=barber.getBranch().getWeeklyHours().stream()
                .filter(h->h.getDay()==start.atZone(SlotRules.ZONE).getDayOfWeek()).findFirst()
                .orElseThrow(()->new BusinessConflictException("Horario no configurado."));
        if(!rules.fits(start,end,hours.toResponse())) throw new BusinessConflictException("El horario no está disponible.");
        availability.requireFree(barber.getId(),start,end);
        var appointment=Appointment.walkIn(barber.getId(),barber.getBranch().getId(),start,selected,
                actor,request.guestName(),request.guestPhone(),now);
        AgendaConflicts.flush(()->appointments.saveAndFlush(appointment));
        return response(appointment);
    }
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public PageResponse<StaffAppointmentResponse> agenda(UUID actor, UUID barberId, UUID branchId,
            LocalDate date, AppointmentStatus status, int page, int size) {
        UUID scoped=staffBarber(actor,barberId);
        return PageResponse.from(appointments.agenda(scoped,branchId,date.atStartOfDay(SlotRules.ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(SlotRules.ZONE).toInstant(),status==null?null:status.name(),
                CatalogPage.of(page,size,"startsAt,asc",Map.of("startsAt","startsAt"))).map(this::response));
    }
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public StaffAppointmentResponse detail(UUID actor, UUID id) {
        var appointment=appointments.findById(id).orElseThrow(NotFoundException::new);
        staffAppointment(actor,appointment); return response(appointment);
    }
    @Transactional
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public StaffAppointmentResponse transition(UUID actor, UUID id, AppointmentStatus target) {
        var a=appointments.findForUpdate(id).orElseThrow(NotFoundException::new);
        staffAppointment(actor,a);
        if(a.getStatus().equals(target.name())) return response(a);
        Instant now=clock.instant();
        switch(target) {
            case IN_PROGRESS -> {
                requireState(a,"CONFIRMED");
                if(now.isBefore(a.getStartsAt())) throw new BusinessConflictException("La cita todavía no comienza.");
                a.begin(now);
            }
            case COMPLETED -> {
                requireState(a,"IN_PROGRESS");
                if(now.isBefore(a.getEndsAt())) throw new BusinessConflictException("El intervalo todavía no finaliza.");
                a.complete(now);
            }
            case NO_SHOW -> {
                requireState(a,"CONFIRMED");
                if(now.isBefore(a.getEndsAt())) throw new BusinessConflictException("El intervalo todavía no finaliza.");
                if(now.isAfter(a.getEndsAt().plus(15,ChronoUnit.MINUTES)))
                    throw new BusinessConflictException("Terminó la ventana de 15 minutos para marcar la inasistencia.");
                a.noShow(now);
            }
            default -> throw new IllegalArgumentException();
        }
        AgendaConflicts.flush(appointments::flush);
        if(target==AppointmentStatus.COMPLETED || target==AppointmentStatus.NO_SHOW)
            incidents.record(a.getClientId(),a.getId(),actor,target.name());
        return response(a);
    }
    @Transactional
    @PreAuthorize("hasRole('CLIENT')")
    public AppointmentResponse cancelOwn(UUID actor, UUID id, CancelRequest request) {
        var a=appointments.findForUpdate(id).orElseThrow(NotFoundException::new);
        if(!actor.equals(a.getClientId())) throw new NotFoundException();
        if(a.getStatus().equals("CANCELLED")) return AppointmentResponse.from(a,incidents.ownStatus(actor));
        requireState(a,"CONFIRMED");
        Instant now=clock.instant();
        if(now.plusSeconds(3600).isAfter(a.getStartsAt()))
            throw new BusinessConflictException("La cancelación requiere al menos 60 minutos de anticipación.");
        a.cancel(actor,now,request.reason(),"CLIENT");
        AgendaConflicts.flush(appointments::flush);
        return AppointmentResponse.from(a,incidents.record(a.getClientId(),a.getId(),actor,"CLIENT_CANCELLED"));
    }
    @Transactional
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public StaffAppointmentResponse cancelStaff(UUID actor, UUID id, CancelRequest request) {
        var a=appointments.findForUpdate(id).orElseThrow(NotFoundException::new);
        staffAppointment(actor,a);
        if(request.reason()==null || request.reason().isBlank()) throw new IllegalArgumentException();
        if(a.getStatus().equals("CANCELLED")) return response(a);
        requireState(a,"CONFIRMED");
        a.cancel(actor,clock.instant(),request.reason(),"STAFF");
        AgendaConflicts.flush(appointments::flush);
        return response(a);
    }
    private void requireState(Appointment a,String expected) {
        if(!expected.equals(a.getStatus())) throw new BusinessConflictException("Transición de estado incompatible.");
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BlockResponse createBlock(UUID actor, BlockRequest request) {
        if(!request.startsAt().isBefore(request.endsAt()) || request.startsAt().isBefore(clock.instant()))
            throw new IllegalArgumentException();
        resources.activeBarber(request.barberId());
        availability.requireFree(request.barberId(),request.startsAt(),request.endsAt());
        // Check again after catalog lock waits.
        if(request.startsAt().isBefore(clock.instant())) throw new IllegalArgumentException();
        var block=new BarberBlock(request.barberId(),request.startsAt(),request.endsAt(),request.reason(),actor,clock.instant());
        AgendaConflicts.flush(()->blocks.saveAndFlush(block));
        return BlockResponse.from(block);
    }
    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public BlockResponse cancelBlock(UUID actor, UUID id) {
        var block=blocks.findForUpdate(id).orElseThrow(NotFoundException::new);
        if(!block.getStatus().equals("CANCELLED")) {
            block.cancel(actor,clock.instant());
            AgendaConflicts.flush(blocks::flush);
        }
        return BlockResponse.from(block);
    }
    @PreAuthorize("hasAnyRole('BARBER','ADMIN')")
    public PageResponse<BlockResponse> blocks(UUID actor, UUID barberId, LocalDate date, int page,int size) {
        UUID scoped=staffBarber(actor,barberId);
        return PageResponse.from(blocks.agenda(scoped,date.atStartOfDay(SlotRules.ZONE).toInstant(),
                date.plusDays(1).atStartOfDay(SlotRules.ZONE).toInstant(),
                CatalogPage.of(page,size,"startsAt,asc",Map.of("startsAt","startsAt"))).map(BlockResponse::from));
    }
}
