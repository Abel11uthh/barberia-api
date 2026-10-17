package com.barberia.api.appointments.controller;

import com.barberia.api.appointments.dto.*;
import com.barberia.api.appointments.service.AppointmentService;
import com.barberia.api.auth.security.AccountPrincipal;
import com.barberia.api.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/appointments")
public class AppointmentController {
    private final AppointmentService service;
    public AppointmentController(AppointmentService service) { this.service = service; }

    @GetMapping("/availability")
    public AvailabilityResponse availability(@RequestParam UUID branchId, @RequestParam UUID barberId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return service.availability(branchId, barberId, date);
    }
    @PostMapping
    public ResponseEntity<AppointmentResponse> book(@AuthenticationPrincipal AccountPrincipal principal,
            @Valid @RequestBody BookingRequest request) {
        var response = service.book(principal.id(), request);
        return ResponseEntity.created(URI.create("/api/v1/appointments/" + response.id())).body(response);
    }
    @GetMapping
    public PageResponse<AppointmentResponse> own(@AuthenticationPrincipal AccountPrincipal principal,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "startsAt,desc") String sort) {
        return service.own(principal.id(), page, size, sort);
    }
    @GetMapping("/{id}")
    public AppointmentResponse detail(@AuthenticationPrincipal AccountPrincipal principal, @PathVariable UUID id) {
        return service.ownDetail(principal.id(), id);
    }
}
