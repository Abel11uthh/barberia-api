package com.barberia.api.support;

import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.test.context.DynamicPropertyRegistry;

public final class PostgresTestDatabase {
    private PostgresTestDatabase() { }
    public static void configure(DynamicPropertyRegistry properties) {
        String url = required("TEST_DB_URL");
        if (!url.matches("^jdbc:postgresql://[^/?#]+/barberia_auth_test$")) {
            throw new IllegalStateException("Las pruebas solo admiten la base exclusiva barberia_auth_test, sin parámetros JDBC adicionales.");
        }
        String username = required("TEST_DB_USERNAME");
        String password = required("TEST_DB_PASSWORD");
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        String secret = Base64.getEncoder().encodeToString(key);
        properties.add("spring.datasource.url", () -> url);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        properties.add("app.jwt.secret-base64", () -> secret);
        properties.add("app.jwt.expiration-seconds", () -> "900");
        properties.add("app.bootstrap-admin.enabled", () -> "false");
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) { throw new IllegalStateException("Falta la variable " + name); }
        return value;
    }
}