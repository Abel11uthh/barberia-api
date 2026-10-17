package com.barberia.api.barbers.controller;

import com.barberia.api.barbers.dto.BarberResponse;
import com.barberia.api.barbers.service.BarberService;
import com.barberia.api.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/barbers")
public class BarberController {
    private final BarberService service;
    public BarberController(BarberService service) { this.service = service; }
    @GetMapping
    public PageResponse<BarberResponse> list(@RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) { return service.list(branchId, page, size, sort); }
    @GetMapping("/{id}")
    public BarberResponse get(@PathVariable UUID id) { return service.get(id); }
}
