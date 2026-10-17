package com.barberia.api.appointments.controller;
import com.barberia.api.appointments.dto.*;
import com.barberia.api.appointments.entity.AppointmentStatus;
import com.barberia.api.appointments.service.AgendaService;
import com.barberia.api.auth.security.AccountPrincipal;
import com.barberia.api.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff")
public class AgendaController {
    private final AgendaService service;
    public AgendaController(AgendaService service) { this.service=service; }
    @GetMapping("/appointments")
    public PageResponse<StaffAppointmentResponse> agenda(@AuthenticationPrincipal AccountPrincipal p,
            @RequestParam LocalDate date, @RequestParam(required=false) UUID barberId,
            @RequestParam(required=false) UUID branchId, @RequestParam(required=false) AppointmentStatus status,
            @RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size) {
        return service.agenda(p.id(),barberId,branchId,date,status,page,size);
    }
    @GetMapping("/appointments/{id}")
    public StaffAppointmentResponse detail(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID id) {
        return service.detail(p.id(),id);
    }
    @PostMapping("/appointments/walk-ins")
    public ResponseEntity<StaffAppointmentResponse> walkIn(@AuthenticationPrincipal AccountPrincipal p,
            @Valid @RequestBody WalkInRequest request) {
        var result=service.walkIn(p.id(),request);
        return ResponseEntity.created(URI.create("/api/v1/staff/appointments/"+result.appointment().id())).body(result);
    }
    @PostMapping("/appointments/{id}/start")
    public StaffAppointmentResponse start(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID id) {
        return service.transition(p.id(),id,AppointmentStatus.IN_PROGRESS);
    }
    @PostMapping("/appointments/{id}/complete")
    public StaffAppointmentResponse complete(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID id) {
        return service.transition(p.id(),id,AppointmentStatus.COMPLETED);
    }
    @PostMapping("/appointments/{id}/no-show")
    public StaffAppointmentResponse noShow(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID id) {
        return service.transition(p.id(),id,AppointmentStatus.NO_SHOW);
    }
    @PostMapping("/appointments/{id}/cancel")
    public StaffAppointmentResponse cancel(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID id,
            @Valid @RequestBody CancelRequest request) { return service.cancelStaff(p.id(),id,request); }
    @GetMapping("/blocks")
    public PageResponse<BlockResponse> blocks(@AuthenticationPrincipal AccountPrincipal p,@RequestParam LocalDate date,
            @RequestParam(required=false) UUID barberId,@RequestParam(defaultValue="0") int page,
            @RequestParam(defaultValue="20") int size) { return service.blocks(p.id(),barberId,date,page,size); }
}
