package com.barberia.api.accounts.controller;

import com.barberia.api.accounts.dto.UserResponse;
import com.barberia.api.accounts.dto.BookingStatusResponse;
import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.accounts.service.ClientIncidentService;
import com.barberia.api.auth.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final AccountService accounts;
    private final ClientIncidentService incidents;

    public UserController(AccountService accounts, ClientIncidentService incidents) {
        this.accounts = accounts; this.incidents = incidents;
    }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return accounts.requireActive(principal.id());
    }
    @GetMapping("/me/booking-status")
    public BookingStatusResponse bookingStatus(@AuthenticationPrincipal AccountPrincipal principal) {
        return incidents.ownStatus(principal.id());
    }
}
