package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.dto.reports.AccessRemovedReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.CaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.CompletedCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.PlaybackReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
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
    public ResponseEntity<List<ConcurrentCaptureSessionReportDTO>> reportConcurrentCaptureSessions() {
        return ResponseEntity.ok(reportService.reportCaptureSessions());
    }

    @GetMapping("/recordings-per-case")
    @Operation(
        operationId = "reportRecordingsPerCase",
        summary = "Get the number of completed capture sessions for each case"
    )
    public ResponseEntity<List<RecordingsPerCaseReportDTO>> reportRecordingsPerCase() {
        return ResponseEntity.ok(reportService.reportRecordingsPerCase());
    }

    @GetMapping("/edits")
    @Operation(operationId = "reportEdits", summary = "Get a report on recordings edits")
    public ResponseEntity<List<EditReportDTO>> reportEdits() {
        return ResponseEntity.ok(reportService.reportEdits());
    }

    @GetMapping("/shared-bookings")
    @Operation(operationId = "reportBookingsShared", summary = "Get a report on the bookings that have been shared")
    public ResponseEntity<List<SharedReportDTO>> reportBookingsShared() {
        return ResponseEntity.ok(reportService.reportShared());
    }

    @GetMapping("/schedules")
    @Operation(
        operationId = "reportSchedules",
        summary = "Get a list of completed capture sessions with booking details"
    )
    public ResponseEntity<List<ScheduleReportDTO>> reportSchedules() {
        return ResponseEntity.ok(reportService.reportScheduled());
    }


    @GetMapping("/playback")
    @Operation(
        operationId = "reportPlayback",
        summary = "Get report on playback by playback source (PORTAL, APPLICATION)"
    )
    public ResponseEntity<List<PlaybackReportDTO>> reportPlayback(
        @Parameter(
            name = "source",
            description = "The source of the playback. Only accepts PORTAL or APPLICATION",
            schema = @Schema(implementation = AuditLogSource.class),
            required = true
        ) AuditLogSource source
    ) {
        return ResponseEntity.ok(reportService.reportPlayback(source));
    }

    @GetMapping("/completed-capture-sessions")
    @Operation(
        operationId = "reportCompletedCaptureSessions",
        summary = "Get a report on capture sessions with available recordings"
    )
    public ResponseEntity<List<CompletedCaptureSessionReportDTO>> reportCompletedCaptureSessions() {
        return ResponseEntity.ok(reportService.reportCompletedCaptureSessions());
    }

    @GetMapping("/share-bookings-removed")
    @Operation(
        operationId = "reportShareBookingRemoved",
        summary = "Get report on booking share removal"
    )
    public ResponseEntity<List<AccessRemovedReportDTO>> reportShareBookingRemoved() {
        return ResponseEntity.ok(reportService.reportAccessRemoved());
    }
}
