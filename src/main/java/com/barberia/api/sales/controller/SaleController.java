package com.barberia.api.sales.controller;
import com.barberia.api.auth.security.AccountPrincipal;
import com.barberia.api.sales.dto.*;
import com.barberia.api.sales.service.SaleService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/staff/sales")
public class SaleController {
    private final SaleService sales;
    public SaleController(SaleService sales) { this.sales=sales; }
    @PostMapping
    public ResponseEntity<SaleResponse> create(@AuthenticationPrincipal AccountPrincipal actor,
            @RequestHeader("Idempotency-Key") UUID key, @Valid @RequestBody SaleRequest request) {
        var result=sales.create(actor.id(),key,request);
        return ResponseEntity.status(result.replayed()?HttpStatus.OK:HttpStatus.CREATED).body(result.sale());
    }
    @GetMapping("/{id}")
    public SaleResponse detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id) {
        return sales.detail(actor.id(),id);
    }
}
