package uk.gov.hmcts.reform.preapi.dto.legacyreports;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "CompletedCaptureSessionReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CompletedCaptureSessionReportDTO {

    @Schema(description = "CompletedCaptureSessionReportStartedAt")
    private Timestamp startedAt;

    @Schema(description = "CompletedCaptureSessionReportFinishedAt")
    private Timestamp finishedAt;

    @Schema(
        description = "CompletedCaptureSessionReportDuration",
        implementation = String.class
    )
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private Duration duration;

    @Schema(description = "CompletedCaptureSessionReportBookingScheduledFor")
    private Timestamp scheduledFor;

    @Schema(description = "CompletedCaptureSessionReportCaseReference")
    private String caseReference;

    @Schema(description = "CompletedCaptureSessionReportDefendantCount")
    private int countDefendants;

    @Schema(description = "CompletedCaptureSessionReportWitnessCount")
    private int countWitnesses;

    @Schema(description = "CompletedCaptureSessionReportRecordingStatus")
    private RecordingStatus recordingStatus;

    @Schema(description = "CompletedCaptureSessionReportCourtName")
    private String court;

    @Schema(description = "CompletedCaptureSessionReportRegions")
    private Set<RegionDTO> regions;

    public CompletedCaptureSessionReportDTO(Recording recording) {
        CaptureSession captureSession = recording.getCaptureSession();

        startedAt = captureSession.getStartedAt();
        finishedAt = captureSession.getFinishedAt();
        duration = recording.getDuration();

        Booking booking = captureSession.getBooking();
        scheduledFor = booking.getScheduledFor();

        Case caseEntity = booking.getCaseId();
        caseReference = caseEntity.getReference();
        countDefendants = (int) booking
            .getParticipants()
            .stream()
            .filter(p -> p.getParticipantType() == ParticipantType.DEFENDANT)
            .count();
        countWitnesses = (int) booking
            .getParticipants()
            .stream()
            .filter(p -> p.getParticipantType() == ParticipantType.WITNESS)
            .count();
        recordingStatus = captureSession.getStatus();
        court = caseEntity.getCourt().getName();
        regions = Stream.ofNullable(caseEntity.getCourt().getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());
    }
}
