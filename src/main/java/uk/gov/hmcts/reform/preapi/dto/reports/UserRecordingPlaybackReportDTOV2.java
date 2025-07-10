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
@Schema(description = "PlaybackReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class UserRecordingPlaybackReportDTOV2 extends BaseReportDTO {

    @Schema(description = "PlaybackReportPlaybackDate")
    protected String playbackDate;

    @Schema(description = "PlaybackReportPlaybackTime")
    protected String playbackTime;

    @Schema(description = "PlaybackReportRecordingVersion")
    protected Integer recordingVersion;

    @Schema(description = "PlaybackReportDefendants")
    protected String defendants;

    @Schema(description = "PlaybackReportWitness")
    protected String witness;

    @Schema(description = "PlaybackReportUserFullName")
    protected String userFullName;

    @Schema(description = "PlaybackReportUserEmail")
    protected String userEmail;

    @Schema(description = "PlaybackReportUserOrganisation")
    protected String userOrganisation;

    public UserRecordingPlaybackReportDTOV2(Audit audit, User user, @Nullable Recording recording) {
        super(recording != null ? recording.getCaptureSession().getBooking().getCaseId() : null);

        playbackDate = DateTimeUtils.formatDate(audit.getCreatedAt());
        playbackTime = DateTimeUtils.formatTime(audit.getCreatedAt());
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
