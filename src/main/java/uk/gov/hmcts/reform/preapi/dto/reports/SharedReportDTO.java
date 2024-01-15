package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;


@Data
@NoArgsConstructor
@Schema(description = "SharedReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SharedReportDTO {

    @Schema(description = "SharedReportSharedAt")
    private Timestamp sharedAt;

    @Schema(description = "SharedReportAllocatedTo")
    private String allocatedTo;

    @Schema(description = "SharedReportAllocatedBy")
    private String allocatedBy;

    @Schema(description = "SharedReportCaseReference")
    private String caseReference;

    @Schema(description = "SharedReportCourtName")
    private String court;

    @Schema(description = "SharedReportRegions")
    private Set<RegionDTO> regions;

    @Schema(description = "SharedReportBookingId")
    private UUID bookingId;

    public SharedReportDTO(ShareBooking shareBooking) {
        sharedAt = shareBooking.getCreatedAt();
        allocatedTo = shareBooking.getSharedWith().getEmail();
        allocatedBy = shareBooking.getSharedBy().getEmail();
        var caseEntity = shareBooking.getBooking().getCaseId();
        caseReference = caseEntity.getReference();
        court = caseEntity.getCourt().getName();
        regions = caseEntity
            .getCourt()
            .getRegions()
            .stream()
            .map(RegionDTO::new)
            .collect(Collectors.toSet());
        bookingId = shareBooking.getBooking().getId();
    }
}
