package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressWarnings("PMD.ShortClassName")
public class CreateCaseDTO {
    private UUID id;
    private UUID courtId;
    private String reference;
    private boolean test;

    public CreateCaseDTO(Case caseEntity) {
        this.id = caseEntity.getId();
        this.courtId = caseEntity.getCourt().getId();
        this.reference = caseEntity.getReference();
        this.test = caseEntity.isTest();
    }
}
