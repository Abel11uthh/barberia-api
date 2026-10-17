package com.barberia.api.appointments.controller;
import com.barberia.api.appointments.dto.*;
import com.barberia.api.appointments.service.AgendaService;
import com.barberia.api.auth.security.AccountPrincipal;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/blocks")
public class BlockController {
    private final AgendaService service;
    public BlockController(AgendaService service) { this.service=service; }
    @PostMapping @ResponseStatus(HttpStatus.CREATED)
    public BlockResponse create(@AuthenticationPrincipal AccountPrincipal p,@Valid @RequestBody BlockRequest request) {
        return service.createBlock(p.id(),request);
    }
    @PostMapping("/{id}/cancel")
    public BlockResponse cancel(@AuthenticationPrincipal AccountPrincipal p,@PathVariable UUID id) {
        return service.cancelBlock(p.id(),id);
    }
}
