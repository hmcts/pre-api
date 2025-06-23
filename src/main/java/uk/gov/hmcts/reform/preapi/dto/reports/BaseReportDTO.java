package uk.gov.hmcts.reform.preapi.dto.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;

@Data
@NoArgsConstructor
public abstract class BaseReportDTO {
    @Schema(description = "ReportCaseReference")
    private String caseReference;

    @Schema(description = "ReportCourt")
    private String court;

    @Schema(description = "ReportCounty")
    private String county;

    @Schema(description = "ReportPostcode")
    private String postcode;

    @Schema(description = "ReportRegion")
    private String region;

    protected BaseReportDTO(Case c) {
        if (c == null) {
            return;
        }

        caseReference = c.getReference();
        Court courtEntity = c.getCourt();
        court = courtEntity.getName();
        county = courtEntity.getCounty();
        postcode = courtEntity.getPostcode();
        region = courtEntity.getRegions().stream().findFirst().map(Region::getName).orElse(null);
    }
}
