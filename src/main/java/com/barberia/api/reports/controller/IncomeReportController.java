package com.barberia.api.reports.controller;
import com.barberia.api.reports.dto.*;
import com.barberia.api.reports.service.IncomeReportService;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/admin/reports/income")
public class IncomeReportController {
    private final IncomeReportService reports;
    public IncomeReportController(IncomeReportService reports) { this.reports=reports; }
    @GetMapping
    public IncomeReport income(@RequestParam ReportPeriod period,
            @RequestParam @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required=false) UUID branchId) { return reports.income(period,date,branchId); }
}
