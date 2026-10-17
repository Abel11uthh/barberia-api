package com.barberia.api.auth.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(JwtSettings.class)
public class JwtConfiguration {
    public static final String ISSUER = "barberia-api";
    public static final String AUDIENCE = "barberia-api";

    @Bean
    Clock jwtClock() { return Clock.systemUTC(); }

    @Bean
    JwtEncoder jwtEncoder(JwtSettings settings) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(settings.signingKey()));
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtSettings settings, Clock clock) {
        var decoder = NimbusJwtDecoder.withSecretKey(settings.signingKey()).macAlgorithm(MacAlgorithm.HS256).build();
        var timestamps = new JwtTimestampValidator(Duration.ZERO);
        timestamps.setClock(clock);
        OAuth2TokenValidator<Jwt> requiredClaims = jwt -> {
            try {
                UUID.fromString(jwt.getSubject());
                if (jwt.getExpiresAt() == null || jwt.getIssuedAt() == null
                        || !clock.instant().isBefore(jwt.getExpiresAt())
                        || jwt.getIssuedAt().isAfter(clock.instant())
                        || !jwt.getAudience().contains(AUDIENCE)) {
                    return invalidClaims();
                }
                return OAuth2TokenValidatorResult.success();
            } catch (IllegalArgumentException | NullPointerException exception) {
                return invalidClaims();
            }
        };
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(timestamps,
                new JwtIssuerValidator(ISSUER), requiredClaims));
        return decoder;
    }

    private static OAuth2TokenValidatorResult invalidClaims() {
        return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Token inválido.", null));
    }
}
