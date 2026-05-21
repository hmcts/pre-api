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
import uk.gov.hmcts.reform.preapi.dto.reports.AccessRemovedReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.CompletedCaptureSessionReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.EditReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.PlaybackReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingParticipantsReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.RecordingsPerCaseReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.ScheduleReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.SharedReportDTOV2;
import uk.gov.hmcts.reform.preapi.dto.reports.UserPrimaryCourtReportDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.UserRecordingPlaybackReportDTOV2;
import uk.gov.hmcts.reform.preapi.enums.AuditLogSource;
import uk.gov.hmcts.reform.preapi.services.ReportService;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/reports-v2")
public class ReportController {

    private final ReportService reportService;

    public ReportController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/capture-sessions-concurrent")
    @Operation(operationId = "reportConcurrentCaptureSessionsv2")
    public ResponseEntity<List<ConcurrentCaptureSessionReportDTOV2>> reportConcurrentCaptureSessions() {
        return ResponseEntity.ok(reportService.reportCaptureSessions());
    }

    @GetMapping("/recordings-per-case")
    @Operation(
        operationId = "reportRecordingsPerCase",
        summary = "Get the number of completed capture sessions for each case v2"
    )
    public ResponseEntity<List<RecordingsPerCaseReportDTOV2>> reportRecordingsPerCase() {
        return ResponseEntity.ok(reportService.reportRecordingsPerCase());
    }

    @GetMapping("/edits")
    @Operation(operationId = "reportEdits", summary = "Get a report on recordings edits v2")
    public ResponseEntity<List<EditReportDTOV2>> reportEdits() {
        return ResponseEntity.ok(reportService.reportEdits());
    }

    @GetMapping("/shared-bookings")
    @Operation(operationId = "reportBookingsShared", summary = "Get a report on the bookings that have been shared v2")
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
    @Parameter(
        name = "onlyActive",
        description = "The shares must be active (not deleted) then true, otherwise false",
        schema = @Schema(implementation = Boolean.class),
        example = "true"
    )
    public ResponseEntity<List<SharedReportDTOV2>> reportBookingsShared(
        @Parameter(hidden = true) SearchSharedReport params
    ) {
        return ResponseEntity.ok(reportService.reportShared(
            params.getCourtId(),
            params.getBookingId(),
            params.getSharedWithId(),
            params.getSharedWithEmail(),
            params.getOnlyActive() != null && params.getOnlyActive()
        ));
    }

    @GetMapping("/schedules")
    @Operation(
        operationId = "reportSchedules",
        summary = "Get a list of completed capture sessions with booking details v2"
    )
    public ResponseEntity<List<ScheduleReportDTOV2>> reportSchedules() {
        return ResponseEntity.ok(reportService.reportScheduled());
    }

    @GetMapping("/playback")
    @Operation(
        operationId = "reportPlayback",
        summary = "Get report on playback by playback source (PORTAL, APPLICATION) v2"
    )
    @Parameter(
        name = "source",
        description = "The source of the playback. Only accepts PORTAL, APPLICATION or null",
        schema = @Schema(implementation = AuditLogSource.class)
    )
    public ResponseEntity<List<PlaybackReportDTOV2>> reportPlayback(
        @RequestParam(required = false) AuditLogSource source
    ) {
        return ResponseEntity.ok(reportService.reportPlayback(source));
    }

    @GetMapping("/user-recording-playback")
    @Operation(
        operationId = "userRecordingPlaybackReport",
        summary = "Get report on playback by playback for all sources v2"
    )
    public ResponseEntity<List<UserRecordingPlaybackReportDTOV2>> userRecordingPlaybackReport() {
        return ResponseEntity.ok(reportService.userRecordingPlaybackReport());
    }

    @GetMapping("/completed-capture-sessions")
    @Operation(
        operationId = "reportCompletedCaptureSessions",
        summary = "Get a report on capture sessions with available recordings v2"
    )
    public ResponseEntity<List<CompletedCaptureSessionReportDTOV2>> reportCompletedCaptureSessions() {
        return ResponseEntity.ok(reportService.reportCompletedCaptureSessions());
    }

    @GetMapping("/share-bookings-removed")
    @Operation(
        operationId = "reportShareBookingRemoved",
        summary = "Get report on booking share removal v2"
    )
    public ResponseEntity<List<AccessRemovedReportDTOV2>> reportShareBookingRemoved() {
        return ResponseEntity.ok(reportService.reportAccessRemoved());
    }

    @GetMapping("/recording-participants")
    @Operation(
        operationId = "reportRecordingParticipants",
        summary = "Get report on participants and the recordings they are part of v2"
    )
    public ResponseEntity<List<RecordingParticipantsReportDTO>> reportRecordingParticipants() {
        return ResponseEntity.ok(reportService.reportRecordingParticipants());
    }

    @GetMapping("/user-primary-courts")
    @Operation(
        operationId = "reportUserPrimaryCourts",
        summary = "Get report on app users and their primary courts v2")
    public ResponseEntity<List<UserPrimaryCourtReportDTO>> reportUserPrimaryCourts() {
        return ResponseEntity.ok(reportService.reportUserPrimaryCourts());
    }
}
