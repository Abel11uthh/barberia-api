package com.barberia.api.reports.service;
import com.barberia.api.branches.repository.BranchRepository;
import com.barberia.api.reports.dto.*;
import com.barberia.api.reports.repository.IncomeReportRepository;
import com.barberia.api.shared.error.NotFoundException;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
@Service
public class IncomeReportService {
    public static final ZoneId ZONE=ZoneId.of("America/Mexico_City");
    private final IncomeReportRepository reports;
    private final BranchRepository branches;
    public IncomeReportService(IncomeReportRepository reports,BranchRepository branches) {
        this.reports=reports; this.branches=branches;
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    @PreAuthorize("hasRole('ADMIN')")
    public IncomeReport income(ReportPeriod period,LocalDate date,UUID branchId) {
        if (date.getYear()<1 || date.getYear()>9998) throw new IllegalArgumentException();
        if (branchId!=null && !branches.existsById(branchId)) throw new NotFoundException();
        LocalDate start=switch(period) {
            case DAY -> date;
            case WEEK -> date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            case MONTH -> date.withDayOfMonth(1);
        };
        LocalDate end=switch(period) {
            case DAY -> start.plusDays(1);
            case WEEK -> start.plusWeeks(1);
            case MONTH -> start.plusMonths(1);
        };
        Instant from=start.atStartOfDay(ZONE).toInstant(), until=end.atStartOfDay(ZONE).toInstant();
        var services=reports.services(from,until,branchId); var products=reports.products(from,until,branchId);
        return new IncomeReport(period,start,end,ZONE.getId(),from,until,branchId,services.amount(),products.amount(),
                services.amount().add(products.amount()),services.count(),products.count(),"MXN","COLLECTED_INCOME_WITHOUT_REFUNDS");
    }
}
