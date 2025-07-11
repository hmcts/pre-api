package uk.gov.hmcts.reform.preapi.dto.legacyreports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "RecordingsPerCaseReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecordingsPerCaseReportDTO {

    @Schema(description = "RecordingsPerCaseCaseReference")
    private String caseReference;

    @Schema(description = "RecordingsPerCaseCourt")
    private String court;

    @Schema(description = "RecordingsPerCaseRegions")
    private Set<RegionDTO> regions;

    @Schema(description = "RecordingsPerCaseCount")
    private int count;

    public RecordingsPerCaseReportDTO(Case caseEntity, int count) {
        caseReference = caseEntity.getReference();
        court = caseEntity.getCourt().getName();
        regions = Stream.ofNullable(caseEntity.getCourt().getRegions())
            .flatMap(regions -> regions.stream().map(RegionDTO::new))
            .collect(Collectors.toSet());
        this.count = count;
    }
}
