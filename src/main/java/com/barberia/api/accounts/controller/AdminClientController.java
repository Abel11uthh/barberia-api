package com.barberia.api.accounts.controller;

import com.barberia.api.accounts.dto.BookingStatusResponse;
import com.barberia.api.accounts.dto.ReactivateRequest;
import com.barberia.api.accounts.service.ClientIncidentService;
import com.barberia.api.auth.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/clients")
public class AdminClientController {
    private final ClientIncidentService incidents;

    public AdminClientController(ClientIncidentService incidents) { this.incidents = incidents; }

    @PostMapping("/{id}/reactivate")
    public BookingStatusResponse reactivate(@AuthenticationPrincipal AccountPrincipal principal,
            @PathVariable UUID id, @Valid @RequestBody ReactivateRequest request) {
        return incidents.reactivate(principal.id(), id, request);
    }
}
