package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.Region;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "PlaybackReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlaybackReportDTO {

    @Schema(description = "PlaybackReportPlaybackDate")
    private String playbackDate;

    @Schema(description = "PlaybackReportPlaybackTime")
    private String playbackTime;

    @Schema(description = "PlaybackReportCaseReference")
    private String caseReference;

    @Schema(description = "PlaybackReportRecordingVersion")
    private Integer recordingVersion;

    @Schema(description = "PlaybackReportDefendants")
    private String defendants;

    @Schema(description = "PlaybackReportWitness")
    private String witness;

    @Schema(description = "PlaybackReportUserFullName")
    private String userFullName;

    @Schema(description = "PlaybackReportUserEmail")
    private String userEmail;

    @Schema(description = "PlaybackReportCourt")
    private String court;

    @Schema(description = "PlaybackReportCounty")
    private String county;

    @Schema(description = "PlaybackReportPostcode")
    private String postcode;

    @Schema(description = "PlaybackReportRegion")
    private String region;

    public PlaybackReportDTO(Audit audit, User user, @Nullable Recording recording) {
        playbackDate = DateTimeUtils.formatDate(audit.getCreatedAt());
        playbackTime = DateTimeUtils.formatTime(audit.getCreatedAt());
        if (recording != null) {
            var booking = recording.getCaptureSession().getBooking();
            var caseEntity = booking.getCaseId();
            caseReference = caseEntity.getReference();
            recordingVersion = recording.getVersion();

            witness = booking.getParticipants()
                .stream()
                .filter(p -> p.getParticipantType() == ParticipantType.WITNESS)
                .findFirst()
                .map(Participant::getFullName)
                .orElse(null);
            defendants = booking.getParticipants()
                .stream()
                .filter(p -> p.getParticipantType() == ParticipantType.DEFENDANT)
                .map(Participant::getFullName)
                .collect(Collectors.joining(", "));

            var courtEntity = caseEntity.getCourt();
            court = courtEntity.getName();
            county = courtEntity.getCounty();
            postcode = courtEntity.getPostcode();
            region = courtEntity.getRegions().stream().findFirst().map(Region::getName).orElse(null);
        }

        if (user != null) {
            userFullName = user.getFullName();
            userEmail = user.getEmail();
        }
    }
}
