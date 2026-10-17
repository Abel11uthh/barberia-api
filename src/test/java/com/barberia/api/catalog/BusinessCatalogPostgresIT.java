package com.barberia.api.catalog;

import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.auth.dto.RegisterRequest;
import com.barberia.api.auth.service.JwtService;
import com.barberia.api.barbers.repository.BarberRepository;
import com.barberia.api.branches.service.BranchService;
import com.barberia.api.branches.dto.BranchRequest;
import com.barberia.api.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class BusinessCatalogPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired JwtService tokens;
    @Autowired BranchService branches;
    @MockitoSpyBean BarberRepository barbers;
    String adminToken;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }

    @BeforeEach
    void setup() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("barberia_auth_test");
        var admin = accountRequest();
        accounts.bootstrapAdministrator(admin);
        adminToken = tokens.issue(accounts.authenticate(new com.barberia.api.auth.dto.LoginRequest(admin.email(), admin.password())).id()).accessToken();
    }

    @Test
    void migrationSeedsAgreedCatalogAndSchedules() throws Exception {
        String[] names = {"Corte básico", "Corte escolar", "Corte personalizado", "Arreglo de barba", "Arreglo de ceja"};
        String[] prices = {"100.00", "120.00", "150.00", "50.00", "30.00"};
        for (int i = 0; i < names.length; i++) {
            var item = call(get("/api/v1/services/20000000-0000-0000-0000-00000000000" + (i + 1)), adminToken, 200);
            assertThat(item.get("name").asText()).isEqualTo(names[i]);
            assertThat(item.get("price").decimalValue()).isEqualByComparingTo(prices[i]);
            assertThat(item.get("type").asText()).isEqualTo(i < 3 ? "HAIRCUT" : "ADD_ON");
            assertThat(item.get("currency").asText()).isEqualTo("MXN");
            assertThat(item.has("duration")).isFalse();
        }
        for (int i = 1; i <= 2; i++) {
            var branch = call(get("/api/v1/branches/10000000-0000-0000-0000-00000000000" + i), adminToken, 200);
            assertThat(branch.get("name").asText()).isEqualTo(i == 1 ? "Sucursal principal" : "Sucursal 2");
            assertThat(branch.get("address").isNull()).isTrue();
            assertThat(branch.get("timeZone").asText()).isEqualTo("America/Mexico_City");
            assertThat(branch.get("weeklyHours").size()).isEqualTo(7);
            for (var day : branch.get("weeklyHours")) {
                if (day.get("day").asText().equals("SUNDAY")) {
                    assertThat(day.get("closed").asBoolean()).isTrue();
                    assertThat(day.get("opensAt").isNull()).isTrue();
                } else {
                    assertThat(day.get("opensAt").asText()).startsWith("08:00");
                    assertThat(day.get("closesAt").asText()).startsWith("18:00");
                    assertThat(day.get("breakStart").asText()).startsWith(i == 1 ? "10:00" : "11:00");
                    assertThat(day.get("breakEnd").asText()).startsWith(i == 1 ? "11:00" : "12:00");
                }
            }
        }
    }

    @Test
    void catalogRequiresAuthentication() throws Exception {
        for (String resource : List.of("branches", "services", "barbers")) {
            call(get("/api/v1/" + resource), null, 401);
            call(get("/api/v1/" + resource + "/" + UUID.randomUUID()), null, 401);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"CLIENT", "BARBER"})
    void nonAdminsCannotAccessAnyAdministrativeOperation(String role) throws Exception {
        var user = accounts.register(accountRequest());
        if (role.equals("BARBER")) {
            jdbc.update("update account_roles set role = 'BARBER' where account_id = ?", user.id());
        }
        String token = tokens.issue(user.id()).accessToken();
        for (String resource : List.of("branches", "services", "barbers")) {
            String path = "/api/v1/admin/" + resource;
            String id = UUID.randomUUID().toString();
            call(get(path), token, 403);
            call(get(path + "/" + id), token, 403);
            call(post(path).contentType("application/json").content("{}"), token, 403);
            call(patch(path + "/" + id + "/deactivate"), token, 403);
            if (!resource.equals("barbers")) {
                call(put(path + "/" + id).contentType("application/json").content("{}"), token, 403);
            }
            call(get("/api/v1/" + resource), token, 200);
        }
        call(post("/api/v1/admin/barbers/assign").contentType("application/json").content("{}"), token, 403);
    }

    @Test
    void adminCreatesUpdatesAndSoftDeletesBranchesAndServices() throws Exception {
        var branch = newBranch();
        String branchId = branch.get("id").asText();
        var changed = branchPayload();
        changed.put("name", "Sucursal actualizada");
        changed.put("address", "Dirección ficticia");
        var updated = call(body(put("/api/v1/admin/branches/" + branchId), changed), adminToken, 200);
        assertThat(updated.get("name").asText()).isEqualTo("Sucursal actualizada");
        call(patch("/api/v1/admin/branches/" + branchId + "/deactivate"), adminToken, 204);
        call(get("/api/v1/branches/" + branchId), adminToken, 404);
        assertThat(call(get("/api/v1/admin/branches/" + branchId), adminToken, 200).get("active").asBoolean()).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from branch_hours where branch_id = ?", Integer.class, UUID.fromString(branchId))).isEqualTo(7);

        var service = call(body(post("/api/v1/admin/services"), servicePayload("25.50")), adminToken, 201);
        String serviceId = service.get("id").asText();
        var serviceUpdate = servicePayload("35.00");
        serviceUpdate.put("type", "HAIRCUT");
        var result = call(body(put("/api/v1/admin/services/" + serviceId), serviceUpdate), adminToken, 200);
        assertThat(result.get("price").decimalValue()).isEqualByComparingTo("35.00");
        assertThat(result.get("type").asText()).isEqualTo("HAIRCUT");
        call(patch("/api/v1/admin/services/" + serviceId + "/deactivate"), adminToken, 204);
        call(get("/api/v1/services/" + serviceId), adminToken, 404);
        assertThat(call(get("/api/v1/admin/services/" + serviceId), adminToken, 200).get("active").asBoolean()).isFalse();
        call(get("/api/v1/admin/branches"), adminToken, 200);
        call(get("/api/v1/admin/services"), adminToken, 200);
    }

    @Test
    void invalidSchedulesAreRejectedOnCreateAndUpdate() throws Exception {
        String id = newBranch().get("id").asText();
        List<Map<String, Object>> invalidDays = List.of(
                Map.of("day","MONDAY","closed",false,"opensAt","18:00","closesAt","08:00"),
                Map.of("day","MONDAY","closed",false,"opensAt","08:00","closesAt","18:00","breakStart","07:00","breakEnd","09:00"),
                Map.of("day","MONDAY","closed",false,"opensAt","08:00","closesAt","18:00","breakStart","11:00","breakEnd","10:00"),
                Map.of("day","MONDAY","closed",false,"opensAt","08:00","closesAt","18:00","breakStart","10:00"),
                Map.of("day","MONDAY","closed",true,"opensAt","08:00"),
                Map.of("day","SUNDAY","closed",true));
        for (var day : invalidDays) {
            var payload = branchPayload();
            var hours = hours();
            hours.set(0, day);
            payload.put("weeklyHours", hours);
            call(body(post("/api/v1/admin/branches"), payload), adminToken, 400);
            call(body(put("/api/v1/admin/branches/" + id), payload), adminToken, 400);
        }
        var shortWeek = branchPayload();
        shortWeek.put("weeklyHours", hours().subList(0, 6));
        call(body(post("/api/v1/admin/branches"), shortWeek), adminToken, 400);
        assertThat(jdbc.queryForObject("select count(*) from branch_hours where branch_id = ?", Integer.class, UUID.fromString(id))).isEqualTo(7);
        assertThrows(DataIntegrityViolationException.class, () -> jdbc.update(
                "update branch_hours set closes_at = opens_at where branch_id = ? and day = 'MONDAY'", UUID.fromString(id)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-10.00", "1.001", "10000000000.00"})
    void invalidPricesAreRejected(String price) throws Exception {
        call(body(post("/api/v1/admin/services"), servicePayload(price)), adminToken, 400);
    }

    @Test
    void paginationSortingAndIdentifiersAreValidated() throws Exception {
        for (String resource : List.of("branches","services","barbers")) {
            String path = "/api/v1/" + resource;
            for (String query : List.of("?page=-1","?size=0","?size=101","?sort=passwordHash","?sort=name,sideways","?page=bad")) {
                call(get(path + query), adminToken, 400);
            }
            call(get(path + "/not-a-uuid"), adminToken, 400);
            call(get(path + "/" + UUID.randomUUID()), adminToken, 404);
            var page = call(get(path + "?size=1&sort=name,desc"), adminToken, 200);
            assertThat(page.get("items").size()).isLessThanOrEqualTo(1);
            assertThat(page.get("page").asInt()).isZero();
            assertThat(page.get("size").asInt()).isEqualTo(1);
        }
        call(get("/api/v1/barbers?branchId=bad"), adminToken, 400);
    }

    @Test
    void createsBarberAccountAndProfileWithExistingLogin() throws Exception {
        var branch = newBranch();
        var request = accountRequest();
        var profile = createBarber(request, branch.get("id").asText(), 201);
        UUID accountId = jdbc.queryForObject("select id from accounts where email = ?", UUID.class, request.email());
        assertThat(jdbc.queryForList("select role from account_roles where account_id = ?", String.class, accountId)).containsExactly("BARBER");
        assertThat(jdbc.queryForObject("select count(*) from barber_profiles where account_id = ? and branch_id = ?", Integer.class,
                accountId, UUID.fromString(branch.get("id").asText()))).isEqualTo(1);
        var login = call(body(post("/api/v1/auth/login"), Map.of("email",request.email().toUpperCase(Locale.ROOT),"password",request.password())), null, 200);
        var me = call(get("/api/v1/users/me"), login.get("accessToken").asText(), 200);
        assertThat(me.get("id").asText()).isEqualTo(accountId.toString());
        assertThat(profile.has("email")).isFalse();
        var duplicate = createBarber(request, branch.get("id").asText(), 409);
        assertThat(duplicate.get("code").asText()).isEqualTo("EMAIL_ALREADY_REGISTERED");
        var elevated = new HashMap<String,Object>(Map.of("account", Map.of("name",request.name(),"phone",request.phone(),
                "email",UUID.randomUUID()+"@example.test","password",request.password(),"roles",List.of("ADMIN")), "branchId",branch.get("id").asText()));
        call(body(post("/api/v1/admin/barbers"), elevated), adminToken, 400);
    }

    @Test
    void profileFailureRollsBackNewAccountAndRoles() throws Exception {
        var request = accountRequest();
        String branchId = newBranch().get("id").asText();
        doThrow(new DataIntegrityViolationException("Simulated profile persistence failure"))
                .when(barbers).saveAndFlush(argThat(profile -> profile.getAccount().getEmail().equals(request.email())));
        try {
            createBarber(request, branchId, 409);
            assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, request.email())).isZero();
        } finally { reset(barbers); }
    }

    @Test
    void assignsOwnerWithoutChangingPasswordAndDeactivatesOnlyProfile() throws Exception {
        var owner = accountRequest();
        accounts.bootstrapAdministrator(owner);
        UUID accountId = jdbc.queryForObject("select id from accounts where email = ?", UUID.class, owner.email());
        String oldHash = jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, accountId);
        String branchId = newBranch().get("id").asText();
        var profile = assign(accountId, branchId, 201);
        assertThat(jdbc.queryForList("select role from account_roles where account_id = ?", String.class, accountId)).containsExactlyInAnyOrder("ADMIN","BARBER");
        assertThat(oldHash.equals(jdbc.queryForObject("select password_hash from accounts where id = ?", String.class, accountId))).isTrue();
        assign(accountId, branchId, 409);
        call(patch("/api/v1/admin/barbers/" + profile.get("id").asText() + "/deactivate"), adminToken, 204);
        assertThat(jdbc.queryForObject("select active from accounts where id = ?", Boolean.class, accountId)).isTrue();
        assertThat(jdbc.queryForList("select role from account_roles where account_id = ?", String.class, accountId)).containsExactlyInAnyOrder("ADMIN","BARBER");
        var login = call(body(post("/api/v1/auth/login"),Map.of("email",owner.email(),"password",owner.password())), null, 200);
        call(get("/api/v1/admin/barbers/" + profile.get("id").asText()), login.get("accessToken").asText(), 200);
        call(get("/api/v1/barbers/" + profile.get("id").asText()), adminToken, 404);
    }

    @Test
    void inactiveBranchRejectsNewAndExistingAccountsWithoutPartialChanges() throws Exception {
        String branchId = newBranch().get("id").asText();
        call(patch("/api/v1/admin/branches/" + branchId + "/deactivate"), adminToken, 204);
        var request = accountRequest();
        createBarber(request, branchId, 409);
        assertThat(jdbc.queryForObject("select count(*) from accounts where email = ?", Integer.class, request.email())).isZero();
        var existing = accounts.register(accountRequest());
        assign(existing.id(), branchId, 409);
        assertThat(jdbc.queryForList("select role from account_roles where account_id = ?", String.class, existing.id())).containsExactly("CLIENT");
        assign(UUID.randomUUID(), newBranch().get("id").asText(), 404);
    }

    @Test
    void concurrentAssignmentProducesExactlyOneProfileAndPreservesClientRole() throws Exception {
        var account = accounts.register(accountRequest());
        String firstBranch = newBranch().get("id").asText();
        String secondBranch = newBranch().get("id").asText();
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            List<Future<Integer>> results = new ArrayList<>();
            for (String branchId : List.of(firstBranch,secondBranch)) {
                results.add(executor.submit(() -> {
                    start.await();
                    return mvc.perform(body(post("/api/v1/admin/barbers/assign"), Map.of("accountId",account.id(),"branchId",branchId))
                            .header("Authorization","Bearer "+adminToken)).andReturn().getResponse().getStatus();
                }));
            }
            start.countDown();
            assertThat(List.of(results.get(0).get(20,TimeUnit.SECONDS),results.get(1).get(20,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(jdbc.queryForObject("select count(*) from barber_profiles where account_id = ?", Integer.class, account.id())).isEqualTo(1);
        assertThat(jdbc.queryForList("select role from account_roles where account_id = ?", String.class, account.id())).containsExactlyInAnyOrder("CLIENT","BARBER");
    }

    @Test
    void catalogFiltersAllUnavailableBarbersAndNeverExposesPrivateFields() throws Exception {
        String branchId = newBranch().get("id").asText();
        var visible = createBarber(accountRequest(), branchId, 201);
        var inactiveProfile = createBarber(accountRequest(), branchId, 201);
        call(patch("/api/v1/admin/barbers/" + inactiveProfile.get("id").asText() + "/deactivate"), adminToken, 204);
        var disabledAccount = accountRequest();
        var inactiveAccountProfile = createBarber(disabledAccount, branchId, 201);
        jdbc.update("update accounts set active = false where email = ?",disabledAccount.email());
        var listing = call(get("/api/v1/barbers?branchId="+branchId), adminToken, 200);
        assertThat(listing.get("totalElements").asLong()).isEqualTo(1);
        var item = listing.get("items").get(0);
        assertThat(item.get("id").asText()).isEqualTo(visible.get("id").asText());
        Set<String> fields = new HashSet<>();
        item.fieldNames().forEachRemaining(fields::add);
        assertThat(fields).containsExactlyInAnyOrder("id","name","branch");
        assertThat(item.get("branch").size()).isEqualTo(2);
        call(get("/api/v1/barbers/"+inactiveAccountProfile.get("id").asText()), adminToken, 404);
        assertThat(call(get("/api/v1/admin/barbers?branchId="+branchId), adminToken, 200).get("totalElements").asLong()).isEqualTo(3);
        call(patch("/api/v1/admin/branches/"+branchId+"/deactivate"), adminToken, 204);
        assertThat(call(get("/api/v1/barbers?branchId="+branchId), adminToken, 200).get("totalElements").asLong()).isZero();
        call(get("/api/v1/barbers/"+visible.get("id").asText()), adminToken, 404);
    }

    @Test
    void serviceLayerAlsoRejectsNonAdminInvocation() {
        assertThrows(org.springframework.security.core.AuthenticationException.class,
                () -> branches.create(new BranchRequest("No autorizado",null,List.of())));
    }

    private JsonNode newBranch() throws Exception { return call(body(post("/api/v1/admin/branches"),branchPayload()),adminToken,201); }
    private JsonNode assign(UUID accountId,String branchId,int expected) throws Exception {
        return call(body(post("/api/v1/admin/barbers/assign"),Map.of("accountId",accountId,"branchId",branchId)),adminToken,expected);
    }
    private JsonNode createBarber(RegisterRequest account,String branchId,int expected) throws Exception {
        return call(body(post("/api/v1/admin/barbers"),Map.of("account",account,"branchId",branchId)),adminToken,expected);
    }
    private JsonNode call(MockHttpServletRequestBuilder request,String token,int expected) throws Exception {
        if (token != null) { request.header("Authorization","Bearer "+token); }
        var response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        if (expected >= 400) { assertThat(response.getContentType()).startsWith("application/json"); }
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(value));
    }
    private static RegisterRequest accountRequest() {
        return new RegisterRequest("Cuenta de prueba","5550000000",UUID.randomUUID()+"@example.test",UUID.randomUUID().toString());
    }
    private static Map<String,Object> branchPayload() { return new HashMap<>(Map.of("name","Sucursal "+UUID.randomUUID(),"weeklyHours",hours())); }
    private static List<Map<String,Object>> hours() {
        List<Map<String,Object>> hours = new ArrayList<>();
        for (var day : java.time.DayOfWeek.values()) {
            hours.add(day == java.time.DayOfWeek.SUNDAY ? Map.of("day",day.name(),"closed",true) :
                    Map.of("day",day.name(),"closed",false,"opensAt","08:00","closesAt","18:00","breakStart","10:00","breakEnd","11:00"));
        }
        return hours;
    }
    private static Map<String,Object> servicePayload(String price) {
        return new HashMap<>(Map.of("name","Servicio "+UUID.randomUUID(),"price",new BigDecimal(price),"type","ADD_ON"));
    }
}
