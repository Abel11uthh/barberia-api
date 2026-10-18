package com.barberia.api.sales.repository;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class SaleOperationRepository {
    private final JdbcTemplate jdbc;
    public SaleOperationRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public void lock(UUID actor, UUID key) {
        jdbc.update("insert into sale_operations(actor_id,idempotency_key) values (?,?) on conflict do nothing",actor,key);
        jdbc.queryForObject("select idempotency_key from sale_operations where actor_id=? and idempotency_key=? for update",
                UUID.class,actor,key);
    }
}
