package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    @NotNull
    @Size(min = 9, max = 13)
    private String reference;

    @Schema(description = "CaseParticipants")
    private Set<CreateParticipantDTO> participants;

    @Schema(description = "CreateCaseIsTest")
    private boolean test;

    public CreateCaseDTO(Case caseEntity) {
        this.id = caseEntity.getId();
        this.courtId = caseEntity.getCourt().getId();
        this.reference = caseEntity.getReference();
        this.participants = Stream.ofNullable(caseEntity.getParticipants())
            .flatMap(participants -> participants.stream().map(CreateParticipantDTO::new))
            .collect(Collectors.toSet());
        this.test = caseEntity.isTest();
    }
}
