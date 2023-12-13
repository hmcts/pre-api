package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateParticipantDTO {
    private UUID id;
    private ParticipantType participantType;
    private String firstName;
    private String lastName;

    public CreateParticipantDTO(Participant participantEntity) {
        this.id = participantEntity.getId();
        this.participantType = participantEntity.getParticipantType();
        this.firstName = participantEntity.getFirstName();
        this.lastName = participantEntity.getLastName();
    }
}
