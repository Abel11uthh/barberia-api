package com.barberia.api.products.controller;

import com.barberia.api.auth.security.AccountPrincipal;
import com.barberia.api.products.dto.*;
import com.barberia.api.products.service.ProductInventoryService;
import com.barberia.api.shared.pagination.PageResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/products")
public class AdminProductController {
    private final ProductInventoryService products;
    public AdminProductController(ProductInventoryService products) { this.products = products; }
    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return products.listForAdmin(search, page, size, sort);
    }
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) { return products.getForAdmin(id); }
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductResponse create(@Valid @RequestBody ProductRequest request) { return products.create(request); }
    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody ProductRequest request) {
        return products.update(id, request);
    }
    @PatchMapping("/{id}/deactivate")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivate(@PathVariable UUID id) { products.deactivate(id); }
    @PostMapping("/{id}/inventory/entries")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse entry(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id,
            @Valid @RequestBody MovementRequest request) { return products.entry(actor.id(), id, request); }
    @PostMapping("/{id}/inventory/adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    public MovementResponse adjustment(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id,
            @Valid @RequestBody MovementRequest request) { return products.adjustment(actor.id(), id, request); }
    @GetMapping("/{id}/inventory/movements")
    public PageResponse<MovementResponse> history(@PathVariable UUID id, @RequestParam(required = false) UUID branchId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "occurredAt,desc") String sort) {
        return products.history(id, branchId, page, size, sort);
    }
}
