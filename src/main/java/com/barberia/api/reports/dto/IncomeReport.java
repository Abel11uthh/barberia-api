package com.barberia.api.reports.dto;
import java.math.BigDecimal;
import java.time.*;
import java.util.UUID;
public record IncomeReport(ReportPeriod period, LocalDate startsOn, LocalDate endsBefore, String timeZone,
        Instant startsAt, Instant endsBeforeInstant, UUID branchId, BigDecimal servicesTotal,
        BigDecimal productsTotal, BigDecimal combinedTotal, long serviceCharges, long productSales,
        String currency, String basis) { }
