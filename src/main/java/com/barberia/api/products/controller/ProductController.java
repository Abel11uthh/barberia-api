package com.barberia.api.products.controller;

import com.barberia.api.products.dto.ProductResponse;
import com.barberia.api.products.service.ProductInventoryService;
import com.barberia.api.shared.pagination.PageResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/products")
public class ProductController {
    private final ProductInventoryService products;
    public ProductController(ProductInventoryService products) { this.products = products; }
    @GetMapping
    public PageResponse<ProductResponse> list(@RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "name,asc") String sort) {
        return products.listForClient(search, page, size, sort);
    }
    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable UUID id) { return products.getForClient(id); }
}
