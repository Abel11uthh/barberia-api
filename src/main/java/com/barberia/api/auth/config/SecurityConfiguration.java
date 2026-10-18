package com.barberia.api.auth.config;

import com.barberia.api.auth.security.AccountJwtConverter;
import com.barberia.api.auth.security.SecurityErrors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

@Configuration(proxyBeanMethods = false)
@EnableMethodSecurity
public class SecurityConfiguration {
    @Bean
    PasswordEncoder passwordEncoder() { return new BCryptPasswordEncoder(12); }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AccountJwtConverter converter,
                                            SecurityErrors errors) throws Exception {
        return http.csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable).httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable).requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/register", "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me").authenticated()
                        .requestMatchers(HttpMethod.GET, "/api/v1/users/me/booking-status").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/appointments/availability").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.POST, "/api/v1/appointments/*/cancel").hasRole("CLIENT")
                        .requestMatchers("/api/v1/staff/**").hasAnyRole("BARBER","ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/products", "/api/v1/products/*").hasRole("CLIENT")
                        .requestMatchers(HttpMethod.GET, "/api/v1/appointments", "/api/v1/appointments/*").hasRole("CLIENT")
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .requestMatchers(HttpMethod.GET, "/api/v1/branches", "/api/v1/branches/*",
                                "/api/v1/services", "/api/v1/services/*", "/api/v1/barbers", "/api/v1/barbers/*").authenticated()
                        .anyRequest().denyAll())
                .exceptionHandling(exceptions -> exceptions.authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint(errors).accessDeniedHandler(errors))
                .build();
    }
}
