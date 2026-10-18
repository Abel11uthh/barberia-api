package com.barberia.api.payments.controller;
import com.barberia.api.auth.security.AccountPrincipal;
import com.barberia.api.payments.dto.*;
import com.barberia.api.payments.service.ChargeService;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
public class ChargeController {
    private final ChargeService charges;
    public ChargeController(ChargeService charges) { this.charges=charges; }
    @PostMapping("/api/v1/staff/appointments/{id}/charge")
    @ResponseStatus(HttpStatus.CREATED)
    public ChargeResponse charge(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id,
            @Valid @RequestBody ChargeRequest request) { return charges.charge(actor.id(),id,request); }
    @GetMapping("/api/v1/staff/appointments/{id}/charge")
    public ChargeResponse detail(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id) {
        return charges.detail(actor.id(),id);
    }
    @GetMapping("/api/v1/appointments/{id}/payment")
    public OwnPaymentResponse own(@AuthenticationPrincipal AccountPrincipal actor, @PathVariable UUID id) {
        return charges.own(actor.id(),id);
    }
}
