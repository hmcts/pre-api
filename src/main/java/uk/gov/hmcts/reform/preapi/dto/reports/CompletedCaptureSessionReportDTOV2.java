package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.CaptureSession;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "CompletedCaptureSessionReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CompletedCaptureSessionReportDTOV2 extends BaseReportDTO {

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

    public CompletedCaptureSessionReportDTOV2(Recording recording) {
        super(recording.getCaptureSession().getBooking().getCaseId());
        CaptureSession captureSession = recording.getCaptureSession();
        status = captureSession.getStatus();

        recordingDate = DateTimeUtils.formatDate(captureSession.getStartedAt());
        recordingTime = DateTimeUtils.formatTime(captureSession.getStartedAt());
        finishTime = DateTimeUtils.formatTime(captureSession.getFinishedAt());
        timezone = DateTimeUtils.getTimezoneAbbreviation(captureSession.getStartedAt());
        Booking booking = captureSession.getBooking();
        scheduledDate = DateTimeUtils.formatDate(captureSession.getBooking().getScheduledFor());

        List<Participant> defendants = booking.getParticipants()
            .stream()
            .filter(p -> p.getParticipantType() == ParticipantType.DEFENDANT)
            .toList();
        defendantNames = defendants.stream()
            .map(Participant::getFullName)
            .collect(Collectors.joining(", "));
        defendant = defendants.size();

        List<Participant> witnesses = booking.getParticipants()
            .stream()
            .filter(p -> p.getParticipantType() == ParticipantType.WITNESS)
            .toList();
        witnessNames = witnesses.stream()
            .map(Participant::getFullName)
            .collect(Collectors.joining(", "));
        witness = witnesses.size();
    }
}
