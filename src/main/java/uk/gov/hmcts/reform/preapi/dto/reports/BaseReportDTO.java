package uk.gov.hmcts.reform.preapi.dto.reports;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Court;
import uk.gov.hmcts.reform.preapi.entities.Region;

@Data
@NoArgsConstructor
@SuppressWarnings("PMD.AbstractClassWithoutAbstractMethod")
public abstract class BaseReportDTO {
    @Schema(description = "ReportCaseReference")
    protected String caseReference;

    @Schema(description = "ReportCourt")
    protected String court;

    @Schema(description = "ReportCounty")
    protected String county;

    @Schema(description = "ReportPostcode")
    protected String postcode;

    @Schema(description = "ReportRegion")
    protected String region;

    protected BaseReportDTO(Case aCase) {
        if (aCase == null) {
            return;
        }

        caseReference = aCase.getReference();
        Court courtEntity = aCase.getCourt();
        court = courtEntity.getName();
        county = courtEntity.getCounty();
        postcode = courtEntity.getPostcode();
        region = courtEntity.getRegions().stream().findFirst().map(Region::getName).orElse(null);
    }
}
