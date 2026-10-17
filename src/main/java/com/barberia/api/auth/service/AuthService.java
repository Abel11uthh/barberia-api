package com.barberia.api.auth.service;

import com.barberia.api.accounts.dto.UserResponse;
import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.auth.dto.*;
import org.springframework.stereotype.Service;

@Service
public class AuthService {
    private final AccountService accounts;
    private final JwtService tokens;

    public AuthService(AccountService accounts, JwtService tokens) {
        this.accounts = accounts;
        this.tokens = tokens;
    }

    public UserResponse register(RegisterRequest request) { return accounts.register(request); }

    public TokenResponse login(LoginRequest request) {
        return tokens.issue(accounts.authenticate(request).id());
    }
}
