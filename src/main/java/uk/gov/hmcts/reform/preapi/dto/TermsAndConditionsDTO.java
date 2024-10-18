package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.TermsAndConditions;
import uk.gov.hmcts.reform.preapi.enums.TermsAndConditionsType;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class TermsAndConditionsDTO {
    private UUID id;
    private TermsAndConditionsType type;
    private String html;
    private Timestamp createdAt;

    public TermsAndConditionsDTO(TermsAndConditions termsAndConditions) {
        this.id = termsAndConditions.getId();
        this.type = termsAndConditions.getType();
        this.html = termsAndConditions.getContent();
        this.createdAt = termsAndConditions.getCreatedAt();
    }
}
