package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.validators.CaseStateConstraint;
import uk.gov.hmcts.reform.preapi.dto.validators.ParticipantTypeConstraint;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@CaseStateConstraint
@Schema(description = "CreateCaseDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
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
    @ParticipantTypeConstraint
    private Set<CreateParticipantDTO> participants;

    @Schema(description = "CreateCaseOrigin")
    private RecordingOrigin origin;

    @Schema(description = "CreateCaseIsTest")
    private boolean test;

    @Schema(description = "CreateCaseState")
    // todo breaking change until this is added in prod
    // @NotNull
    private CaseState state;

    @Schema(description = "CreateCaseClosedAt")
    private Timestamp closedAt;

    public CreateCaseDTO(Case caseEntity) {
        id = caseEntity.getId();
        courtId = caseEntity.getCourt().getId();
        reference = caseEntity.getReference();
        participants = Stream.ofNullable(caseEntity.getParticipants())
            .flatMap(participants -> participants.stream().map(CreateParticipantDTO::new))
            .collect(Collectors.toSet());
        origin = caseEntity.getOrigin();
        test = caseEntity.isTest();
        state = caseEntity.getState();
        closedAt = caseEntity.getClosedAt();
    }
}
