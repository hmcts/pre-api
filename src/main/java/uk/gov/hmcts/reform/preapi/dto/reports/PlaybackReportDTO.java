package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import java.util.stream.Collectors;
import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "PlaybackReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlaybackReportDTO extends BaseReportDTO {

    @Schema(description = "PlaybackReportPlaybackDate")
    private String playbackDate;

    @Schema(description = "PlaybackReportPlaybackTime")
    private String playbackTime;

    @Schema(description = "PlaybackReportTimeZone")
    private String playbackTimeZone;

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

    @Schema(description = "PlaybackReportUserOrganisation")
    private String userOrganisation;

    public PlaybackReportDTO(Audit audit, User user, @Nullable Recording recording) {
        super(recording != null ? recording.getCaptureSession().getBooking().getCaseId() : null);

        playbackDate = DateTimeUtils.formatDate(audit.getCreatedAt());
        playbackTime = DateTimeUtils.formatTime(audit.getCreatedAt());
        playbackTimeZone = DateTimeUtils.getTimezoneAbbreviation(audit.getCreatedAt());
        if (user != null) {
            userFullName = user.getFullName();
            userEmail = user.getEmail();
            userOrganisation = user.getOrganisation();
        }

        if (recording != null) {
            Booking booking = recording.getCaptureSession().getBooking();
            recordingVersion = recording.getVersion();
            witness = booking.getParticipants()
                .stream()
                .filter(p -> p.getParticipantType() == ParticipantType.WITNESS)
                .findFirst()
                .map(Participant::getFullName)
                .orElse(null);
            defendants = booking.getParticipants().stream()
                .filter(p -> p.getParticipantType() == ParticipantType.DEFENDANT)
                .map(Participant::getFullName)
                .collect(Collectors.collectingAndThen(
                    Collectors.joining(", "),
                    result -> result.isEmpty() ? null : result));
        }
    }
}
