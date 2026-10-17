package com.barberia.api.auth.service;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PasswordRulesTest {
    @Test
    void enforcesBcryptByteLimitWithoutTruncatingUnicode() {
        assertDoesNotThrow(() -> PasswordRules.validateRegistration("é".repeat(36)));
        assertThrows(IllegalArgumentException.class, () -> PasswordRules.validateRegistration("é".repeat(37)));
    }

    @Test
    void rejectsNullBlankAndShortPasswords() {
        assertThrows(IllegalArgumentException.class, () -> PasswordRules.validateRegistration(null));
        assertThrows(IllegalArgumentException.class, () -> PasswordRules.validateRegistration(" ".repeat(12)));
        assertThrows(IllegalArgumentException.class, () -> PasswordRules.validateRegistration("a".repeat(11)));
    }
}
