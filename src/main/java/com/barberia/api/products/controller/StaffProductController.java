package com.barberia.api.products.controller;

import com.barberia.api.auth.security.AccountPrincipal;
import com.barberia.api.products.dto.ProductResponse;
import com.barberia.api.products.service.ProductInventoryService;
import com.barberia.api.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/staff/products")
public class StaffProductController {
    private final ProductInventoryService products;
    public StaffProductController(ProductInventoryService products) { this.products = products; }
    @GetMapping
    public PageResponse<ProductResponse> list(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestParam(required = false) String search, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size, @RequestParam(defaultValue = "name,asc") String sort) {
        return products.listForBarber(actor.id(), search, page, size, sort);
    }
    @GetMapping("/{id}")
    public ProductResponse get(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id) {
        return products.getForBarber(actor.id(), id);
    }
}
