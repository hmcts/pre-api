package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CaseState;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "CaseDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@SuppressWarnings("PMD.ShortClassName")
public class CaseDTO {
    @Schema(description = "CaseId")
    private UUID id;

    @Schema(description = "CaseCourt")
    private CourtDTO court;

    @Schema(description = "CaseReference")
    private String reference;

    @Schema(description = "CaseParticipants")
    private List<ParticipantDTO> participants;

    @Schema(description = "CaseIsTest")
    private boolean test;

    @Schema(description = "CaseState")
    private CaseState state = CaseState.OPEN; 

    @Schema(description = "CaseClosedAt")
    private LocalDate closedAt;

    @Schema(description = "CaseDeletedAt")
    private Timestamp deletedAt;

    @Schema(description = "CaseCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "CaseModifiedAt")
    private Timestamp modifiedAt;

    public CaseDTO(Case caseEntity) {
        this.id = caseEntity.getId();
        this.court = new CourtDTO(caseEntity.getCourt());
        this.reference = caseEntity.getReference();
        this.participants = Stream.ofNullable(caseEntity.getParticipants())
            .flatMap(participants ->
                         participants
                             .stream()
                             .filter(participant -> participant.getDeletedAt() == null)
                             .sorted(Comparator.comparing(Participant::getFirstName))
                             .map(ParticipantDTO::new))
            .collect(Collectors.toList());
        this.test = caseEntity.isTest();
        this.deletedAt = caseEntity.getDeletedAt();
        this.createdAt = caseEntity.getCreatedAt();
        this.modifiedAt = caseEntity.getModifiedAt();
    }
}
