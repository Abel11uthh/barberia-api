package com.barberia.api.auth.config;

import com.barberia.api.auth.service.JwtService;
import java.security.SecureRandom;
import java.time.*;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class JwtSecurityTest {
    private final JwtConfiguration configuration = new JwtConfiguration();
    private final Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:00:00Z"), ZoneOffset.UTC);
    private JwtSettings settings;
    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setup() {
        settings = new JwtSettings(randomSecret(), 900);
        encoder = configuration.jwtEncoder(settings);
        decoder = configuration.jwtDecoder(settings, clock);
    }

    @Test
    void issuedTokenContainsIdentityAndLimitedLifetime() {
        UUID id = UUID.randomUUID();
        var response = new JwtService(encoder, settings, clock).issue(id);
        var jwt = decoder.decode(response.accessToken());
        assertThat(jwt.getSubject()).isEqualTo(id.toString());
        assertThat(jwt.getExpiresAt()).isEqualTo(clock.instant().plusSeconds(900));
        assertThat(jwt.hasClaim("roles")).isFalse();
        assertThat(response.expiresIn()).isEqualTo(900);
    }

    @Test
    void rejectsExpiredTokenIncludingExactExpiry() {
        var response = new JwtService(encoder, settings, Clock.offset(clock, Duration.ofSeconds(-900)))
                .issue(UUID.randomUUID());
        assertThrows(JwtException.class, () -> decoder.decode(response.accessToken()));
    }

    @Test
    void rejectsTokenSignedWithAnotherKey() {
        var other = new JwtSettings(randomSecret(), 900);
        var token = new JwtService(configuration.jwtEncoder(other), other, clock).issue(UUID.randomUUID());
        assertThrows(JwtException.class, () -> decoder.decode(token.accessToken()));
    }

    @Test
    void rejectsMissingExpiration() {
        var claims = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER)
                .audience(List.of(JwtConfiguration.AUDIENCE)).subject(UUID.randomUUID().toString())
                .issuedAt(clock.instant()).build();
        assertThrows(JwtException.class, () -> decoder.decode(sign(claims)));
    }

    @Test
    void rejectsWrongIssuerAudienceAndSubject() {
        for (String field : List.of("issuer", "audience", "subject")) {
            var claims = JwtClaimsSet.builder()
                    .issuer(field.equals("issuer") ? "another-api" : JwtConfiguration.ISSUER)
                    .audience(List.of(field.equals("audience") ? "another-api" : JwtConfiguration.AUDIENCE))
                    .subject(field.equals("subject") ? "not-an-account-id" : UUID.randomUUID().toString())
                    .issuedAt(clock.instant()).expiresAt(clock.instant().plusSeconds(900)).build();
            assertThrows(JwtException.class, () -> decoder.decode(sign(claims)));
        }
    }

    @Test
    void rejectsMissingShortOrMalformedKeyAndUnboundedLifetime() {
        assertThrows(IllegalArgumentException.class, () -> new JwtSettings(null, 900));
        assertThrows(IllegalArgumentException.class, () -> new JwtSettings("invalid-base64!", 900));
        assertThrows(IllegalArgumentException.class, () -> new JwtSettings(Base64.getEncoder().encodeToString(new byte[16]), 900));
        assertThrows(IllegalArgumentException.class, () -> new JwtSettings(randomSecret(), 0));
        assertThrows(IllegalArgumentException.class, () -> new JwtSettings(randomSecret(), 3601));
    }

    private String sign(JwtClaimsSet claims) {
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();
    }

    private static String randomSecret() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
