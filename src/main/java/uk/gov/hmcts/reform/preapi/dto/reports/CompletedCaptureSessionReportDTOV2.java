package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

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

    public CompletedCaptureSessionReportDTOV2(
        String recordingDate,
        String recordingTime,
        String finishTime,
        String timezone,
        String scheduledDate,
        RecordingStatus status,
        String defendantNames,
        Long defendant,
        String witnessNames,
        Long witness
    ) {
        this.recordingDate = recordingDate;
        this.recordingTime = recordingTime;
        this.finishTime = finishTime;
        this.timezone = timezone;
        this.scheduledDate = scheduledDate;
        this.status = status;
        this.defendantNames = defendantNames;
        this.defendant = defendant != null ? defendant.intValue() : 0;
        this.witnessNames = witnessNames;
        this.witness = witness != null ? witness.intValue() : 0;
    }
}
