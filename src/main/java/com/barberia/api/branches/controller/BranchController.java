package com.barberia.api.branches.controller;

import com.barberia.api.branches.dto.BranchResponse;
import com.barberia.api.branches.service.BranchService;
import com.barberia.api.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/branches")
public class BranchController {
    private final BranchService service;
    public BranchController(BranchService service) { this.service = service; }
    @GetMapping
    public PageResponse<BranchResponse> list(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "name,asc") String sort) {
        return service.list(page, size, sort);
    }
    @GetMapping("/{id}")
    public BranchResponse get(@PathVariable UUID id) { return service.get(id); }
}
