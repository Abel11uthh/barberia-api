package com.barberia.api.reports.repository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
@Repository
public class IncomeReportRepository {
    public record Totals(BigDecimal amount,long count) { }
    private final JdbcTemplate jdbc;
    public IncomeReportRepository(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    public Totals services(Instant start,Instant end,UUID branchId) {
        return jdbc.queryForObject("select coalesce(sum(amount),0) as amount, count(*) as count from appointment_charges " +
                "where paid_at>=? and paid_at<? and (?::uuid is null or branch_id=?::uuid)",
                (row,index)->new Totals(row.getBigDecimal("amount"),row.getLong("count")),
                Timestamp.from(start),Timestamp.from(end),branchId,branchId);
    }
    public Totals products(Instant start,Instant end,UUID branchId) {
        return jdbc.queryForObject("select coalesce(sum(total),0) as amount, count(*) as count from product_sales " +
                "where paid_at>=? and paid_at<? and (?::uuid is null or branch_id=?::uuid)",
                (row,index)->new Totals(row.getBigDecimal("amount"),row.getLong("count")),
                Timestamp.from(start),Timestamp.from(end),branchId,branchId);
    }
}
