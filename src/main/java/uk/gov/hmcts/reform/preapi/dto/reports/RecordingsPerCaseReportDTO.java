package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Region;

@Data
@NoArgsConstructor
@Schema(description = "RecordingsPerCaseReportDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecordingsPerCaseReportDTO {

    @Schema(description = "RecordingsPerCaseCaseReference")
    private String caseReference;

    @Schema(description = "RecordingsPerCaseCourt")
    private String court;

    @Schema(description = "RecordingsPerCaseCourtCounty")
    private String county;

    @Schema(description = "RecordingsPerCaseCourtPostcode")
    private String postcode;

    @Schema(description = "RecordingsPerCaseRegion")
    private String region;

    @Schema(description = "RecordingsPerCaseCount")
    private int count;

    public RecordingsPerCaseReportDTO(Case caseEntity, int count) {
        caseReference = caseEntity.getReference();
        var courtEntity = caseEntity.getCourt();
        court = courtEntity.getName();
        county = courtEntity.getCounty();
        postcode = courtEntity.getPostcode();
        region = courtEntity.getRegions()
            .stream()
            .findFirst()
            .map(Region::getName)
            .orElse(null);
        this.count = count;
    }
}
