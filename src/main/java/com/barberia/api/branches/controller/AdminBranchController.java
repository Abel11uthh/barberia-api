package com.barberia.api.branches.controller;

import com.barberia.api.branches.dto.*;
import com.barberia.api.branches.service.BranchService;
import com.barberia.api.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/branches")
public class AdminBranchController {
    private final BranchService service;
    public AdminBranchController(BranchService service) { this.service = service; }
    @GetMapping
    public PageResponse<BranchResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "name,asc") String sort) {
        return service.listAll(page, size, sort);
    }
    @GetMapping("/{id}")
    public BranchResponse get(@PathVariable UUID id) { return service.getAny(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BranchResponse create(@Valid @RequestBody BranchRequest request) { return service.create(request); }
    @PutMapping("/{id}")
    public BranchResponse update(@PathVariable UUID id, @Valid @RequestBody BranchRequest request) { return service.update(id, request); }
    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) { service.deactivate(id); }
}
