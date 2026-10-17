package com.barberia.api.accounts.service;

import com.barberia.api.accounts.dto.UserResponse;
import com.barberia.api.accounts.entity.Account;
import com.barberia.api.accounts.entity.Role;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.auth.dto.LoginRequest;
import com.barberia.api.auth.dto.RegisterRequest;
import com.barberia.api.auth.service.PasswordRules;
import java.util.Set;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository accounts;
    private final PasswordEncoder passwords;
    private final String dummyHash;

    public AccountService(AccountRepository accounts, PasswordEncoder passwords) {
        this.accounts = accounts;
        this.passwords = passwords;
        this.dummyHash = passwords.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        return create(request, Set.of(Role.CLIENT));
    }

    @Transactional
    public boolean bootstrapAdministrator(RegisterRequest request) {
        if (accounts.existsByEmail(request.email())) {
            return false; // Nunca modificar contraseña, estado ni roles de una cuenta existente.
        }
        create(request, Set.of(Role.ADMIN));
        return true;
    }

    private UserResponse create(RegisterRequest request, Set<Role> roles) {
        PasswordRules.validateRegistration(request.password());
        if (accounts.existsByEmail(request.email())) { throw new DuplicateEmailException(); }
        var account = new Account(request.name(), request.phone(), request.email(),
                passwords.encode(request.password()), roles);
        try {
            return UserResponse.from(accounts.saveAndFlush(account));
        } catch (DataIntegrityViolationException exception) {
            // La restricción resuelve también dos registros simultáneos del mismo correo.
            Throwable cause = exception;
            while (cause != null) {
                // SQLSTATE no depende del idioma de los mensajes de PostgreSQL.
                if (cause instanceof java.sql.SQLException sql && "23505".equals(sql.getSQLState())) {
                    throw new DuplicateEmailException();
                }
                cause = cause.getCause();
            }
            throw exception;
        }
    }

    @Transactional(readOnly = true)
    public UserResponse authenticate(LoginRequest request) {
        var account = accounts.findByEmail(request.email()).orElse(null);
        String candidate = PasswordRules.fitsBcrypt(request.password()) ? request.password() : "";
        boolean valid = passwords.matches(candidate, account == null ? dummyHash : account.getPasswordHash());
        if (!valid || account == null || !account.isActive()) {
            throw new BadCredentialsException("Credenciales inválidas.");
        }
        return UserResponse.from(account);
    }

    @Transactional(readOnly = true)
    public UserResponse requireActive(UUID id) {
        return accounts.findById(id).filter(Account::isActive).map(UserResponse::from)
                .orElseThrow(() -> new BadCredentialsException("No autorizado."));
    }
}
