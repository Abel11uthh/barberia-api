package com.barberia.api.auth.service;

import com.barberia.api.auth.config.JwtConfiguration;
import com.barberia.api.auth.config.JwtSettings;
import com.barberia.api.auth.dto.TokenResponse;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;

@Service
public class JwtService {
    private final JwtEncoder encoder;
    private final JwtSettings settings;
    private final Clock clock;

    public JwtService(JwtEncoder encoder, JwtSettings settings, Clock clock) {
        this.encoder = encoder;
        this.settings = settings;
        this.clock = clock;
    }

    public TokenResponse issue(UUID accountId) {
        var now = clock.instant();
        var expiresAt = now.plusSeconds(settings.expirationSeconds());
        var claims = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER)
                .audience(List.of(JwtConfiguration.AUDIENCE)).subject(accountId.toString())
                .issuedAt(now).expiresAt(expiresAt).id(UUID.randomUUID().toString()).build();
        String value = encoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
        return new TokenResponse(value, "Bearer", settings.expirationSeconds(), expiresAt);
    }
}
