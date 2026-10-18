package com.barberia.api.products;

import com.barberia.api.accounts.service.AccountService;
import com.barberia.api.auth.dto.RegisterRequest;
import com.barberia.api.auth.service.JwtService;
import com.barberia.api.products.repository.InventoryMovementRepository;
import com.barberia.api.products.repository.ProductRepository;
import com.barberia.api.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE, printOnlyOnFailure = false)
class ProductInventoryPostgresIT {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountService accounts;
    @Autowired JwtService tokens;
    @MockitoSpyBean InventoryMovementRepository movements;
    @MockitoSpyBean ProductRepository products;
    String adminToken, clientToken, barberToken;
    UUID adminId, barberId, firstBranch, secondBranch;

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("select current_database()", String.class)).isEqualTo("barberia_auth_test");
        var admin = account();
        accounts.bootstrapAdministrator(admin);
        adminId = jdbc.queryForObject("select id from accounts where email=?", UUID.class, admin.email());
        adminToken = tokens.issue(adminId).accessToken();
        var client = accounts.register(account());
        clientToken = tokens.issue(client.id()).accessToken();
        var barber = accounts.register(account());
        barberId = barber.id();
        jdbc.update("update account_roles set role='BARBER' where account_id=?", barber.id());
        barberToken = tokens.issue(barber.id()).accessToken();
        firstBranch = UUID.randomUUID(); secondBranch = UUID.randomUUID();
        jdbc.update("insert into branches(id,name,active) values (?,?,true)", firstBranch, "Sucursal prueba A");
        jdbc.update("insert into branches(id,name,active) values (?,?,true)", secondBranch, "Sucursal prueba B");
        jdbc.update("insert into barber_profiles(id,account_id,branch_id,active) values (?,?,?,true)",
                UUID.randomUUID(), barber.id(), firstBranch);
    }

    @Test void skuNormalizationUniquenessAndPostgresConstraint() throws Exception {
        var product = create("  demo-"+UUID.randomUUID()+"  ", "25.50", 201);
        String id = product.get("id").asText(), sku = product.get("sku").asText();
        assertThat(sku).isEqualTo(sku.toUpperCase(Locale.ROOT)).doesNotStartWith(" ");
        assertThat(create(sku.toLowerCase(Locale.ROOT), "30.00", 409).get("code").asText())
                .isEqualTo("BUSINESS_CONFLICT");
        assertThatThrownBy(() -> jdbc.update(
                "insert into products(id,sku,name,price,active) values (?,?,?,25.00,true)",
                UUID.randomUUID(), sku, "Duplicado")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(() -> jdbc.update(
                "insert into products(id,sku,name,price,active) values (?,?,?,25.00,true)",
                UUID.randomUUID(), " con espacio ", "Inválido")).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(call(get("/api/v1/admin/products/"+id), adminToken, 200).get("sku").asText()).isEqualTo(sku);
    }
    @Test void priceAndQuantityValidation() throws Exception {
        create("sku-"+UUID.randomUUID(), "0", 400);
        create("sku-"+UUID.randomUUID(), "-1.00", 400);
        create("sku-"+UUID.randomUUID(), "1.001", 400);
        create("bad sku", "10.00", 400);
        String id = create("sku-"+UUID.randomUUID(), "19.99", 201).get("id").asText();
        move(id, "entries", firstBranch, 0, 400);
        move(id, "entries", firstBranch, -1, 400);
        move(id, "adjustments", firstBranch, 0, 400);
        call(body(post("/api/v1/admin/products/"+id+"/inventory/entries"),
                Map.of("branchId", firstBranch, "quantity", 1.5, "reason", "Cantidad fraccionaria")), adminToken, 400);
        assertThat(movementCount(id)).isZero();
    }
    @Test void concurrentNormalizedSkuCreationReturnsCreatedAndConflict() throws Exception {
        String sku = ("RACE-"+UUID.randomUUID()).toUpperCase(Locale.ROOT);
        var ready = new CountDownLatch(2);
        // Both requests pass the pre-check; the real PostgreSQL unique constraint decides the winner.
        doAnswer(invocation -> {
            ready.countDown();
            if (!ready.await(20, TimeUnit.SECONDS)) throw new AssertionError("No llegaron ambas solicitudes.");
            return false;
        }).when(products).existsBySku(sku);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> callStatus(body(post("/api/v1/admin/products"),
                    Map.of("sku", sku.toLowerCase(Locale.ROOT), "name", "Producto ficticio", "price", "20.00")), adminToken));
            var second = pool.submit(() -> callStatus(body(post("/api/v1/admin/products"),
                    Map.of("sku", "  "+sku+"  ", "name", "Producto ficticio", "price", "20.00")), adminToken));
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(jdbc.queryForObject("select count(*) from products where sku=?", Integer.class, sku)).isEqualTo(1);
    }
    @Test void totalRemainsExactBeyondLongRangeAndPostgresRejectsNegativeStock() throws Exception {
        String id = create("sku-"+UUID.randomUUID(), "12.00", 201).get("id").asText();
        move(id, "entries", firstBranch, Long.MAX_VALUE, 201);
        move(id, "entries", secondBranch, Long.MAX_VALUE, 201);
        var result = call(get("/api/v1/admin/products/"+id), adminToken, 200);
        assertThat(result.get("totalStock").bigIntegerValue())
                .isEqualTo(java.math.BigInteger.valueOf(Long.MAX_VALUE).multiply(java.math.BigInteger.TWO));
        assertThatThrownBy(() -> jdbc.update("update product_stocks set quantity=-1 where product_id=? and branch_id=?",
                UUID.fromString(id), firstBranch)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(stockIn(call(get("/api/v1/products/"+id), clientToken, 200), firstBranch)).isEqualTo(Long.MAX_VALUE);
        assertThat(movementCount(id)).isEqualTo(2);
    }
    @Test void adminUpdatesCatalogWithoutChangingStockOrMovementHistory() throws Exception {
        String id = create("sku-"+UUID.randomUUID(), "19.99", 201).get("id").asText();
        String otherSku = "sku-"+UUID.randomUUID();
        create(otherSku, "15.00", 201);
        move(id, "entries", firstBranch, 4, 201);
        call(body(put("/api/v1/admin/products/"+id),
                Map.of("sku", otherSku, "name", "Duplicado", "price", "30.00")), adminToken, 409);
        var updated = call(body(put("/api/v1/admin/products/"+id),
                Map.of("sku", "new-"+UUID.randomUUID(), "name", "Producto actualizado", "price", "30.25")), adminToken, 200);
        assertThat(updated.get("price").decimalValue()).isEqualByComparingTo("30.25");
        assertThat(stockIn(updated, firstBranch)).isEqualTo(4);
        assertThat(movementCount(id)).isEqualTo(1);
    }
    @Test void independentBranchesZeroStockTotalsAndHistory() throws Exception {
        String id = create("sku-"+UUID.randomUUID(), "49.90", 201).get("id").asText();
        var zero = call(get("/api/v1/products/"+id), clientToken, 200);
        assertThat(zero.get("totalStock").asLong()).isZero();
        assertThat(stockIn(zero, firstBranch)).isZero();
        assertThat(stockIn(zero, secondBranch)).isZero();
        var entry = move(id, "entries", firstBranch, 7, 201);
        assertThat(entry.get("kind").asText()).isEqualTo("ENTRY");
        assertThat(entry.get("actorId").asText()).isEqualTo(adminId.toString());
        assertThat(entry.get("quantityBefore").asLong()).isZero();
        move(id, "entries", secondBranch, 3, 201);
        move(id, "adjustments", firstBranch, -2, 201);
        var result = call(get("/api/v1/products/"+id), clientToken, 200);
        assertThat(result.get("totalStock").asLong()).isEqualTo(8);
        assertThat(stockIn(result, firstBranch)).isEqualTo(5);
        assertThat(stockIn(result, secondBranch)).isEqualTo(3);
        assertThat(result.toString()).doesNotContain("actorId", "password", "passwordHash", "email");
        var history = call(get("/api/v1/admin/products/"+id+"/inventory/movements"), adminToken, 200);
        assertThat(history.get("totalElements").asInt()).isEqualTo(3);
        assertThat(history.get("items").get(0).get("reason").asText()).isEqualTo("Motivo de prueba");
    }
    @Test void insufficientAdjustmentDoesNotCreateMovement() throws Exception {
        String id = create("sku-"+UUID.randomUUID(), "12.00", 201).get("id").asText();
        move(id, "entries", firstBranch, 2, 201);
        assertThat(move(id, "adjustments", firstBranch, -3, 409).get("message").asText())
                .contains("insuficientes");
        assertThat(movementCount(id)).isEqualTo(1);
        assertThat(stockIn(call(get("/api/v1/products/"+id), clientToken, 200), firstBranch)).isEqualTo(2);
    }
    @Test void concurrentAdjustmentsDoNotLoseUnitsOrGoNegative() throws Exception {
        String id = create("sku-"+UUID.randomUUID(), "12.00", 201).get("id").asText();
        move(id, "entries", firstBranch, 5, 201);
        var ready = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { ready.countDown(); ready.await(20, TimeUnit.SECONDS);
                return move(id, "adjustments", firstBranch, -4, 201).get("quantityAfter").asLong(); });
            var second = pool.submit(() -> { ready.countDown(); ready.await(20, TimeUnit.SECONDS);
                return move(id, "adjustments", firstBranch, 3, 201).get("quantityAfter").asLong(); });
            first.get(30, TimeUnit.SECONDS); second.get(30, TimeUnit.SECONDS);
        }
        assertThat(stockIn(call(get("/api/v1/products/"+id), clientToken, 200), firstBranch)).isEqualTo(4);
        assertThat(movementCount(id)).isEqualTo(3);
        var simultaneous = new CountDownLatch(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { simultaneous.countDown(); simultaneous.await(20, TimeUnit.SECONDS);
                return callStatus(body(post("/api/v1/admin/products/"+id+"/inventory/adjustments"),
                        Map.of("branchId", firstBranch, "quantity", -3, "reason", "Ajuste ficticio")), adminToken); });
            var second = pool.submit(() -> { simultaneous.countDown(); simultaneous.await(20, TimeUnit.SECONDS);
                return callStatus(body(post("/api/v1/admin/products/"+id+"/inventory/adjustments"),
                        Map.of("branchId", firstBranch, "quantity", -3, "reason", "Ajuste ficticio")), adminToken); });
            assertThat(List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(201, 409);
        }
        assertThat(stockIn(call(get("/api/v1/products/"+id), clientToken, 200), firstBranch)).isEqualTo(1);
        assertThat(movementCount(id)).isEqualTo(4);
    }
    @Test void stockAndMovementRollbackTogetherOnDatabaseFailure() throws Exception {
        String id = create("sku-"+UUID.randomUUID(), "12.00", 201).get("id").asText();
        UUID productId = UUID.fromString(id);
        doThrow(new IllegalStateException("Fallo ficticio")).when(movements).saveAndFlush(any());
        call(body(post("/api/v1/admin/products/"+id+"/inventory/entries"),
                Map.of("branchId", firstBranch, "quantity", 5, "reason", "Motivo de prueba")), adminToken, 500);
        assertThat(jdbc.queryForObject("select count(*) from product_stocks where product_id=?", Integer.class, productId)).isZero();
        assertThat(movementCount(id)).isZero();
    }
    @Test void rolesInactiveResourcesAndResponsePrivacy() throws Exception {
        String id = create("sku-"+UUID.randomUUID(), "12.00", 201).get("id").asText();
        move(id, "entries", firstBranch, 2, 201);
        move(id, "entries", secondBranch, 3, 201);
        call(get("/api/v1/products/"+id), null, 401);
        call(get("/api/v1/products/"+id), barberToken, 403);
        call(get("/api/v1/staff/products/"+id), clientToken, 403);
        call(get("/api/v1/admin/products/"+id), clientToken, 403);
        call(body(post("/api/v1/admin/products/"+id+"/inventory/entries"),
                Map.of("branchId", firstBranch, "quantity", 1, "reason", "Prueba")), barberToken, 403);
        var own = call(get("/api/v1/staff/products/"+id), barberToken, 200);
        assertThat(own.get("branches").size()).isEqualTo(1);
        assertThat(stockIn(own, firstBranch)).isEqualTo(2);
        assertThat(own.toString()).doesNotContain(secondBranch.toString(), "email", "password", "actorId");
        jdbc.update("insert into account_roles(account_id,role) values (?,'CLIENT')", barberId);
        assertThat(call(get("/api/v1/products/"+id), barberToken, 200).get("branches").size()).isGreaterThan(1);
        assertThat(call(get("/api/v1/staff/products/"+id), barberToken, 200).get("branches").size()).isEqualTo(1);
        jdbc.update("update branches set active=false where id=?", secondBranch);
        move(id, "entries", secondBranch, 1, 409);
        assertThat(call(get("/api/v1/products/"+id), clientToken, 200).get("branches").size()).isGreaterThan(0);
        assertThat(call(get("/api/v1/admin/products/"+id), adminToken, 200).get("branches").size())
                .isGreaterThan(call(get("/api/v1/products/"+id), clientToken, 200).get("branches").size());
        call(patch("/api/v1/admin/products/"+id+"/deactivate"), adminToken, 204);
        call(get("/api/v1/products/"+id), clientToken, 404);
        move(id, "entries", firstBranch, 1, 409);
        assertThat(call(get("/api/v1/admin/products/"+id), adminToken, 200).get("active").asBoolean()).isFalse();
        assertThat(movementCount(id)).isEqualTo(2);
    }
    @Test void pagingFilteringAndMissingProducts() throws Exception {
        String id = create("unique_"+UUID.randomUUID(), "15.00", 201).get("id").asText();
        String sku = call(get("/api/v1/admin/products/"+id), adminToken, 200).get("sku").asText();
        assertThat(call(get("/api/v1/products").param("search", sku), clientToken, 200)
                .get("totalElements").asInt()).isEqualTo(1);
        call(get("/api/v1/products").param("sort", "passwordHash,asc"), clientToken, 400);
        call(get("/api/v1/products").param("size", "101"), clientToken, 400);
        call(get("/api/v1/admin/products/"+UUID.randomUUID()), adminToken, 404);
        call(get("/api/v1/admin/products/"+id+"/inventory/movements").param("sort", "actorId,desc"), adminToken, 400);
    }

    private JsonNode create(String sku, String price, int status) throws Exception {
        return call(body(post("/api/v1/admin/products"),
                Map.of("sku", sku, "name", "Producto de prueba", "price", price)), adminToken, status);
    }
    private JsonNode move(String id, String kind, UUID branch, long quantity, int status) throws Exception {
        return call(body(post("/api/v1/admin/products/"+id+"/inventory/"+kind),
                Map.of("branchId", branch, "quantity", quantity, "reason", "Motivo de prueba")), adminToken, status);
    }
    private long stockIn(JsonNode response, UUID branchId) {
        for (var row : response.get("branches"))
            if (row.get("branchId").asText().equals(branchId.toString())) return row.get("quantity").asLong();
        throw new AssertionError("Sucursal no incluida.");
    }
    private int movementCount(String id) {
        return jdbc.queryForObject("select count(*) from inventory_movements where product_id=?",
                Integer.class, UUID.fromString(id));
    }
    private JsonNode call(MockHttpServletRequestBuilder request, String token, int expected) throws Exception {
        if (token != null) request.header("Authorization", "Bearer "+token);
        var response = mvc.perform(request).andExpect(status().is(expected)).andReturn().getResponse();
        if (expected >= 400) assertThat(response.getContentType()).startsWith("application/json");
        return response.getContentAsString().isBlank() ? json.nullNode() : json.readTree(response.getContentAsString());
    }
    private int callStatus(MockHttpServletRequestBuilder request, String token) throws Exception {
        request.header("Authorization", "Bearer "+token);
        var response = mvc.perform(request).andReturn();
        if (response.getResponse().getStatus() >= 500)
            throw new AssertionError("Error interno en prueba concurrente.", response.getResolvedException());
        return response.getResponse().getStatus();
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request, Object value) throws Exception {
        return request.contentType("application/json").content(json.writeValueAsBytes(value));
    }
    private static RegisterRequest account() {
        return new RegisterRequest("Cuenta ficticia", "5550000000", UUID.randomUUID()+"@example.test",
                UUID.randomUUID().toString());
    }
}
