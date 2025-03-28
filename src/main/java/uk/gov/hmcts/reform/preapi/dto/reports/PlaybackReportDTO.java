package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PlaybackReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlaybackReportDTO {

    @Schema(description = "PlaybackReportPlaybackAt")
    private Timestamp playbackAt;

    @Schema(description = "PlaybackReportUserFullName")
    private String userFullName;

    @Schema(description = "PlaybackReportUserEmail")
    private String userEmail;

    @Schema(description = "PlaybackReportCaseReference")
    private String caseReference;

    @Schema(description = "PlaybackReportCourt")
    private String court;

    @Schema(description = "PlaybackReportRegions")
    private Set<RegionDTO> regions;

    @Schema(description = "PlaybackReportRecordingId")
    private UUID recordingId;

    public PlaybackReportDTO(Audit audit, User user, @Nullable Recording recording) {
        playbackAt = audit.getCreatedAt();
        if (user != null) {
            userFullName = user.getFullName();
            userEmail = user.getEmail();
        }
        if (recording != null) {
            Case caseEntity = recording.getCaptureSession().getBooking().getCaseId();
            Court courtEntity = caseEntity.getCourt();
            court = courtEntity.getName();
            caseReference = caseEntity.getReference();
            regions = Stream.ofNullable(caseEntity.getCourt().getRegions())
                .flatMap(regions -> regions.stream().map(RegionDTO::new))
                .collect(Collectors.toSet());
            recordingId = recording.getId();
        }
    }
}
