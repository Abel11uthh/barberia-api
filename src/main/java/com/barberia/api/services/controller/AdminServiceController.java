package com.barberia.api.services.controller;

import com.barberia.api.services.dto.*;
import com.barberia.api.services.service.ServiceCatalogService;
import com.barberia.api.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/services")
public class AdminServiceController {
    private final ServiceCatalogService service;
    public AdminServiceController(ServiceCatalogService service) { this.service = service; }
    @GetMapping
    public PageResponse<ServiceResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "name,asc") String sort) {
        return service.listAll(page, size, sort);
    }
    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable UUID id) { return service.getAny(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceResponse create(@Valid @RequestBody ServiceRequest request) { return service.create(request); }
    @PutMapping("/{id}")
    public ServiceResponse update(@PathVariable UUID id, @Valid @RequestBody ServiceRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) { service.deactivate(id); }
}
