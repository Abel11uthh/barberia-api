package com.barberia.api.auth.dto;

import java.time.Instant;

public record TokenResponse(String accessToken, String tokenType, long expiresIn, Instant expiresAt) {
    @Override
    public String toString() { return "TokenResponse[REDACTED]"; }
}
