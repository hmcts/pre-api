package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Audit;
import uk.gov.hmcts.reform.preapi.entities.Recording;
import uk.gov.hmcts.reform.preapi.entities.User;
import uk.gov.hmcts.reform.preapi.utils.DateTimeUtils;

import java.util.stream.Collectors;
import java.util.stream.Stream;
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

    @Schema(description = "PlaybackReportTimeZone")
    private String playbackTimeZone;

    @Schema(description = "PlaybackReportUser")
    private String user;

    @Schema(description = "PlaybackReportUserOrganisation")
    private String userOrganisation;

    @Schema(description = "PlaybackReportCaseReference")
    private String caseReference;

    @Schema(description = "PlaybackReportCourtName")
    private String courtName;

    @Schema(description = "PlaybackReportCounty")
    private String county;

    @Schema(description = "PlaybackReportPostcode")
    private String postcode;

    @Schema(description = "PlaybackReportRegions")
    private String regions;

    public PlaybackReportDTO(Audit audit, User user, @Nullable Recording recording) {
        playbackDate = DateTimeUtils.formatDate(audit.getCreatedAt());
        playbackTimeZone = DateTimeUtils.getTimezoneAbbreviation(audit.getCreatedAt());
        playbackTime = DateTimeUtils.formatTime(audit.getCreatedAt());
        if (user != null) {
            this.user = user.getFullName();
            userOrganisation = user.getOrganisation();
        }
        if (recording != null) {
            var caseEntity = recording.getCaptureSession().getBooking().getCaseId();
            var courtEntity = caseEntity.getCourt();
            caseReference = caseEntity.getReference();
            courtName = courtEntity.getName();
            county = courtEntity.getCounty();
            postcode = courtEntity.getPostcode();
            regions = Stream.ofNullable(caseEntity.getCourt().getRegions())
                            .flatMap(regions -> regions.stream().map(RegionDTO::new))
                            .map(RegionDTO::getName)
                            .collect(Collectors.joining(", "));
        }
    }
}
