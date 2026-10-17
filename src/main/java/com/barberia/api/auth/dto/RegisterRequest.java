package com.barberia.api.auth.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.Locale;

public record RegisterRequest(
        @NotBlank @Size(max = 120) String name,
        @NotBlank @Size(max = 32) String phone,
        @NotBlank @Email @Size(max = 254) String email,
        @NotBlank @Size(min = 12, max = 72) String password) {
    public RegisterRequest {
        name = name == null ? null : name.strip();
        phone = phone == null ? null : phone.strip();
        email = email == null ? null : email.strip().toLowerCase(Locale.ROOT);
    }

    @Override
    public String toString() { return "RegisterRequest[REDACTED]"; }
}
