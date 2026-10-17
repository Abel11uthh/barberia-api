package com.barberia.api.barbers.controller;

import com.barberia.api.barbers.dto.*;
import com.barberia.api.barbers.service.BarberService;
import com.barberia.api.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/barbers")
public class AdminBarberController {
    private final BarberService service;
    public AdminBarberController(BarberService service) { this.service = service; }
    @GetMapping
    public PageResponse<AdminBarberResponse> list(@RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) { return service.listAll(branchId, page, size, sort); }
    @GetMapping("/{id}")
    public AdminBarberResponse get(@PathVariable UUID id) { return service.getAny(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AdminBarberResponse create(@Valid @RequestBody CreateBarberRequest request) { return service.create(request); }
    @PostMapping("/assign")
    @ResponseStatus(HttpStatus.CREATED)
    public AdminBarberResponse assign(@Valid @RequestBody AssignBarberRequest request) { return service.assign(request); }
    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) { service.deactivate(id); }
}
