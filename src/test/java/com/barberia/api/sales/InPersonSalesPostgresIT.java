package com.barberia.api.sales;

import com.barberia.api.appointments.entity.*;
import com.barberia.api.appointments.repository.AppointmentRepository;
import com.barberia.api.auth.service.JwtService;
import com.barberia.api.products.repository.InventoryMovementRepository;
import com.barberia.api.services.repository.ServiceRepository;
import com.barberia.api.support.PostgresTestDatabase;
import com.fasterxml.jackson.databind.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.*;
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

@SpringBootTest
@AutoConfigureMockMvc(print=MockMvcPrint.NONE,printOnlyOnFailure=false)
@Import(InPersonSalesPostgresIT.TimeConfig.class)
class InPersonSalesPostgresIT {
    static final ZoneId ZONE=ZoneId.of("America/Mexico_City");
    static final LocalDate DATE=LocalDate.of(2032,2,16);
    static class TestClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(DATE.atTime(12,0).atZone(ZONE).toInstant());
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        @Override public Instant instant() { return now.get(); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class TimeConfig { @Bean @Primary TestClock salesClock() { return new TestClock(); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService tokens;
    @Autowired TestClock clock;
    @Autowired TransactionTemplate tx;
    @Autowired AppointmentRepository appointments;
    @Autowired ServiceRepository services;
    @MockitoSpyBean InventoryMovementRepository movements;
    UUID admin,barberActor,otherBarberActor,client,otherClient,barber,otherBarber,branch,otherBranch,service;
    int appointmentSequence;
    String hash;

    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("barberia_auth_test");
        clock.now.set(DATE.atTime(12,0).atZone(ZONE).toInstant()); appointmentSequence=0;
        hash=encoder.encode(UUID.randomUUID().toString());
        admin=account("ADMIN"); barberActor=account("BARBER"); otherBarberActor=account("BARBER");
        client=account("CLIENT"); otherClient=account("CLIENT");
        branch=branch(); otherBranch=branch(); barber=profile(barberActor,branch); otherBarber=profile(otherBarberActor,otherBranch);
        service=UUID.randomUUID();
        jdbc.update("insert into catalog_services(id,name,price,type,active) values (?,'Corte ficticio',145.25,'HAIRCUT',true)",service);
    }
    @Test void chargeUsesHistoricalTotalAndOwnPaymentDoesNotExposeStaffData() throws Exception {
        UUID id=appointment("COMPLETED",barber,branch);
        jdbc.update("update catalog_services set price=999.99 where id=?",service);
        assertThat(call(get("/api/v1/appointments/"+id+"/payment"),client,200).get("paid").asBoolean()).isFalse();
        var paid=charge(id,barberActor,201);
        assertThat(paid.get("amount").decimalValue()).isEqualByComparingTo("145.25");
        assertThat(paid.get("paidAt").asText()).isEqualTo(clock.instant().toString());
        charge(id,admin,409);
        var own=call(get("/api/v1/appointments/"+id+"/payment"),client,200);
        assertThat(own.get("paid").asBoolean()).isTrue();
        assertThat(own.toString()).doesNotContain("actorId","password","email","branchId");
        call(get("/api/v1/appointments/"+id+"/payment"),otherClient,404);
        call(get("/api/v1/staff/appointments/"+id+"/charge"),barberActor,200);
    }
    @Test void chargeRejectsUnfinishedNoShowAndCancelledAppointments() throws Exception {
        for (String state:List.of("CONFIRMED","IN_PROGRESS","CANCELLED","NO_SHOW"))
            charge(appointment(state,barber,branch),admin,409);
        assertThat(jdbc.queryForObject("select count(*) from appointment_charges where branch_id=?",Integer.class,branch)).isZero();
    }
    @Test void chargeRequiresOwnBarberEvenWithinSameBranchAndActiveResources() throws Exception {
        UUID sameBranchBarber=profile(account("BARBER"),branch);
        charge(appointment("COMPLETED",sameBranchBarber,branch),barberActor,404);
        UUID id=appointment("COMPLETED",otherBarber,otherBranch);
        charge(id,barberActor,404); charge(id,admin,201);
        UUID own=appointment("COMPLETED",barber,branch);
        jdbc.update("update barber_profiles set active=false where id=?",barber);
        charge(own,barberActor,409);
        jdbc.update("update branches set active=false where id=?",branch);
        charge(own,admin,409);
    }
    @Test void concurrentChargesCreateExactlyOneAndPostgresAlsoEnforcesUniqueness() throws Exception {
        UUID id=appointment("COMPLETED",barber,branch);
        var statuses=race(
                ()->status(body(post("/api/v1/staff/appointments/"+id+"/charge"),Map.of("method","CASH")),barberActor),
                ()->status(body(post("/api/v1/staff/appointments/"+id+"/charge"),Map.of("method","CARD")),admin));
        assertThat(statuses).containsExactlyInAnyOrder(201,409);
        assertThat(jdbc.queryForObject("select count(*) from appointment_charges where appointment_id=?",Integer.class,id)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("insert into appointment_charges(id,appointment_id,branch_id,actor_id,amount,method,paid_at) values (?,?,?,?,145.25,'CASH',now())",
                UUID.randomUUID(),id,branch,admin)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void multiLineSaleSnapshotsPricesAndLinksStockMovements() throws Exception {
        UUID first=product("10.25",8),second=product("20.50",6);
        var sale=sell(admin,UUID.randomUUID(),sale(branch,"CARD",first,2,second,3),201);
        assertThat(sale.get("total").decimalValue()).isEqualByComparingTo("82.00");
        assertThat(stock(first,branch)).isEqualTo(6); assertThat(stock(second,branch)).isEqualTo(3);
        jdbc.update("update products set price=999.99,name='Otro nombre',sku=? where id=?",("CHANGED-"+UUID.randomUUID()).toUpperCase(Locale.ROOT),first);
        var saved=call(get("/api/v1/staff/sales/"+sale.get("id").asText()),admin,200);
        assertThat(saved).isEqualTo(sale);
        assertThat(saved.toString()).doesNotContain("password","email");
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where sale_id=? and kind='SALE'",Integer.class,
                UUID.fromString(sale.get("id").asText()))).isEqualTo(2);
        assertThat(jdbc.queryForList("select quantity_before+delta=quantity_after as valid from inventory_movements where sale_id=?",Boolean.class,
                UUID.fromString(sale.get("id").asText()))).containsOnly(true);
    }
    @Test void saleIdempotencyReplaysOriginalAndRejectsDifferentContent() throws Exception {
        UUID first=product("10.25",8),second=product("20.50",6),key=UUID.randomUUID();
        var initial=sell(barberActor,key,sale(null,"CASH",first,2,second,1),201);
        jdbc.update("update products set active=false,price=100 where id=?",first);
        var replay=sell(barberActor,key,sale(branch,"CASH",second,1,first,2),200);
        assertThat(replay).isEqualTo(initial);
        sell(barberActor,key,sale(branch,"CARD",first,2,second,1),409);
        assertThat(stock(first,branch)).isEqualTo(6);
        assertThat(jdbc.queryForObject("select count(*) from product_sales where actor_id=? and idempotency_key=?",Integer.class,barberActor,key)).isEqualTo(1);
    }
    @Test void concurrentSameKeyProducesOneSaleAndOneStockDeduction() throws Exception {
        UUID first=product("10.00",5),key=UUID.randomUUID(); var payload=sale(branch,"CASH",first,2);
        var statuses=race(()->status(saleRequest(key,payload),barberActor),()->status(saleRequest(key,payload),barberActor));
        assertThat(statuses).containsExactlyInAnyOrder(201,200);
        assertThat(stock(first,branch)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from product_sales where actor_id=?",Integer.class,barberActor)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where product_id=? and kind='SALE'",Integer.class,first)).isEqualTo(1);
    }
    @Test void insufficientLineRollsBackAllProductsSaleOperationAndMovements() throws Exception {
        UUID first=product("10.00",5),second=product("20.00",1),key=UUID.randomUUID();
        sell(admin,key,sale(branch,"CASH",first,2,second,2),409);
        assertThat(stock(first,branch)).isEqualTo(5); assertThat(stock(second,branch)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from sale_operations where actor_id=? and idempotency_key=?",Integer.class,admin,key)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from product_sales where branch_id=?",Integer.class,branch)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from inventory_movements where product_id in (?,?) and kind='SALE'",Integer.class,first,second)).isZero();
    }
    @Test void failureAfterSaleAndStockWriteRollsBackEverything() throws Exception {
        UUID first=product("10.00",5),second=product("20.00",5),key=UUID.randomUUID();
        doAnswer(invocation->{
            var movement=(com.barberia.api.products.entity.InventoryMovement)invocation.getArgument(0);
            if (movement.getKind().equals("SALE")) throw new IllegalStateException("Fallo ficticio");
            return invocation.callRealMethod();
        }).when(movements).saveAndFlush(any());
        sell(admin,key,sale(branch,"CASH",first,2,second,2),500);
        assertThat(stock(first,branch)).isEqualTo(5); assertThat(stock(second,branch)).isEqualTo(5);
        assertThat(jdbc.queryForObject("select count(*) from product_sales where branch_id=?",Integer.class,branch)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from sale_operations where actor_id=? and idempotency_key=?",Integer.class,admin,key)).isZero();
    }
    @Test void competingSalesDoNotOversellAndReversedLinesUseStableLockOrder() throws Exception {
        UUID first=product("10.00",3),second=product("20.00",3);
        var statuses=race(()->status(saleRequest(UUID.randomUUID(),sale(branch,"CASH",first,2,second,2)),admin),
                ()->status(saleRequest(UUID.randomUUID(),sale(null,"CARD",second,2,first,2)),barberActor));
        assertThat(statuses).containsExactlyInAnyOrder(201,409);
        assertThat(stock(first,branch)).isEqualTo(1); assertThat(stock(second,branch)).isEqualTo(1);
    }
    @Test void salesRejectOtherBranchesInactiveResourcesAndForgedAmounts() throws Exception {
        UUID first=product("10.00",5);
        sell(barberActor,UUID.randomUUID(),sale(otherBranch,"CASH",first,1),404);
        call(body(post("/api/v1/staff/sales").header("Idempotency-Key",UUID.randomUUID()),
                Map.of("branchId",branch,"method","CASH","total",1,"items",List.of(Map.of("productId",first,"quantity",1)))),admin,400);
        sell(admin,UUID.randomUUID(),sale(branch,"CASH",first,1,first,1),400);
        sell(admin,UUID.randomUUID(),sale(branch,"CASH",first,0),400);
        call(body(post("/api/v1/staff/sales"),sale(branch,"CASH",first,1)),admin,400);
        jdbc.update("update products set active=false where id=?",first);
        sell(admin,UUID.randomUUID(),sale(branch,"CASH",first,1),409);
        jdbc.update("update products set active=true where id=?",first);
        jdbc.update("update barber_profiles set active=false where id=?",barber);
        sell(barberActor,UUID.randomUUID(),sale(null,"CASH",first,1),409);
        jdbc.update("update branches set active=false where id=?",branch);
        sell(admin,UUID.randomUUID(),sale(branch,"CASH",first,1),409);
    }
    @Test void clientCannotChargeSellReadSalesOrReportAndInactiveAccountsAreRejected() throws Exception {
        UUID id=appointment("COMPLETED",barber,branch),first=product("10.00",5);
        charge(id,client,403);
        sell(client,UUID.randomUUID(),sale(branch,"CASH",first,1),403);
        var sale=sell(admin,UUID.randomUUID(),sale(branch,"CASH",first,1),201);
        call(get("/api/v1/staff/sales/"+sale.get("id").asText()),otherBarberActor,404);
        call(get("/api/v1/staff/sales/"+sale.get("id").asText()),client,403);
        call(get("/api/v1/admin/reports/income").param("period","DAY").param("date",DATE.toString()),client,403);
        call(get("/api/v1/admin/reports/income").param("period","DAY").param("date",DATE.toString()),barberActor,403);
        jdbc.update("update accounts set active=false where id=?",barberActor);
        charge(id,barberActor,401);
        sell(null,UUID.randomUUID(),sale(branch,"CASH",first,1),401);
    }
    @ParameterizedTest
    @CsvSource({"DAY,2032-02-16,2032-02-16,2032-02-17","WEEK,2032-02-18,2032-02-16,2032-02-23","MONTH,2032-03-15,2032-03-01,2032-04-01"})
    void reportsUsePaymentTimeAndHalfOpenBusinessZonePeriods(String period,String anchor,String from,String until) throws Exception {
        Instant start=LocalDate.parse(from).atStartOfDay(ZONE).toInstant(),end=LocalDate.parse(until).atStartOfDay(ZONE).toInstant();
        UUID first=product("10.50",20);
        for (Instant time:List.of(start.minusSeconds(1),start,end.minusSeconds(1),end)) {
            clock.now.set(time);
            charge(appointment("COMPLETED",barber,branch),admin,201);
            sell(admin,UUID.randomUUID(),sale(branch,"CASH",first,1),201);
        }
        // These appointments have no payment and must not inflate income or counts.
        appointment("COMPLETED",barber,branch); appointment("CONFIRMED",barber,branch);
        var report=call(get("/api/v1/admin/reports/income").param("period",period).param("date",anchor)
                .param("branchId",branch.toString()),admin,200);
        assertThat(report.get("startsOn").asText()).isEqualTo(from);
        assertThat(report.get("endsBefore").asText()).isEqualTo(until);
        assertThat(report.get("startsAt").asText()).isEqualTo(start.toString());
        assertThat(report.get("servicesTotal").decimalValue()).isEqualByComparingTo("290.50");
        assertThat(report.get("productsTotal").decimalValue()).isEqualByComparingTo("21.00");
        assertThat(report.get("combinedTotal").decimalValue()).isEqualByComparingTo("311.50");
        assertThat(report.get("serviceCharges").asInt()).isEqualTo(2);
        assertThat(report.get("productSales").asInt()).isEqualTo(2);
        assertThat(report.get("basis").asText()).isEqualTo("COLLECTED_INCOME_WITHOUT_REFUNDS");
    }
    @Test void reportsFilterBranchesAndAdminCanSeeBoth() throws Exception {
        charge(appointment("COMPLETED",barber,branch),admin,201);
        charge(appointment("COMPLETED",otherBarber,otherBranch),admin,201);
        var own=call(get("/api/v1/admin/reports/income").param("period","DAY").param("date",DATE.toString())
                .param("branchId",branch.toString()),admin,200);
        assertThat(own.get("serviceCharges").asInt()).isEqualTo(1);
        var all=call(get("/api/v1/admin/reports/income").param("period","DAY").param("date",DATE.toString()),admin,200);
        assertThat(all.get("serviceCharges").asLong()).isGreaterThanOrEqualTo(2);
    }
    @Test void swaggerIsAccessibleAndDeclaresBearerAndImplementedPaths() throws Exception {
        call(get("/swagger-ui/index.html"),null,200);
        var spec=call(get("/v3/api-docs"),null,200);
        assertThat(spec.at("/components/securitySchemes/bearerAuth/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.get("paths").has("/api/v1/staff/sales")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1auth~1login/post/security").isArray()).isTrue();
    }

    private UUID account(String role) {
        UUID id=UUID.randomUUID();
        jdbc.update("insert into accounts(id,name,phone,email,password_hash,active,created_at) values (?,'Cuenta ficticia','0000000000',?,?,true,now())",
                id,id+"@example.test",hash);
        jdbc.update("insert into account_roles(account_id,role) values (?,?)",id,role); return id;
    }
    private UUID branch() {
        UUID id=UUID.randomUUID(); jdbc.update("insert into branches(id,name,active) values (?,'Sucursal ficticia',true)",id); return id;
    }
    private UUID profile(UUID actor,UUID branchId) {
        UUID id=UUID.randomUUID(); jdbc.update("insert into barber_profiles(id,account_id,branch_id,active) values (?,?,?,true)",id,actor,branchId); return id;
    }
    private UUID appointment(String state,UUID barberId,UUID branchId) {
        int index=appointmentSequence++;
        Instant start=DATE.minusDays(20).plusDays(index/8).atTime(8+index%8,0).atZone(ZONE).toInstant();
        return tx.execute(status->{
            var a=new Appointment(client,barberId,branchId,start,List.of(new BookedService(services.findById(service).orElseThrow())),start.minusSeconds(3600));
            if (state.equals("IN_PROGRESS") || state.equals("COMPLETED")) a.begin(start);
            if (state.equals("COMPLETED")) a.complete(start.plusSeconds(3600));
            if (state.equals("NO_SHOW")) a.noShow(start.plusSeconds(3600));
            if (state.equals("CANCELLED")) a.cancel(admin,start.minusSeconds(60),"Motivo ficticio","STAFF");
            return appointments.saveAndFlush(a).getId();
        });
    }
    private UUID product(String price,long quantity) throws Exception {
        UUID id=UUID.fromString(call(body(post("/api/v1/admin/products"),Map.of("sku","DEMO-"+UUID.randomUUID(),
                "name","Producto ficticio","price",price)),admin,201).get("id").asText());
        call(body(post("/api/v1/admin/products/"+id+"/inventory/entries"),Map.of("branchId",branch,"quantity",quantity,"reason","Entrada ficticia")),admin,201);
        return id;
    }
    private long stock(UUID product,UUID branchId) { return jdbc.queryForObject("select quantity from product_stocks where product_id=? and branch_id=?",Long.class,product,branchId); }
    private JsonNode charge(UUID id,UUID actor,int expected) throws Exception { return call(body(post("/api/v1/staff/appointments/"+id+"/charge"),Map.of("method","CASH")),actor,expected); }
    private Map<String,Object> sale(UUID branchId,String method,Object... productQuantities) {
        var result=new HashMap<String,Object>(); result.put("branchId",branchId); result.put("method",method);
        var items=new ArrayList<Map<String,Object>>();
        for(int i=0;i<productQuantities.length;i+=2) items.add(Map.of("productId",productQuantities[i],"quantity",productQuantities[i+1]));
        result.put("items",items); return result;
    }
    private MockHttpServletRequestBuilder saleRequest(UUID key,Object payload) throws Exception { return body(post("/api/v1/staff/sales").header("Idempotency-Key",key),payload); }
    private JsonNode sell(UUID actor,UUID key,Object payload,int expected) throws Exception { return call(saleRequest(key,payload),actor,expected); }
    private JsonNode call(MockHttpServletRequestBuilder request,UUID actor,int expected) throws Exception {
        var result=perform(request,actor);
        assertThat(result.getResponse().getStatus()).withFailMessage("HTTP esperado %s; recibido %s; error %s",expected,
                result.getResponse().getStatus(),result.getResolvedException()==null?"ninguno":result.getResolvedException().getClass().getSimpleName()).isEqualTo(expected);
        var response=result.getResponse();
        if (expected>=400) assertThat(response.getContentType()).startsWith("application/json");
        if (response.getContentType()!=null && response.getContentType().contains("text/html")) return json.nullNode();
        return response.getContentAsString().isBlank()?json.nullNode():json.readTree(response.getContentAsString());
    }
    private MvcResult perform(MockHttpServletRequestBuilder request,UUID actor) throws Exception {
        if(actor!=null) request.header("Authorization","Bearer "+tokens.issue(actor).accessToken());
        return mvc.perform(request).andReturn();
    }
    private int status(MockHttpServletRequestBuilder request,UUID actor) throws Exception {
        var result=perform(request,actor);
        if(result.getResponse().getStatus()>=500) throw new AssertionError("Fallo concurrente",result.getResolvedException());
        return result.getResponse().getStatus();
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder request,Object payload) throws Exception { return request.contentType("application/json").content(json.writeValueAsBytes(payload)); }
    private List<Integer> race(Callable<Integer> first,Callable<Integer> second) throws Exception {
        var ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ready.countDown(); if(!ready.await(20,TimeUnit.SECONDS)) throw new AssertionError(); return first.call();});
            var b=pool.submit(()->{ready.countDown(); if(!ready.await(20,TimeUnit.SECONDS)) throw new AssertionError(); return second.call();});
            return List.of(a.get(30,TimeUnit.SECONDS),b.get(30,TimeUnit.SECONDS));
        }
    }
}
