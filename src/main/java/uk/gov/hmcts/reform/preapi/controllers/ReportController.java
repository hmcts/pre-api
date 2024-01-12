package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.util.List;

@RestController
@RequestMapping("/reports")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/capture-sessions-concurrent")
    @Operation(operationId = "reportConcurrentCaptureSessions")
    public ResponseEntity<List<CaptureSessionReportDTO>> reportConcurrentCaptureSessions() {
        return ResponseEntity.ok(reportService.reportCaptureSessions());
    }

    @GetMapping("/recordings-per-case")
    @Operation(operationId = "reportRecordingsPerCase", summary = "Get the number of recordings by each case")
    public ResponseEntity<List<RecordingsPerCaseReportDTO>> reportRecordingsPerCase() {
        return ResponseEntity.ok(reportService.reportRecordingsPerCase());
    }
}
