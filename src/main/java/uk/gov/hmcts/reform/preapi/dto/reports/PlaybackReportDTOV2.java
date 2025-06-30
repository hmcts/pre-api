package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import javax.annotation.Nullable;

@Data
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "PlaybackReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class PlaybackReportDTOV2 extends BaseReportDTO {

    @Schema(description = "PlaybackReportPlaybackDate")
    private String playbackDate;

    @Schema(description = "PlaybackReportPlaybackTime")
    private String playbackTime;

    @Schema(description = "PlaybackReportTimeZone")
    private String playbackTimeZone;

    @Schema(description = "PlaybackReportUserFullName")
    private String userFullName;

    @Schema(description = "PlaybackReportUserEmail")
    private String userEmail;

    @Schema(description = "PlaybackReportUserOrganisation")
    private String userOrganisation;

    public PlaybackReportDTOV2(Audit audit, User user, @Nullable Recording recording) {
        super(recording != null ? recording.getCaptureSession().getBooking().getCaseId() : null);

        playbackDate = DateTimeUtils.formatDate(audit.getCreatedAt());
        playbackTime = DateTimeUtils.formatTime(audit.getCreatedAt());
        playbackTimeZone = DateTimeUtils.getTimezoneAbbreviation(audit.getCreatedAt());
        if (user != null) {
            userFullName = user.getFullName();
            userEmail = user.getEmail();
            userOrganisation = user.getOrganisation();
        }
    }
}
