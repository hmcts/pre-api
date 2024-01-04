package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateCaseDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressWarnings("PMD.ShortClassName")
public class CreateCaseDTO {
    @Schema(description = "CreateCaseId")
    private UUID id;

    @Schema(description = "CreateCaseCourtId")
    private UUID courtId;

    @Schema(description = "CreateCaseReference")
    private String reference;

    @Schema(description = "CreateCaseIsTest")
    private boolean test;

    public CreateCaseDTO(Case caseEntity) {
        this.id = caseEntity.getId();
        this.courtId = caseEntity.getCourt().getId();
        this.reference = caseEntity.getReference();
        this.test = caseEntity.isTest();
    }
}
