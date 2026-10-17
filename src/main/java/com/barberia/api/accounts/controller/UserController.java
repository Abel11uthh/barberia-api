package com.barberia.api.accounts.controller;

import com.barberia.api.accounts.dto.UserResponse;
import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.auth.security.AccountPrincipal;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/users")
public class UserController {
    private final AccountService accounts;

    public UserController(AccountService accounts) { this.accounts = accounts; }

    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AccountPrincipal principal) {
        return accounts.requireActive(principal.id());
    }
}
