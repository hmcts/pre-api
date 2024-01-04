package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "CreateParticipantDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateParticipantDTO {
    @Schema(description = "CreateParticipantId")
    private UUID id;

    @Schema(description = "CreateParticipantType")
    private ParticipantType participantType;

    @Schema(description = "CreateParticipantFirstName")
    private String firstName;

    @Schema(description = "CreateParticipantLastName")
    private String lastName;

    public CreateParticipantDTO(Participant participantEntity) {
        this.id = participantEntity.getId();
        this.participantType = participantEntity.getParticipantType();
        this.firstName = participantEntity.getFirstName();
        this.lastName = participantEntity.getLastName();
    }
}
