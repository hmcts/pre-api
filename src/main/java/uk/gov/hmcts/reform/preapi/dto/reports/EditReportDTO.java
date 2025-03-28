package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Recording;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;


@Data
@NoArgsConstructor
@Schema(description = "EditReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class EditReportDTO {

    @Schema(description = "EditReportEditCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "EditReportRecordingVersion")
    private int version;

    @Schema(description = "EditReportCaseReference")
    private String caseReference;

    @Schema(description = "EditReportCourtName")
    private String court;

    @Schema(description = "EditReportRegions")
    private Set<RegionDTO> regions;

    @Schema(description = "EditReportRecordingId")
    private UUID recordingId;

    public EditReportDTO(Recording recordingEntity) {
        createdAt = recordingEntity.getCreatedAt();
        version = recordingEntity.getVersion();
        Case caseEntity = recordingEntity
            .getCaptureSession()
            .getBooking()
            .getCaseId();
        caseReference = caseEntity.getReference();
        court = caseEntity.getCourt().getName();
        regions = Stream.ofNullable(caseEntity.getCourt().getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());
        recordingId = recordingEntity.getId();
    }
}
