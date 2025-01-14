package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@Schema(description = "CompletedCaptureSessionReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CompletedCaptureSessionReportDTO {

    @Schema(description = "CompletedCaptureSessionReportRecordingDate")
    private String recordingDate;

    @Schema(description = "CompletedCaptureSessionReportRecordingTime")
    private String recordingTime;

    @Schema(description = "CompletedCaptureSessionReportFinishTime")
    private String finishTime;

    @Schema(description = "CompletedCaptureSessionReportTimezone")
    private String timezone;

    @Schema(description = "CompletedCaptureSessionReportScheduledDate")
    private String scheduledDate;

    @Schema(description = "CompletedCaptureSessionReportCaseReference")
    private String caseReference;

    @Schema(description = "CompletedCaptureSessionReportStatus")
    private RecordingStatus status;

    @Schema(description = "CompletedCaptureSessionReportDefendantNames")
    private String defendantNames;

    @Schema(description = "CompletedCaptureSessionReportDefendantCount")
    private int defendant;

    @Schema(description = "CompletedCaptureSessionReportWitnessNames")
    private String witnessNames;

    @Schema(description = "CompletedCaptureSessionReportWitnessCount")
    private int witness;

    @Schema(description = "CompletedCaptureSessionReportCourtName")
    private String court;

    @Schema(description = "CompletedCaptureSessionReportCounty")
    private String county;

    @Schema(description = "CompletedCaptureSessionReportPostcode")
    private String postcode;

    @Schema(description = "CompletedCaptureSessionReportRegion")
    private String region;

    public CompletedCaptureSessionReportDTO(Recording recording) {
        var captureSession = recording.getCaptureSession();
        status = captureSession.getStatus();

        recordingDate = DateTimeUtils.formatDate(captureSession.getStartedAt());
        recordingTime = DateTimeUtils.formatTime(captureSession.getStartedAt());
        finishTime = DateTimeUtils.formatTime(captureSession.getFinishedAt());
        timezone = DateTimeUtils.getTimezoneAbbreviation(captureSession.getStartedAt());
        var booking = captureSession.getBooking();
        scheduledDate = DateTimeUtils.formatDate(captureSession.getBooking().getScheduledFor());

        var caseEntity = booking.getCaseId();
        caseReference = caseEntity.getReference();

        var defendants = booking.getParticipants()
            .stream()
            .filter(p -> p.getParticipantType() == ParticipantType.DEFENDANT)
            .toList();
        defendantNames = defendants.stream()
            .map(Participant::getFullName)
            .collect(Collectors.joining(", "));
        defendant = defendants.size();

        var witnesses = booking.getParticipants()
            .stream()
            .filter(p -> p.getParticipantType() == ParticipantType.WITNESS)
            .toList();
        witnessNames = witnesses.stream()
            .map(Participant::getFullName)
            .collect(Collectors.joining(", "));
        witness = witnesses.size();

        var courtEntity = caseEntity.getCourt();
        court = courtEntity.getName();
        county = courtEntity.getCounty();
        postcode = courtEntity.getPostcode();
        region = courtEntity.getRegions().stream().findFirst().map(Region::getName).orElse(null);
    }
}
