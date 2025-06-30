package uk.gov.hmcts.reform.preapi.dto.reports;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@Schema(description = "RecordingsPerCaseReportDTOV2")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class RecordingsPerCaseReportDTO extends BaseReportDTO {

    @Schema(description = "RecordingsPerCaseNumberOfRecordings")
    private int numberOfRecordings;

    public RecordingsPerCaseReportDTO(Case caseEntity, int count) {
        super(caseEntity);
        this.numberOfRecordings = count;
    }
}
