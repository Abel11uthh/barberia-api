package com.barberia.api.services.controller;

import com.barberia.api.services.dto.ServiceResponse;
import com.barberia.api.services.service.ServiceCatalogService;
import com.barberia.api.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/services")
public class ServiceController {
    private final ServiceCatalogService service;
    public ServiceController(ServiceCatalogService service) { this.service = service; }
    @GetMapping
    public PageResponse<ServiceResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "name,asc") String sort) {
        return service.list(page, size, sort);
    }
    @GetMapping("/{id}")
    public ServiceResponse get(@PathVariable UUID id) { return service.get(id); }
}
