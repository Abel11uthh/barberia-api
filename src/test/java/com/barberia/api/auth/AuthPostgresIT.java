package com.barberia.api.auth;

import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.auth.bootstrap.AdminInitializer;
import com.barberia.api.auth.config.JwtConfiguration;
import com.barberia.api.auth.dto.RegisterRequest;
import com.barberia.api.auth.security.AccountJwtConverter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class AuthPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder passwords;
    @Autowired JwtEncoder encoder;
    @Autowired JwtDecoder decoder;
    @Autowired AccountJwtConverter converter;
    @Autowired AccountService accounts;
    @Autowired ApplicationContext context;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry properties) {
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

    @BeforeEach
    void verifyDedicatedDatabase() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("barberia_auth_test");
    }

    @Test
    void registersNormalizedClientWithoutExposingCredentials() throws Exception {
        var request = request();
        var payload = payload(request);
        payload.put("email", "  " + request.email().toUpperCase(Locale.ROOT) + "  ");
        var result = mvc.perform(post("/api/v1/auth/register").contentType("application/json")
                .content(json.writeValueAsBytes(payload)))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.email").value(request.email()))
                .andExpect(jsonPath("$.roles[0]").value("CLIENT"))
                .andExpect(jsonPath("$.password").doesNotExist()).andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andReturn();
        assertThat(result.getResponse().getContentAsString().contains(request.password())).isFalse();
        String hash = jdbc.queryForObject("select password_hash from accounts where email = ?", String.class, request.email());
        assertThat(passwords.matches(request.password(), hash)).isTrue();
        assertThat(hash.startsWith("$2a$12$")).isTrue();
    }

    @Test
    void duplicateEmailIsConflictAndPostgresAlsoEnforcesUniqueness() throws Exception {
        var request = request();
        register(request);
        var duplicated = new RegisterRequest(request.name(), request.phone(), request.email().toUpperCase(Locale.ROOT), request.password());
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsBytes(duplicated)))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "insert into accounts select ?, name, phone, email, password_hash, active, created_at from accounts where email = ?",
                UUID.randomUUID(), request.email()));
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update accounts set email = upper(email) where email = ?", request.email()));
    }

    @Test
    void simultaneousRegistrationsProduceOneClientAndOneConflict() throws Exception {
        var request = request();
        byte[] payload = json.writeValueAsBytes(request);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> register = () -> {
                start.await();
                return mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(payload))
                        .andReturn().getResponse().getStatus();
            };
            var first = executor.submit(register);
            var second = executor.submit(register);
            start.countDown();
            assertThat(List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"roles", "active", "id", "passwordHash"})
    void publicRegistrationRejectsPrivilegeFields(String field) throws Exception {
        var request = request();
        var payload = payload(request);
        payload.put(field, field.equals("roles") ? List.of("ADMIN") : "forbidden");
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, request.email())).isZero();
    }

    @Test
    void rejectsMalformedInputsWithoutEchoingValues() throws Exception {
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").value("Datos inválidos."));
        var payload = payload(request());
        payload.put("password", "é".repeat(37));
        mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsBytes(payload)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginAndMeWorkWithActualSignedBearerToken() throws Exception {
        var request = request();
        var user = register(request);
        String token = login(request);
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(user.get("id").asText()))
                .andExpect(jsonPath("$.email").value(request.email()))
                .andExpect(jsonPath("$.passwordHash").doesNotExist())
                .andExpect(header().doesNotExist("Set-Cookie"));
    }

    @Test
    void invalidUnknownAndInactiveCredentialsHaveSameGenericResponse() throws Exception {
        var request = request();
        register(request);
        String wrong = UUID.randomUUID().toString();
        assertInvalidLogin(request.email(), wrong);
        assertInvalidLogin(UUID.randomUUID() + "@example.test", wrong);
        jdbc.update("update accounts set active = false where email = ?", request.email());
        assertInvalidLogin(request.email(), request.password());
    }

    @Test
    void meRejectsMissingMalformedExpiredAndBadSignatureTokens() throws Exception {
        var request = request();
        var user = register(request);
        mvc.perform(get("/api/v1/users/me")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        String token = login(request);
        String[] parts = token.split("\\.");
        parts[2] = (parts[2].startsWith("A") ? "B" : "A") + parts[2].substring(1);
        for (String invalid : List.of("invalid", String.join(".", parts),
                signedToken(user.get("id").asText(), Instant.now().minusSeconds(60), Map.of()),
                signedToken("not-a-uuid", Instant.now().plusSeconds(900), Map.of()))) {
            mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + invalid))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
        }
    }

    @Test
    void deactivationImmediatelyRejectsPreviouslyIssuedToken() throws Exception {
        var request = request();
        register(request);
        String token = login(request);
        jdbc.update("update accounts set active = false where email = ?", request.email());
        mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void multipleRolesArePreservedAndPermissionsAreReadAgainFromDatabase() throws Exception {
        var request = request();
        UUID id = UUID.fromString(register(request).get("id").asText());
        jdbc.update("insert into account_roles (account_id, role) values (?, 'ADMIN'), (?, 'BARBER')", id, id);
        jdbc.update("delete from account_roles where account_id = ? and role = 'CLIENT'", id);
        String token = login(request);
        var response = mvc.perform(get("/api/v1/users/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn();
        var roles = json.readTree(response.getResponse().getContentAsString()).get("roles");
        List<String> roleNames = new ArrayList<>();
        roles.forEach(role -> roleNames.add(role.asText()));
        assertThat(roleNames).containsExactlyInAnyOrder("ADMIN", "BARBER");
        assertThat(converter.convert(decoder.decode(token)).getAuthorities()).extracting("authority")
                .containsExactlyInAnyOrder("ROLE_ADMIN", "ROLE_BARBER");
        jdbc.update("delete from account_roles where account_id = ? and role = 'ADMIN'", id);
        assertThat(converter.convert(decoder.decode(token)).getAuthorities()).extracting("authority")
                .containsExactly("ROLE_BARBER");
    }

    @Test
    void tokenClaimsCannotGrantRolesAndDeniedRequestsUseJson403() throws Exception {
        var request = request();
        String id = register(request).get("id").asText();
        String token = signedToken(id, Instant.now().plusSeconds(900), Map.of("roles", List.of("ADMIN")));
        assertThat(converter.convert(decoder.decode(token)).getAuthorities()).extracting("authority")
                .containsExactly("ROLE_CLIENT");
        mvc.perform(get("/api/v1/not-allowed").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void bootstrapIsDisabledAndDoesNotPromoteOrChangeExistingClient() throws Exception {
        assertThat(context.getBeansOfType(AdminInitializer.class)).isEmpty();
        var request = request();
        register(request);
        String oldHash = jdbc.queryForObject("select password_hash from accounts where email = ?", String.class, request.email());
        assertThat(accounts.bootstrapAdministrator(new RegisterRequest("Otro nombre", "Otro teléfono", request.email(), UUID.randomUUID().toString())))
                .isFalse();
        assertThat(jdbc.queryForObject("select password_hash from accounts where email = ?", String.class, request.email()).equals(oldHash)).isTrue();
        assertThat(accounts.authenticate(new com.barberia.api.auth.dto.LoginRequest(request.email(), request.password())).roles())
                .containsExactly(com.barberia.api.accounts.entity.Role.CLIENT);
    }

    @Test
    void bootstrapCreatesAdminOnceAndKeepsCredentialsOnRerun() {
        var request = request();
        assertThat(accounts.bootstrapAdministrator(request)).isTrue();
        assertThat(accounts.bootstrapAdministrator(new RegisterRequest(request.name(), request.phone(), request.email(), UUID.randomUUID().toString())))
                .isFalse();
        assertThat(accounts.authenticate(new com.barberia.api.auth.dto.LoginRequest(request.email(), request.password())).roles())
                .containsExactly(com.barberia.api.accounts.entity.Role.ADMIN);
    }

    private JsonNode register(RegisterRequest request) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/auth/register").contentType("application/json").content(json.writeValueAsBytes(request)))
                .andExpect(status().isCreated()).andReturn();
        return json.readTree(result.getResponse().getContentAsString());
    }

    private String login(RegisterRequest request) throws Exception {
        var response = mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("email", request.email().toUpperCase(Locale.ROOT), "password", request.password()))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900)).andExpect(jsonPath("$.expiresAt").isString())
                .andExpect(header().string("Cache-Control", "no-store")).andReturn();
        return json.readTree(response.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void assertInvalidLogin(String email, String password) throws Exception {
        mvc.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json.writeValueAsBytes(Map.of("email", email, "password", password))))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Credenciales inválidas."));
    }

    private String signedToken(String subject, Instant expiry, Map<String, Object> extra) {
        var builder = JwtClaimsSet.builder().issuer(JwtConfiguration.ISSUER).audience(List.of(JwtConfiguration.AUDIENCE))
                .subject(subject).issuedAt(Instant.now().minusSeconds(300)).expiresAt(expiry);
        extra.forEach(builder::claim);
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), builder.build())).getTokenValue();
    }

    private static RegisterRequest request() {
        return new RegisterRequest("Cliente de prueba", "5550000000", UUID.randomUUID() + "@example.test", UUID.randomUUID().toString());
    }

    private static Map<String, Object> payload(RegisterRequest request) {
        return new HashMap<>(Map.of("name", request.name(), "phone", request.phone(), "email", request.email(), "password", request.password()));
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) { throw new IllegalStateException("Falta la variable " + name); }
        return value;
    }
}
