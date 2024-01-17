package uk.gov.hmcts.reform.preapi.dto.reports;


import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Timestamp;
import java.time.Duration;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@Schema(description = "PlaybackReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlaybackReportDTO {

    @Schema(description = "PlaybackReportPlaybackAt")
    private Timestamp playbackAt;

    @Schema(description = "PlaybackReportFinishTime")
    private Timestamp finishedAt;

    @Schema(description = "PlaybackReportDuration", implementation = String.class)
    private Duration duration;

    @Schema(description = "PlaybackReportUser")
    private String user;

    @Schema(description = "PlaybackReportCaseReference")
    private String caseReference;

    @Schema(description = "PlaybackReportCourt")
    private String court;

    @Schema(description = "PlaybackReportRegions")
    private Set<RegionDTO> regions;

    @Schema(description = "PlaybackReportRecordingId")
    private UUID recordingId;

    public PlaybackReportDTO(Audit audit, String email, @Nullable Recording recording) {
        playbackAt = audit.getCreatedAt();
        finishedAt = null;
        duration = null;
        user = email;
        if (recording != null) {
            var caseEntity = recording.getCaptureSession().getBooking().getCaseId();
            var courtEntity = caseEntity.getCourt();
            court = courtEntity.getName();
            caseReference = caseEntity.getReference();
            regions = courtEntity
                .getRegions()
                .stream()
                .map(RegionDTO::new)
                .collect(Collectors.toSet());
            recordingId = recording.getId();
        }
    }
}
