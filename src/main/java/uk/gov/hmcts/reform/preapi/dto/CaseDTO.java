package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Case;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@Schema(description = "CaseDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CaseDTO {
    @Schema(description = "CaseId")
    private UUID id;

    @Schema(description = "CaseCourt")
    private CourtDTO court;

    @Schema(description = "CaseReference")
    private String reference;

    @Schema(description = "CaseParticipants")
    private List<ParticipantDTO> participants;

    @Schema(description = "CaseOrigin")
    private RecordingOrigin origin;

    @Schema(description = "CaseIsTest")
    private boolean test;

    @Schema(description = "CaseState")
    private CaseState state;

    @Schema(description = "CaseClosedAt")
    private Timestamp closedAt;

    @Schema(description = "CaseDeletedAt")
    private Timestamp deletedAt;

    @Schema(description = "CaseCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "CaseModifiedAt")
    private Timestamp modifiedAt;

    public CaseDTO(Case caseEntity) {
        id = caseEntity.getId();
        court = new CourtDTO(caseEntity.getCourt());
        reference = caseEntity.getReference();
        participants = Stream.ofNullable(caseEntity.getParticipants())
            .flatMap(participants ->
                         participants
                             .stream()
                             .filter(participant -> participant.getDeletedAt() == null)
                             .sorted(Comparator.comparing(Participant::getFirstName))
                             .map(ParticipantDTO::new))
            .collect(Collectors.toList());
        origin = caseEntity.getOrigin();
        test = caseEntity.isTest();
        state = caseEntity.getState();
        closedAt = caseEntity.getClosedAt();
        deletedAt = caseEntity.getDeletedAt();
        createdAt = caseEntity.getCreatedAt();
        modifiedAt = caseEntity.getModifiedAt();
    }
}
