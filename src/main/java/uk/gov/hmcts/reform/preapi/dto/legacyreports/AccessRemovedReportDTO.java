package uk.gov.hmcts.reform.preapi.dto.legacyreports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.sql.Timestamp;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "AccessRemovedReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class AccessRemovedReportDTO {

    @Schema(description = "AccessRemovedReportRemovedAt")
    private Timestamp removedAt;

    @Schema(description = "AccessRemovedReportCaseReference")
    private String caseReference;

    @Schema(description = "AccessRemovedReportCourtName")
    private String court;

    @Schema(description = "AccessRemovedReportRegions")
    private Set<RegionDTO> regions;

    @Schema(description = "AccessRemovedReportUserFullName")
    private String userFullName;

    @Schema(description = "AccessRemovedReportUserEmail")
    private String userEmail;

    @Schema(description = "AccessRemovedReportRemovalReason")
    private String removalReason;

    public AccessRemovedReportDTO(ShareBooking shareBooking) {
        var booking = shareBooking.getBooking();
        var courtEntity = booking.getCaseId().getCourt();

        removedAt = shareBooking.getDeletedAt();
        caseReference = booking.getCaseId().getReference();
        court = courtEntity.getName();
        regions = Stream.ofNullable(courtEntity.getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());

        var user = shareBooking.getSharedWith();
        userFullName = user.getFullName();
        userEmail = user.getEmail();
    }
}
