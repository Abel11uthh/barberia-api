package com.barberia.api.appointments;

import com.barberia.api.accounts.entity.*;
import com.barberia.api.accounts.repository.AccountRepository;
import com.barberia.api.auth.service.JwtService;
import com.barberia.api.appointments.service.AgendaAvailability;
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
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
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
@Import(AgendaOperationsPostgresIT.TimeConfig.class)
class AgendaOperationsPostgresIT {
    static final LocalDate DATE=LocalDate.of(2030,1,7);
    static Instant at(String time) { return DATE.atTime(LocalTime.parse(time)).atZone(ZoneId.of("America/Mexico_City")).toInstant(); }
    static class TestClock extends Clock {
        final AtomicReference<Instant> now=new AtomicReference<>(at("07:00"));
        final ThreadLocal<Instant> threadTime=new ThreadLocal<>();
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(instant(),zone); }
        @Override public Instant instant() { return threadTime.get()==null?now.get():threadTime.get(); }
    }
    @TestConfiguration(proxyBeanMethods=false)
    static class TimeConfig { @Bean @Primary TestClock agendaClock() { return new TestClock(); } }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry registry) { PostgresTestDatabase.configure(registry); }
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired AccountRepository accounts;
    @Autowired BranchRepository branches;
    @Autowired BarberRepository barbers;
    @Autowired ServiceRepository services;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtService tokens;
    @Autowired TestClock clock;
    @MockitoSpyBean AgendaAvailability availability;
    UUID client, otherClient, admin, barberAccount, otherBarberAccount, barber, otherBarber, branch, otherBranch, haircut, addon;
    String clientPassword;
    @BeforeEach void setup() {
        assertThat(jdbc.queryForObject("select current_database()",String.class)).isEqualTo("barberia_auth_test");
        clock.now.set(at("07:00")); clock.threadTime.remove();
        clientPassword=UUID.randomUUID().toString();
        String hash=encoder.encode(clientPassword);
        tx.executeWithoutResult(status->{
            client=accounts.save(account(hash,Role.CLIENT)).getId();
            otherClient=accounts.save(account(hash,Role.CLIENT)).getId();
            admin=accounts.save(account(hash,Role.ADMIN)).getId();
            var ba=accounts.save(account(hash,Role.BARBER)); barberAccount=ba.getId();
            var ob=accounts.save(account(hash,Role.BARBER)); otherBarberAccount=ob.getId();
            var hours=Arrays.stream(DayOfWeek.values()).map(day->day==DayOfWeek.SUNDAY
                    ?new HoursRequest(day,true,null,null,null,null)
                    :new HoursRequest(day,false,LocalTime.of(8,0),LocalTime.of(18,0),LocalTime.of(10,0),LocalTime.of(11,0))).toList();
            var b=branches.save(new Branch(new BranchRequest("Sucursal ficticia",null,hours))); branch=b.getId();
            var b2=branches.save(new Branch(new BranchRequest("Otra sucursal ficticia",null,hours))); otherBranch=b2.getId();
            barber=barbers.save(new BarberProfile(ba,b)).getId();
            otherBarber=barbers.save(new BarberProfile(ob,b2)).getId();
            haircut=services.save(new CatalogService(new ServiceRequest("Corte ficticio",null,new BigDecimal("100.10"),ServiceType.HAIRCUT))).getId();
            addon=services.save(new CatalogService(new ServiceRequest("Extra ficticio",null,new BigDecimal("25.25"),ServiceType.ADD_ON))).getId();
        });
    }
    @AfterEach void resetState() { reset(availability); clock.threadTime.remove(); }
    private Account account(String hash,Role role) {
        return new Account("Persona ficticia","0000000000",UUID.randomUUID()+"@example.test",hash,Set.of(role));
    }
    private MockHttpServletRequestBuilder body(MockHttpServletRequestBuilder r,Object input) throws Exception {
        return r.contentType("application/json").content(json.writeValueAsBytes(input));
    }
    private MvcResult perform(MockHttpServletRequestBuilder r,UUID actor) throws Exception {
        if(actor!=null) r.header("Authorization","Bearer "+tokens.issue(actor).accessToken());
        return mvc.perform(r).andReturn();
    }
    private JsonNode call(MockHttpServletRequestBuilder r,UUID actor,int expected) throws Exception {
        var result=perform(r,actor);
        assertThat(result.getResponse().getStatus()).isEqualTo(expected);
        String data=result.getResponse().getContentAsString();
        return data.isEmpty()?json.nullNode():json.readTree(data);
    }
    private Map<String,Object> reservation(String time) {
        return new HashMap<>(Map.of("barberId",barber,"date",DATE.toString(),"time",time,
                "primaryServiceId",haircut,"addOnIds",List.of(addon)));
    }
    private Map<String,Object> walk() {
        return new HashMap<>(Map.of("barberId",barber,"guestName","Invitado ficticio",
                "guestPhone","0000000000","primaryServiceId",haircut,"addOnIds",List.of(addon)));
    }
    private JsonNode book(String time) throws Exception {
        return call(body(post("/api/v1/appointments"),reservation(time)),client,201);
    }
    private JsonNode walk(UUID actor,int expected) throws Exception {
        return call(body(post("/api/v1/staff/appointments/walk-ins"),walk()),actor,expected);
    }
    private JsonNode action(String id,String action,UUID actor,int expected) throws Exception {
        return call(post("/api/v1/staff/appointments/"+id+"/"+action),actor,expected);
    }
    private JsonNode cancel(String id,UUID actor,boolean staff,Object request,int expected) throws Exception {
        return call(body(post("/api/v1/"+(staff?"staff/":"")+"appointments/"+id+"/cancel"),request),actor,expected);
    }
    private Map<String,Object> block(String start,String end) {
        return Map.of("barberId",barber,"startsAt",at(start).toString(),"endsAt",at(end).toString(),"reason","Motivo ficticio");
    }
    private long occupied() {
        return jdbc.queryForObject("select count(*) from agenda_occupations where barber_id=? and active",Long.class,barber);
    }

    @Test void immediateUsesServerMinuteAndCreatesNoAccountOrPhoneLink() throws Exception {
        long count=accounts.count();
        clock.now.set(at("14:25:49"));
        var result=walk(barberAccount,201);
        var a=result.get("appointment");
        assertThat(a.get("startsAt").asText()).isEqualTo(at("14:25").toString());
        assertThat(a.get("endsAt").asText()).isEqualTo(at("15:25").toString());
        assertThat(a.get("origin").asText()).isEqualTo("WALK_IN");
        assertThat(a.get("status").asText()).isEqualTo("IN_PROGRESS");
        assertThat(a.get("total").decimalValue()).isEqualByComparingTo("125.35");
        assertThat(result.get("createdBy").asText()).isEqualTo(barberAccount.toString());
        assertThat(result.get("customer").get("guest").asBoolean()).isTrue();
        assertThat(accounts.count()).isEqualTo(count);
        assertThat(jdbc.queryForObject("select client_id from appointments where id=?",UUID.class,UUID.fromString(a.get("id").asText()))).isNull();
        assertThat(result.toString()).doesNotContain("password","hash","token","email");
        call(get("/api/v1/appointments/"+a.get("id").asText()),client,404);
        for(String field:List.of("startsAt","time","clientId","status","createdBy")) {
            var spoof=walk(); spoof.put(field,"invalid");
            call(body(post("/api/v1/staff/appointments/walk-ins"),spoof),admin,400);
        }
        jdbc.update("update catalog_services set name='Cambio', price=500 where id in (?,?)",haircut,addon);
        var detail=call(get("/api/v1/staff/appointments/"+a.get("id").asText()),admin,200);
        assertThat(detail).isEqualTo(result);
    }
    @Test void scheduledAtFifteenPreventsFourteenTwentyFiveWalkIn() throws Exception {
        book("15:00"); clock.now.set(at("14:25")); walk(admin,409);
    }
    @Test void overlappingGuestsAndInsufficientGapBeforeSixteenAreRejected() throws Exception {
        book("16:00");
        clock.now.set(at("14:25")); walk(admin,201);
        clock.now.set(at("15:10")); walk(admin,409);
        clock.now.set(at("15:25")); walk(admin,409);
        assertThat(occupied()).isEqualTo(2);
    }
    @Test void adjacentImmediateIntervalsAndOtherBarbersAreAllowed() throws Exception {
        clock.now.set(at("14:25")); walk(admin,201);
        var other=walk(); other.put("barberId",otherBarber);
        call(body(post("/api/v1/staff/appointments/walk-ins"),other),admin,201);
        clock.now.set(at("15:25")); walk(admin,201);
        assertThat(occupied()).isEqualTo(2);
    }
    @ParameterizedTest @ValueSource(strings={"07:59","09:25","10:30","17:01"})
    void immediateRespectsOpeningBreakAndClosing(String time) throws Exception {
        clock.now.set(at(time)); walk(admin,409);
    }
    @Test void immediateFitsBreakBoundaryAndClosingExactly() throws Exception {
        clock.now.set(at("09:00")); walk(admin,201);
        clock.now.set(at("17:00")); walk(admin,201);
        clock.now.set(DATE.plusDays(6).atTime(14,25).atZone(ZoneId.of("America/Mexico_City")).toInstant());
        walk(admin,409);
    }
    @Test void barberScopeAndStaffAgendaPrivacy() throws Exception {
        var own=book("14:00"); var other=reservation("14:00"); other.put("barberId",otherBarber);
        call(body(post("/api/v1/appointments"),other),otherClient,201);
        var ownPage=call(get("/api/v1/staff/appointments").param("date",DATE.toString()),barberAccount,200);
        assertThat(ownPage.get("totalElements").asInt()).isEqualTo(1);
        assertThat(ownPage.get("items").get(0).get("customer").get("phone").asText()).isEqualTo("0000000000");
        call(get("/api/v1/staff/appointments/"+own.get("id").asText()),otherBarberAccount,404);
        call(get("/api/v1/staff/appointments").param("date",DATE.toString()).param("barberId",barber.toString()),otherBarberAccount,404);
        call(get("/api/v1/staff/appointments").param("date",DATE.toString()),client,403);
        call(get("/api/v1/staff/appointments").param("date",DATE.toString()),null,401);
        var adminPage=call(get("/api/v1/staff/appointments").param("date",DATE.toString()).param("branchId",branch.toString())
                .param("status","CONFIRMED").param("size","1"),admin,200);
        assertThat(adminPage.get("totalElements").asInt()).isEqualTo(1);
        call(get("/api/v1/staff/appointments").param("date",DATE.toString()).param("status","INVALID"),admin,400);
        call(get("/api/v1/staff/appointments").param("date",DATE.toString()).param("size","101"),admin,400);
        clock.now.set(at("12:25"));
        walk(client,403); walk(otherBarberAccount,404);
        walk(barberAccount,201);
    }
    @Test void serviceAndActiveChecksAreReusedForWalkIns() throws Exception {
        clock.now.set(at("14:25"));
        var invalid=walk(); invalid.put("primaryServiceId",addon); invalid.put("addOnIds",List.of());
        call(body(post("/api/v1/staff/appointments/walk-ins"),invalid),admin,400);
        invalid=walk(); invalid.put("addOnIds",List.of(addon,addon));
        call(body(post("/api/v1/staff/appointments/walk-ins"),invalid),admin,400);
        jdbc.update("update catalog_services set active=false where id=?",addon); walk(admin,404);
        jdbc.update("update catalog_services set active=true where id=?",addon);
        jdbc.update("update barber_profiles set active=false where id=?",barber); walk(admin,404);
    }
    @Test void stateTransitionsAreGuardedAndIdempotentAndCompletionKeepsOccupancy() throws Exception {
        String id=book("14:00").get("id").asText();
        action(id,"start",barberAccount,409);
        action(id,"complete",barberAccount,409);
        action(id,"no-show",barberAccount,409);
        clock.now.set(at("14:00"));
        var started=action(id,"start",barberAccount,200);
        assertThat(action(id,"start",barberAccount,200)).isEqualTo(started);
        action(id,"start",otherBarberAccount,404);
        action(id,"complete",client,403);
        clock.now.set(at("14:10"));
        action(id,"complete",admin,409);
        clock.now.set(at("14:25")); walk(admin,409);
        clock.now.set(at("15:00"));
        var complete=action(id,"complete",admin,200);
        assertThat(action(id,"complete",admin,200)).isEqualTo(complete);
        action(id,"start",admin,409); action(id,"no-show",admin,409);
        cancel(id,admin,true,Map.of("reason","Motivo"),409);
        assertThat(occupied()).isEqualTo(1);
        assertThat(complete.get("appointment").get("endsAt").asText()).isEqualTo(at("15:00").toString());
        assertThat(complete.toString()).doesNotContain("payment","paid");
    }
    @Test void noShowOnlyAfterEndWithoutStartAndKeepsHistory() throws Exception {
        String id=book("14:00").get("id").asText();
        clock.now.set(at("14:59")); action(id,"no-show",admin,409);
        clock.now.set(at("15:00"));
        var marked=action(id,"no-show",admin,200);
        assertThat(action(id,"no-show",barberAccount,200)).isEqualTo(marked);
        action(id,"start",admin,409); action(id,"complete",admin,409);
        cancel(id,admin,true,Map.of("reason","Motivo"),409);
        assertThat(occupied()).isEqualTo(1);
        var history=call(get("/api/v1/staff/appointments").param("date",DATE.toString())
                .param("barberId",barber.toString()).param("status","NO_SHOW"),admin,200);
        assertThat(history.get("totalElements").asInt()).isEqualTo(1);
    }
    @Test void clientCancellationAtSixtyMinutesReleasesBlockAndPreservesAudit() throws Exception {
        String id=book("14:00").get("id").asText();
        clock.now.set(at("13:00"));
        var cancelled=cancel(id,client,false,Map.of(),200);
        assertThat(cancelled.get("cancellationOrigin").asText()).isEqualTo("CLIENT");
        assertThat(cancel(id,client,false,Map.of("reason","No sustituir"),200)).isEqualTo(cancelled);
        assertThat(occupied()).isZero();
        book("14:00");
        assertThat(jdbc.queryForObject("select count(*) from appointments where barber_id=?",Integer.class,barber)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select cancelled_by from appointments where id=?",UUID.class,UUID.fromString(id))).isEqualTo(client);
        assertThat(jdbc.queryForObject("select count(*) from agenda_occupations where barber_id=?",Integer.class,barber)).isEqualTo(2);
    }
    @Test void clientCancellationAtFiftyNineMinutesRejectedAndOtherClientsCannotCancel() throws Exception {
        String id=book("14:00").get("id").asText();
        cancel(id,otherClient,false,Map.of(),404);
        clock.now.set(at("13:01")); cancel(id,client,false,Map.of(),409);
        assertThat(occupied()).isEqualTo(1);
    }
    @Test void staffCancellationRequiresReasonHasNoLeadTimeAndIsNotAttributedToClient() throws Exception {
        String id=book("14:00").get("id").asText(); clock.now.set(at("14:30"));
        cancel(id,admin,true,Map.of(),400);
        cancel(id,barberAccount,true,Map.of("reason"," "),400);
        cancel(id,otherBarberAccount,true,Map.of("reason","Motivo"),404);
        var cancelled=cancel(id,barberAccount,true,Map.of("reason","Cierre extraordinario"),200);
        assertThat(cancelled.get("appointment").get("cancellationOrigin").asText()).isEqualTo("STAFF");
        assertThat(cancelled.get("cancelledBy").asText()).isEqualTo(barberAccount.toString());
        assertThat(cancel(id,admin,true,Map.of("reason","No sustituir"),200)).isEqualTo(cancelled);
        assertThat(occupied()).isZero();
        walk(admin,201);
    }
    @Test void blocksAreAdminOnlyValidateRangesAndReleaseWithoutDeleting() throws Exception {
        var request=block("14:25","15:25");
        call(body(post("/api/v1/admin/blocks"),request),client,403);
        call(body(post("/api/v1/admin/blocks"),request),barberAccount,403);
        call(body(post("/api/v1/admin/blocks"),block("14:25","14:25")),admin,400);
        call(body(post("/api/v1/admin/blocks"),block("06:00","07:00")),admin,400);
        var blocked=call(body(post("/api/v1/admin/blocks"),request),admin,201);
        assertThat(occupied()).isEqualTo(1);
        var free=call(get("/api/v1/appointments/availability").param("date",DATE.toString()).param("branchId",branch.toString())
                .param("barberId",barber.toString()),client,200);
        assertThat(free.get("slots").findValuesAsText("startsAt")).doesNotContain(at("14:00").toString(),at("15:00").toString());
        call(body(post("/api/v1/appointments"),reservation("15:00")),client,409);
        clock.now.set(at("14:30")); walk(admin,409);
        call(get("/api/v1/staff/blocks").param("date",DATE.toString()).param("barberId",barber.toString()),otherBarberAccount,404);
        assertThat(call(get("/api/v1/staff/blocks").param("date",DATE.toString()),barberAccount,200).get("totalElements").asInt()).isEqualTo(1);
        String cancelPath="/api/v1/admin/blocks/"+blocked.get("id").asText()+"/cancel";
        call(post(cancelPath),barberAccount,403);
        var cancelled=call(post(cancelPath),admin,200);
        assertThat(call(post(cancelPath),admin,200)).isEqualTo(cancelled);
        assertThat(occupied()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from barber_blocks where id=?",Integer.class,UUID.fromString(blocked.get("id").asText()))).isEqualTo(1);
        walk(admin,201);
    }
    @Test void blocksCannotDisplaceAppointmentsAndAdjacentBlocksAreValid() throws Exception {
        book("14:00");
        call(body(post("/api/v1/admin/blocks"),block("14:59","15:25")),admin,409);
        call(body(post("/api/v1/admin/blocks"),block("15:00","15:25")),admin,201);
        call(body(post("/api/v1/admin/blocks"),block("15:25","15:50")),admin,201);
        assertThat(occupied()).isEqualTo(3);
    }
    @Test void migrationBackfillsExistingReservationsAndUsesExclusion() {
        assertThat(jdbc.queryForObject("select count(*) from appointments where origin is null or created_by is null",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from appointments a left join agenda_occupations o on o.appointment_id=a.id where o.id is null",Long.class)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from pg_constraint where conname='agenda_no_overlap' and contype='x'",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from pg_extension where extname='btree_gist'",Integer.class)).isEqualTo(1);
    }
    private void race(Callable<Integer> first,Callable<Integer> second) throws Exception {
        CountDownLatch both=new CountDownLatch(2);
        doAnswer(inv->{
            inv.callRealMethod(); both.countDown();
            if(!both.await(30,TimeUnit.SECONDS)) throw new IllegalStateException("No llegaron ambas operaciones.");
            return null;
        }).when(availability).requireFree(eq(barber),any(),any());
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(first); var b=pool.submit(second);
            assertThat(List.of(a.get(45,TimeUnit.SECONDS),b.get(45,TimeUnit.SECONDS))).containsExactlyInAnyOrder(201,409);
        }
        assertThat(occupied()).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from appointments where barber_id=?",Long.class,barber)
                +jdbc.queryForObject("select count(*) from barber_blocks where barber_id=?",Long.class,barber)).isEqualTo(1);
    }
    private int concurrent(MockHttpServletRequestBuilder request,UUID actor,String now) throws Exception {
        clock.threadTime.set(at(now));
        try {
            var result=perform(request,actor);
            if(result.getResponse().getStatus()>=500)
                throw new AssertionError("Error interno en la prueba concurrente.",result.getResolvedException());
            return result.getResponse().getStatus();
        }
        finally { clock.threadTime.remove(); }
    }
    @Test void simultaneousWalkInsWithDifferentOverlappingStartsOnlyPersistOne() throws Exception {
        race(()->concurrent(body(post("/api/v1/staff/appointments/walk-ins"),walk()),admin,"14:25"),
                ()->concurrent(body(post("/api/v1/staff/appointments/walk-ins"),walk()),barberAccount,"14:30"));
    }
    @Test void reservationVersusImmediateCannotPersistOverlappingIntervals() throws Exception {
        race(()->concurrent(body(post("/api/v1/appointments"),reservation("15:00")),client,"14:25"),
                ()->concurrent(body(post("/api/v1/staff/appointments/walk-ins"),walk()),admin,"14:25"));
    }
    @Test void blockVersusReservationCannotPersistOverlappingIntervals() throws Exception {
        race(()->concurrent(body(post("/api/v1/admin/blocks"),block("14:25","15:25")),admin,"14:00"),
                ()->concurrent(body(post("/api/v1/appointments"),reservation("15:00")),client,"14:00"));
    }
    @Test void concurrentCancellationDoesNotOverwriteAudit() throws Exception {
        String id=book("14:00").get("id").asText();
        CountDownLatch ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            Callable<JsonNode> request=()->{
                ready.countDown(); if(!ready.await(20,TimeUnit.SECONDS)) throw new IllegalStateException();
                return cancel(id,client,false,Map.of(),200);
            };
            var a=pool.submit(request); var b=pool.submit(request);
            assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));
        }
        assertThat(occupied()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from agenda_occupations where appointment_id=?",Integer.class,UUID.fromString(id))).isEqualTo(1);
    }

    private JsonNode bookingStatus(UUID actor, int expected) throws Exception {
        return call(get("/api/v1/users/me/booking-status"), actor, expected);
    }
    private int incidentEvents(UUID accountId) {
        return jdbc.queryForObject("select count(*) from appointment_client_events where account_id=?",Integer.class,accountId);
    }
    private JsonNode reactivate(UUID actor, Object request, int expected) throws Exception {
        return call(body(post("/api/v1/admin/clients/"+client+"/reactivate"),request),actor,expected);
    }

    @Test void cancellationAndNoShowsWarnOnSecondAndBlockOnThirdWithoutLosingExistingAccess() throws Exception {
        String first=book("14:00").get("id").asText();
        String second=book("15:00").get("id").asText();
        String third=book("16:00").get("id").asText();
        String fourth=book("17:00").get("id").asText();
        var future=reservation("14:00"); future.put("date",DATE.plusDays(1).toString());
        String futureId=call(body(post("/api/v1/appointments"),future),client,201).get("id").asText();
        assertThat(cancel(first,client,false,Map.of(),200).get("bookingStatus").get("consecutiveAppointmentIncidents").asInt()).isEqualTo(1);
        assertThat(bookingStatus(client,200).get("warning").isNull()).isTrue();
        clock.now.set(at("16:00"));
        action(second,"no-show",barberAccount,200);
        var warned=bookingStatus(client,200);
        assertThat(warned.get("consecutiveAppointmentIncidents").asInt()).isEqualTo(2);
        assertThat(warned.get("warning").asText()).contains("Advertencia");
        clock.now.set(at("17:00"));
        action(third,"no-show",admin,200);
        var blocked=bookingStatus(client,200);
        assertThat(blocked.get("consecutiveAppointmentIncidents").asInt()).isEqualTo(3);
        assertThat(blocked.get("bookingBlocked").asBoolean()).isTrue();
        assertThat(bookingStatus(otherClient,200).get("consecutiveAppointmentIncidents").asInt()).isZero();
        bookingStatus(admin,403);
        assertThat(call(body(post("/api/v1/appointments"),reservation("17:00")),client,409).get("message").asText()).contains("bloqueada");
        call(get("/api/v1/appointments/"+fourth),client,200);
        call(get("/api/v1/appointments"),client,200);
        call(get("/api/v1/users/me"),client,200);
        String email=accounts.findById(client).orElseThrow().getEmail();
        call(body(post("/api/v1/auth/login"),Map.of("email",email,"password",clientPassword)),null,200);
        assertThat(incidentEvents(client)).isEqualTo(3);
        assertThat(jdbc.queryForObject("select count(*) from appointments where client_id=?",Integer.class,client)).isEqualTo(5);
        cancel(futureId,client,false,Map.of(),200);
        assertThat(incidentEvents(client)).isEqualTo(4);
    }

    @Test void completedResetsCounterButDoesNotLiftAnExistingCommercialBlock() throws Exception {
        String first=book("13:00").get("id").asText();
        String second=book("14:00").get("id").asText();
        String third=book("15:00").get("id").asText();
        String completed=book("16:00").get("id").asText();
        cancel(first,client,false,Map.of(),200);
        assertThat(cancel(second,client,false,Map.of(),200).get("bookingStatus").get("warning").asText()).contains("Advertencia");
        cancel(third,client,false,Map.of(),200);
        clock.now.set(at("16:00"));
        action(completed,"start",barberAccount,200);
        clock.now.set(at("17:00"));
        action(completed,"complete",barberAccount,200);
        var state=bookingStatus(client,200);
        assertThat(state.get("consecutiveAppointmentIncidents").asInt()).isZero();
        assertThat(state.get("bookingBlocked").asBoolean()).isTrue();
        assertThat(incidentEvents(client)).isEqualTo(4);
    }

    @Test void staffCancellationAndGuestCompletionDoNotCountAgainstClient() throws Exception {
        String reservationId=book("14:00").get("id").asText();
        cancel(reservationId,barberAccount,true,Map.of("reason","Cierre ficticio"),200);
        clock.now.set(at("14:25"));
        String guest=walk(admin,201).get("appointment").get("id").asText();
        clock.now.set(at("15:25"));
        action(guest,"complete",admin,200);
        assertThat(bookingStatus(client,200).get("consecutiveAppointmentIncidents").asInt()).isZero();
        assertThat(incidentEvents(client)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from appointment_client_events where appointment_id=?",Integer.class,UUID.fromString(guest))).isZero();
    }

    @Test void onlyAdminReactivatesWithReasonAndHistoryIsPreserved() throws Exception {
        String first=book("14:00").get("id").asText();
        String second=book("15:00").get("id").asText();
        String third=book("16:00").get("id").asText();
        cancel(first,client,false,Map.of(),200);
        cancel(second,client,false,Map.of(),200);
        cancel(third,client,false,Map.of(),200);
        reactivate(client,Map.of("reason","Solicitud"),403);
        jdbc.update("insert into account_roles(account_id, role) values (?, 'ADMIN')",client);
        reactivate(client,Map.of("reason","Solicitud"),403);
        reactivate(barberAccount,Map.of("reason","Solicitud"),403);
        reactivate(admin,Map.of("reason"," "),400);
        var result=reactivate(admin,Map.of("reason","Revisión ficticia"),200);
        assertThat(result.get("bookingBlocked").asBoolean()).isFalse();
        assertThat(result.get("consecutiveAppointmentIncidents").asInt()).isZero();
        assertThat(incidentEvents(client)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select reason from appointment_client_events where account_id=? and kind='ADMIN_REACTIVATED'",String.class,client)).isEqualTo("Revisión ficticia");
        assertThat(jdbc.queryForObject("select actor_id from appointment_client_events where account_id=? and kind='ADMIN_REACTIVATED'",UUID.class,client)).isEqualTo(admin);
        reactivate(admin,Map.of("reason","Repetida"),409);
        assertThat(incidentEvents(client)).isEqualTo(4);
        clock.now.set(at("07:01"));
        call(body(post("/api/v1/appointments"),reservation("17:00")),client,201);
    }

    @Test void repeatedTerminalTransitionCountsOnlyOnceAndPreservesActor() throws Exception {
        String id=book("14:00").get("id").asText();
        clock.now.set(at("15:00"));
        action(id,"no-show",barberAccount,200);
        action(id,"no-show",admin,200);
        assertThat(bookingStatus(client,200).get("consecutiveAppointmentIncidents").asInt()).isEqualTo(1);
        assertThat(incidentEvents(client)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select actor_id from appointment_client_events where appointment_id=?",UUID.class,UUID.fromString(id))).isEqualTo(barberAccount);
    }

    @Test void concurrentTerminalTransitionsOnDifferentAppointmentsDoNotLoseAnIncrement() throws Exception {
        String first=book("14:00").get("id").asText();
        String second=book("15:00").get("id").asText();
        CountDownLatch ready=new CountDownLatch(2);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=pool.submit(()->{ready.countDown(); ready.await(20,TimeUnit.SECONDS); return cancel(first,client,false,Map.of(),200);});
            var b=pool.submit(()->{ready.countDown(); ready.await(20,TimeUnit.SECONDS); return cancel(second,client,false,Map.of(),200);});
            a.get(30,TimeUnit.SECONDS); b.get(30,TimeUnit.SECONDS);
        }
        assertThat(bookingStatus(client,200).get("consecutiveAppointmentIncidents").asInt()).isEqualTo(2);
        assertThat(incidentEvents(client)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select count(distinct sequence) from appointment_client_events where account_id=?",Integer.class,client)).isEqualTo(2);
    }

    @Test void noShowWindowIncludesEndAndFifteenMinutesButNotLater() throws Exception {
        String atEnd=book("14:00").get("id").asText();
        String atLimit=book("16:00").get("id").asText();
        String tooLate=book("17:00").get("id").asText();
        clock.now.set(at("14:59:59")); action(atEnd,"no-show",admin,409);
        clock.now.set(at("15:00")); action(atEnd,"no-show",admin,200);
        clock.now.set(at("17:15")); action(atLimit,"no-show",barberAccount,200);
        clock.now.set(at("18:15"));
        assertThat(call(get("/api/v1/staff/appointments/"+tooLate),admin,200)
                .get("requiresAdministrativeReview").asBoolean()).isFalse();
        clock.now.set(at("18:15:01"));
        assertThat(action(tooLate,"no-show",admin,409).get("message").asText()).contains("ventana de 15 minutos");
        assertThat(action(atEnd,"no-show",admin,200).get("appointment").get("status").asText()).isEqualTo("NO_SHOW");
        var unresolved=call(get("/api/v1/staff/appointments/"+tooLate),admin,200);
        assertThat(unresolved.get("appointment").get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(unresolved.get("requiresAdministrativeReview").asBoolean()).isTrue();
        assertThat(bookingStatus(client,200).get("consecutiveAppointmentIncidents").asInt()).isEqualTo(2);
        assertThat(incidentEvents(client)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select active from agenda_occupations where appointment_id=?",Boolean.class,UUID.fromString(tooLate))).isTrue();
    }

    @Test void startedAppointmentCannotBecomeNoShow() throws Exception {
        String id=book("14:00").get("id").asText();
        clock.now.set(at("14:00")); action(id,"start",barberAccount,200);
        clock.now.set(at("15:00")); action(id,"no-show",admin,409);
        clock.now.set(at("15:01"));
        assertThat(call(get("/api/v1/staff/appointments/"+id),admin,200)
                .get("requiresAdministrativeReview").asBoolean()).isTrue();
        action(id,"complete",barberAccount,200);
        assertThat(bookingStatus(client,200).get("consecutiveAppointmentIncidents").asInt()).isZero();
        assertThat(incidentEvents(client)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select kind from appointment_client_events where appointment_id=?",String.class,UUID.fromString(id))).isEqualTo("COMPLETED");
    }

    @Test void mondayUnrecordedTuesdayCompletedWednesdayLateNoShowDoesNotPenalize() throws Exception {
        String monday=book("14:00").get("id").asText();
        var tuesdayRequest=reservation("14:00"); tuesdayRequest.put("date",DATE.plusDays(1).toString());
        String tuesday=call(body(post("/api/v1/appointments"),tuesdayRequest),client,201).get("id").asText();
        clock.now.set(DATE.plusDays(1).atTime(14,0).atZone(ZoneId.of("America/Mexico_City")).toInstant());
        action(tuesday,"start",barberAccount,200);
        clock.now.set(DATE.plusDays(1).atTime(15,0).atZone(ZoneId.of("America/Mexico_City")).toInstant());
        action(tuesday,"complete",barberAccount,200);
        clock.now.set(DATE.plusDays(2).atTime(15,0).atZone(ZoneId.of("America/Mexico_City")).toInstant());
        action(monday,"no-show",admin,409);
        var unresolved=call(get("/api/v1/staff/appointments/"+monday),admin,200);
        assertThat(unresolved.get("appointment").get("status").asText()).isEqualTo("CONFIRMED");
        assertThat(unresolved.get("requiresAdministrativeReview").asBoolean()).isTrue();
        assertThat(bookingStatus(client,200).get("consecutiveAppointmentIncidents").asInt()).isZero();
        assertThat(jdbc.queryForList("select kind from appointment_client_events where account_id=? order by sequence",String.class,client))
                .containsExactly("COMPLETED");
        var mondayAgenda=call(get("/api/v1/staff/appointments").param("date",DATE.toString())
                .param("status","CONFIRMED"),admin,200);
        assertThat(mondayAgenda.get("items").findValuesAsText("requiresAdministrativeReview")).contains("true");
    }
}
