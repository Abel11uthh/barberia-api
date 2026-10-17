package com.barberia.api.appointments;

import com.barberia.api.accounts.entity.*;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.auth.service.JwtService;
import com.barberia.api.appointments.service.SlotRules;
import com.barberia.api.barbers.entity.BarberProfile;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.dto.*;
import com.barberia.api.branches.entity.Branch;
import com.barberia.api.branches.repository.BranchRepository;
import com.barberia.api.services.dto.ServiceRequest;
import com.barberia.api.services.entity.*;
import com.barberia.api.services.repository.ServiceRepository;
import com.barberia.api.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
@Import(AppointmentPostgresIT.FixedTime.class)
class AppointmentPostgresIT {
    static final LocalDate DATE = LocalDate.of(2030, 1, 7);
    @TestConfiguration(proxyBeanMethods = false)
    static class FixedTime {
        @Bean @Primary Clock appointmentTestClock() {
            return Clock.fixed(Instant.parse("2030-01-07T13:00:00Z"), ZoneOffset.UTC);
        }
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.configure(registry);
    }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transaction;
    @Autowired AccountRepository accounts;
    @Autowired BranchRepository branches;
    @Autowired BarberRepository barbers;
    @Autowired ServiceRepository services;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService tokens;
    @MockitoSpyBean SlotRules rules;
    @LocalServerPort int port;
    UUID clientId, branchId, barberId, barberAccountId, haircutId, addonId;
    String clientToken, otherToken, adminToken;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("barberia_auth_test");
        String hash = encoder.encode(UUID.randomUUID().toString());
        transaction.executeWithoutResult(status -> {
            var client = accounts.save(account(hash, Role.CLIENT)); clientId = client.getId();
            var other = accounts.save(account(hash, Role.CLIENT));
            var admin = accounts.save(account(hash, Role.ADMIN));
            var barberAccount = accounts.save(account(hash, Role.BARBER)); barberAccountId = barberAccount.getId();
            var hours = Arrays.stream(DayOfWeek.values()).map(day -> day == DayOfWeek.SUNDAY
                    ? new HoursRequest(day, true, null, null, null, null)
                    : new HoursRequest(day, false, LocalTime.of(8,0), LocalTime.of(18,0),
                            LocalTime.of(10,0), LocalTime.of(11,0))).toList();
            var branch = branches.save(new Branch(new BranchRequest("Sucursal ficticia", null, hours)));
            branchId = branch.getId();
            barberId = barbers.save(new BarberProfile(barberAccount, branch)).getId();
            haircutId = services.save(new CatalogService(new ServiceRequest("Corte ficticio", null,
                    new BigDecimal("100.10"), ServiceType.HAIRCUT))).getId();
            addonId = services.save(new CatalogService(new ServiceRequest("Complemento ficticio", null,
                    new BigDecimal("25.25"), ServiceType.ADD_ON))).getId();
            clientToken = tokens.issue(clientId).accessToken();
            otherToken = tokens.issue(other.getId()).accessToken();
            adminToken = tokens.issue(admin.getId()).accessToken();
        });
    }
    @AfterEach void resetRules() { reset(rules); }
    private Account account(String hash, Role role) {
        return new Account("Persona ficticia", "0000000000", UUID.randomUUID() + "@example.test", hash, Set.of(role));
    }
    private Map<String,Object> payload(String time) {
        Map<String,Object> result = new HashMap<>();
        result.put("barberId", barberId); result.put("date", DATE.toString()); result.put("time", time);
        result.put("primaryServiceId", haircutId); result.put("addOnIds", List.of(addonId));
        return result;
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(value));
    }
    private JsonNode call(MockHttpServletRequestBuilder request, String token, int status) throws Exception {
        if (token != null) request.header("Authorization", "Bearer " + token);
        String content = mvc.perform(request).andExpect(status().is(status)).andReturn().getResponse().getContentAsString();
        return content.isEmpty() ? json.nullNode() : json.readTree(content);
    }
    private JsonNode book(Map<String,Object> request, String token, int status) throws Exception {
        return call(body(post("/api/v1/appointments"), request), token, status);
    }
    private JsonNode availability(UUID branch, LocalDate date, int status) throws Exception {
        return call(get("/api/v1/appointments/availability").param("branchId", branch.toString())
                .param("barberId", barberId.toString()).param("date", date.toString()), clientToken, status);
    }
    @Test void availabilityUsesDatabaseHoursAndExcludesOccupiedSlotsWithoutOccupants() throws Exception {
        var before = availability(branchId, DATE, 200);
        assertThat(before.get("slots")).hasSize(9);
        book(payload("09:00"), clientToken, 201);
        var after = availability(branchId, DATE, 200);
        assertThat(after.get("slots")).hasSize(8);
        assertThat(after.toString()).doesNotContain("clientId", "name", "email", "phone");
        assertThat(after.get("slots").findValuesAsText("startsAt")).doesNotContain("2030-01-07T15:00:00Z");
        jdbc.update("update branch_hours set break_start='11:00', break_end='12:00' where branch_id=? and day='MONDAY'", branchId);
        var changed = availability(branchId, DATE, 200);
        assertThat(changed.get("slots").findValuesAsText("startsAt"))
                .contains("2030-01-07T16:00:00Z").doesNotContain("2030-01-07T17:00:00Z");
    }
    @Test void sundayAndPastDatesHaveNoAvailability() throws Exception {
        assertThat(availability(branchId, DATE.plusDays(6), 200).get("slots")).isEmpty();
        assertThat(availability(branchId, DATE.minusDays(1), 200).get("slots")).isEmpty();
    }
    @ParameterizedTest @ValueSource(strings = {"07:00", "10:00", "18:00"})
    void outsideHoursAndBreakCannotBeBooked(String time) throws Exception { book(payload(time), clientToken, 409); }
    @Test void pastDateAndNonExactTimesCannotBeBooked() throws Exception {
        var past = payload("09:00"); past.put("date", DATE.minusDays(1).toString());
        book(past, clientToken, 400);
        for (String time : List.of("09:01", "09:00:01", "09:00:00.000000001")) book(payload(time), clientToken, 400);
        var sunday = payload("09:00"); sunday.put("date", DATE.plusDays(6).toString());
        book(sunday, clientToken, 409);
    }
    @Test void mismatchedBranchIsNotFound() throws Exception { availability(UUID.randomUUID(), DATE, 404); }
    @Test void missingAndMalformedAvailabilityParametersAreBadRequests() throws Exception {
        call(get("/api/v1/appointments/availability"), clientToken, 400);
        call(get("/api/v1/appointments/availability").param("branchId", branchId.toString())
                .param("barberId", barberId.toString()).param("date", "not-a-date"), clientToken, 400);
    }
    @ParameterizedTest @ValueSource(strings = {"accounts", "barber_profiles", "branches"})
    void inactiveBarberElementsRejectAvailabilityAndBooking(String table) throws Exception {
        UUID id = switch (table) { case "accounts" -> barberAccountId; case "branches" -> branchId; default -> barberId; };
        jdbc.update("update " + table + " set active=false where id=?", id);
        availability(branchId, DATE, 404);
        book(payload("09:00"), clientToken, 404);
    }
    @Test void inactiveClientRejectedWithExistingToken() throws Exception {
        jdbc.update("update accounts set active=false where id=?", clientId);
        book(payload("09:00"), clientToken, 401);
        call(get("/api/v1/appointments"), clientToken, 401);
    }
    @Test void primaryServiceRequiredAndTypesAreValidated() throws Exception {
        var missing = payload("09:00"); missing.remove("primaryServiceId"); book(missing, clientToken, 400);
        var wrongPrimary = payload("09:00"); wrongPrimary.put("primaryServiceId", addonId);
        wrongPrimary.put("addOnIds", List.of()); book(wrongPrimary, clientToken, 400);
        var wrongAddon = payload("09:00"); wrongAddon.put("addOnIds", List.of(haircutId)); book(wrongAddon, clientToken, 400);
        UUID otherHaircut = transaction.execute(status -> services.save(new CatalogService(
                new ServiceRequest("Otro corte", null, new BigDecimal("10.00"), ServiceType.HAIRCUT))).getId());
        wrongAddon.put("addOnIds", List.of(otherHaircut)); book(wrongAddon, clientToken, 400);
    }
    @ParameterizedTest @ValueSource(strings = {"primary", "addon"})
    void inactiveServicesCannotBeBooked(String kind) throws Exception {
        jdbc.update("update catalog_services set active=false where id=?", kind.equals("primary") ? haircutId : addonId);
        book(payload("09:00"), clientToken, 404);
    }
    @Test void duplicateAndNullAddonsRejectedButOptionalListAllowed() throws Exception {
        var duplicates = payload("09:00"); duplicates.put("addOnIds", List.of(addonId, addonId)); book(duplicates, clientToken, 400);
        duplicates.put("addOnIds", Arrays.asList((Object) null)); book(duplicates, clientToken, 400);
        var optional = payload("09:00"); optional.remove("addOnIds");
        assertThat(book(optional, clientToken, 201).get("services")).hasSize(1);
    }
    @Test void totalsAndSnapshotsRemainHistorical() throws Exception {
        var saved = book(payload("09:00"), clientToken, 201);
        assertThat(saved.get("total").decimalValue()).isEqualByComparingTo("125.35");
        assertThat(saved.get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(saved.get("startsAt").asText()).isEqualTo("2030-01-07T15:00:00Z");
        assertThat(saved.get("endsAt").asText()).isEqualTo("2030-01-07T16:00:00Z");
        jdbc.update("update catalog_services set name='Nombre cambiado', price=999.99, active=false where id in (?, ?)", haircutId, addonId);
        var detail = call(get("/api/v1/appointments/" + saved.get("id").asText()), clientToken, 200);
        assertThat(detail).isEqualTo(saved);
        assertThat(detail.toString()).doesNotContain("password", "hash", "email", "phone", "clientId");
    }
    @Test void principalOwnsBookingAndForeignDetailIsIndistinguishableFromMissing() throws Exception {
        var saved = book(payload("09:00"), clientToken, 201);
        UUID id = UUID.fromString(saved.get("id").asText());
        assertThat(jdbc.queryForObject("select client_id from appointments where id=?", UUID.class, id)).isEqualTo(clientId);
        var foreign = call(get("/api/v1/appointments/" + id), otherToken, 404);
        var missing = call(get("/api/v1/appointments/" + UUID.randomUUID()), otherToken, 404);
        assertThat(foreign.get("code")).isEqualTo(missing.get("code"));
        assertThat(foreign.get("message")).isEqualTo(missing.get("message"));
        assertThat(call(get("/api/v1/appointments"), otherToken, 200).get("totalElements").asInt()).isZero();
        assertThat(call(get("/api/v1/appointments").param("size","1"), clientToken, 200).get("items")).hasSize(1);
        for (String field : List.of("clientId", "branchId", "status", "total")) {
            var spoof = payload("11:00"); spoof.put(field, clientId.toString()); book(spoof, clientToken, 400);
        }
        call(get("/api/v1/appointments").param("sort", "clientId,asc"), clientToken, 400);
        call(get("/api/v1/appointments").param("size", "101"), clientToken, 400);
    }
    @Test void authenticationAndClientRoleRequired() throws Exception {
        book(payload("09:00"), null, 401);
        book(payload("09:00"), adminToken, 403);
        call(get("/api/v1/appointments"), null, 401);
        call(get("/api/v1/appointments"), adminToken, 403);
        call(get("/api/v1/appointments/" + UUID.randomUUID()), adminToken, 403);
        call(get("/api/v1/appointments/availability").param("branchId",branchId.toString())
                .param("barberId",barberId.toString()).param("date",DATE.toString()), null, 401);
        call(get("/api/v1/appointments/availability").param("branchId",branchId.toString())
                .param("barberId",barberId.toString()).param("date",DATE.toString()), adminToken, 200);
    }
    @Test void consecutiveSlotsAndDifferentBarbersCanBeBooked() throws Exception {
        book(payload("11:00"), clientToken, 201);
        book(payload("12:00"), clientToken, 201);
        UUID second = transaction.execute(status -> {
            var account = accounts.save(account(encoder.encode(UUID.randomUUID().toString()), Role.BARBER));
            return barbers.save(new BarberProfile(account, branches.findById(branchId).orElseThrow())).getId();
        });
        var sameTime = payload("11:00"); sameTime.put("barberId", second);
        book(sameTime, clientToken, 201);
        book(payload("11:00"), clientToken, 409);
    }
    @Test void databaseRejectsDuplicateAndNonGridSlots() throws Exception {
        var saved = book(payload("09:00"), clientToken, 201);
        UUID id = UUID.fromString(saved.get("id").asText());
        String copy = "insert into appointments(id,client_id,barber_id,branch_id,starts_at,ends_at,status,total,created_at) "
                + "select ?,client_id,barber_id,branch_id,starts_at,ends_at,status,total,created_at from appointments where id=?";
        assertThatThrownBy(() -> jdbc.update(copy, UUID.randomUUID(), id)).isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update appointments set ends_at=ends_at+interval '1 minute' where id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update("update appointments set starts_at=starts_at+interval '1 minute', ends_at=ends_at+interval '1 minute' where id=?", id))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
    @Test void twoRealHttpRequestsInIndependentTransactionsYieldOneCreatedAndOneConflict() throws Exception {
        CountDownLatch bothValidated = new CountDownLatch(2);
        doAnswer(invocation -> {
            Object candidates = invocation.callRealMethod();
            bothValidated.countDown();
            if (!bothValidated.await(30, TimeUnit.SECONDS)) throw new IllegalStateException("No llegaron ambas reservas.");
            return candidates;
        }).when(rules).candidates(eq(DATE), any());
        String body = json.writeValueAsString(payload("09:00"));
        try (var http = HttpClient.newHttpClient(); var executor = Executors.newFixedThreadPool(2)) {
            Callable<Integer> request = () -> http.send(HttpRequest.newBuilder(
                    URI.create("http://localhost:" + port + "/api/v1/appointments"))
                    .timeout(Duration.ofSeconds(45)).header("Authorization", "Bearer " + clientToken)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.discarding()).statusCode();
            Future<Integer> first = executor.submit(request);
            Future<Integer> second = executor.submit(request);
            assertThat(List.of(first.get(50, TimeUnit.SECONDS), second.get(50, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from appointments where barber_id=?", Integer.class, barberId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointment_services s join appointments a on a.id=s.appointment_id where a.barber_id=?",
                Integer.class, barberId)).isEqualTo(2);
    }
}
