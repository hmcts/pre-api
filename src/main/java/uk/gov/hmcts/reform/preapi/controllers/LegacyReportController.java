package uk.gov.hmcts.reform.preapi.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchSharedReport;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.AccessRemovedReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.CompletedCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.EditReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.PlaybackReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.RecordingsPerCaseReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.ScheduleReportDTO;
import uk.gov.hmcts.reform.preapi.dto.legacyreports.SharedReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingParticipantsReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.UserPrimaryCourtReportDTO;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.services.LegacyReportService;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reports")
public class LegacyReportController {

    private final LegacyReportService reportService;
    private final ReportService v2ReportService;

    public LegacyReportController(LegacyReportService reportService, ReportService v2ReportService) {
        this.reportService = reportService;
        this.v2ReportService = v2ReportService;
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
    @Parameter(
        name = "courtId",
        description = "The court id to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "bookingId",
        description = "The booking id to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "sharedWithId",
        description = "The id of the user the booking is shared with to search by",
        schema = @Schema(implementation = UUID.class),
        example = "123e4567-e89b-12d3-a456-426614174000"
    )
    @Parameter(
        name = "sharedWithEmail",
        description = "The email of the user the booking is shared with to search by",
        schema = @Schema(implementation = String.class),
        example = "example@example.com"
    )
    public ResponseEntity<List<SharedReportDTO>> reportBookingsShared(
        @Parameter(hidden = true) SearchSharedReport params
    ) {
        return ResponseEntity.ok(reportService.reportShared(
            params.getCourtId(),
            params.getBookingId(),
            params.getSharedWithId(),
            params.getSharedWithEmail()
        ));
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
    @Parameter(
        name = "source",
        description = "The source of the playback. Only accepts PORTAL, APPLICATION or null",
        schema = @Schema(implementation = AuditLogSource.class)
    )
    public ResponseEntity<List<PlaybackReportDTO>> reportPlayback(
        @RequestParam(required = false) AuditLogSource source
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

    @GetMapping("/recording-participants")
    @Operation(
        operationId = "reportRecordingParticipants",
        summary = "Get report on participants and the recordings they are part of"
    )
    public ResponseEntity<List<RecordingParticipantsReportDTO>> reportRecordingParticipants() {
        return ResponseEntity.ok(v2ReportService.reportRecordingParticipants());
    }

    @GetMapping("/user-primary-courts")
    @Operation(
        operationId = "reportUserPrimaryCourts",
        summary = "Get report on app users: their first and last name, their role, their active status, "
            + "their primary court and their last access time (if available)")
    public ResponseEntity<List<UserPrimaryCourtReportDTO>> reportUserPrimaryCourts() {
        return ResponseEntity.ok(v2ReportService.reportUserPrimaryCourts());
    }
}
