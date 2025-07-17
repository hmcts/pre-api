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
public class PlaybackReportDTOV2 extends UserRecordingPlaybackReportDTOV2 {

    @Schema(description = "PlaybackReportTimeZone")
    private String playbackTimeZone;

    public PlaybackReportDTOV2(Audit audit, User user, @Nullable Recording recording) {
        super(audit, user, recording);
        playbackTimeZone = DateTimeUtils.getTimezoneAbbreviation(audit.getCreatedAt());
    }
}
