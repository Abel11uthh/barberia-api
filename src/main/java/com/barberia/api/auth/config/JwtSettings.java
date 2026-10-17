package com.barberia.api.auth.config;

import java.util.Base64;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("app.jwt")
public record JwtSettings(String secretBase64, long expirationSeconds) {
    public JwtSettings {
        boolean validKey = false;
        try {
            validKey = secretBase64 != null && Base64.getDecoder().decode(secretBase64).length >= 32;
        } catch (IllegalArgumentException ignored) {
            // No incluir el valor recibido ni la causa en el error de configuración.
        }
        if (!validKey) { throw new IllegalArgumentException("JWT_SECRET_BASE64 debe codificar al menos 32 bytes aleatorios."); }
        if (expirationSeconds < 60 || expirationSeconds > 3600) {
            throw new IllegalArgumentException("JWT_EXPIRATION_SECONDS debe estar entre 60 y 3600.");
        }
    }

    public SecretKey signingKey() {
        return new SecretKeySpec(Base64.getDecoder().decode(secretBase64), "HmacSHA256");
    }

    @Override
    public String toString() { return "JwtSettings[REDACTED]"; }
}
