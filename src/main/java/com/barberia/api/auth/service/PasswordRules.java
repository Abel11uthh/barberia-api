package com.barberia.api.auth.service;

import java.nio.charset.StandardCharsets;

public final class PasswordRules {
    private PasswordRules() { }

    public static boolean fitsBcrypt(String password) {
        return password != null && password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }

    public static void validateRegistration(String password) {
        if (password == null || password.isBlank() || password.length() < 12 || !fitsBcrypt(password)) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 12 caracteres y hasta 72 bytes UTF-8.");
        }
    }
}
