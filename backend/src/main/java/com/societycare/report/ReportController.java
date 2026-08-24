package com.societycare.report;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Management reporting. Admin only - the export carries every flat's activity,
 * which is exactly the thing a resident must never be able to pull.
 */
@RestController
@RequestMapping("/api/v1/reports")
public class ReportController {

    private static final String XLSX_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ReportService reportService;
    private final ActivityReportWriter writer;

    public ReportController(ReportService reportService, ActivityReportWriter writer) {
        this.reportService = reportService;
        this.writer = writer;
    }

    /**
     * Complaints and suggestions raised between two dates, as a spreadsheet.
     * Both bounds are inclusive whole days in the report's configured zone.
     *
     * The dates default to the 1st of the current month through today, which is
     * both what the UI sends and what makes the endpoint usable straight from
     * Swagger.
     */
    @GetMapping("/activity")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Download complaints and suggestions raised in a date range as .xlsx")
    public ResponseEntity<byte[]> activity(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {

        LocalDate start = from != null ? from : reportService.defaultFrom();
        LocalDate end = to != null ? to : reportService.defaultTo();

        List<ReportRow> rows = reportService.buildRows(start, end);
        byte[] body = writer.write(rows, start, end, reportService.getZone());

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(XLSX_MEDIA_TYPE))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + writer.fileName(start, end) + "\"")
                // A report is a snapshot of a moment; a cached copy served after
                // new tickets arrive would be quietly wrong.
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(body);
    }
}
