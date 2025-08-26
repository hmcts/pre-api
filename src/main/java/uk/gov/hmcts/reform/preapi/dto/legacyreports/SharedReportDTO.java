package uk.gov.hmcts.reform.preapi.dto.legacyreports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Data
@NoArgsConstructor
@Schema(description = "SharedReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class SharedReportDTO {

    @Schema(description = "SharedReportSharedAt")
    private Timestamp sharedAt;

    @Schema(description = "SharedReportAllocatedTo")
    private String allocatedTo;

    @Schema(description = "SharedReportAllocatedToFullName")
    private String allocatedToFullName;

    @Schema(description = "SharedReportAllocatedBy")
    private String allocatedBy;

    @Schema(description = "SharedReportAllocatedToFullName")
    private String allocatedByFullName;

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
        allocatedToFullName = shareBooking.getSharedWith().getFullName();
        allocatedBy = shareBooking.getSharedBy().getEmail();
        allocatedByFullName = shareBooking.getSharedBy().getFullName();
        Case caseEntity = shareBooking.getBooking().getCaseId();
        caseReference = caseEntity.getReference();
        court = caseEntity.getCourt().getName();
        regions = Stream.ofNullable(caseEntity.getCourt().getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());
        bookingId = shareBooking.getBooking().getId();
    }
}
