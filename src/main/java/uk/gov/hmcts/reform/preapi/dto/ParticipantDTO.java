package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Participant;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@Schema(description = "ParticipantDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ParticipantDTO {
    @Schema(description = "ParticipantId")
    private UUID id;

    @Schema(description = "ParticipantType")
    private ParticipantType participantType;

    @Schema(description = "ParticipantFirstName")
    private String firstName;

    @Schema(description = "ParticipantLastName")
    private String lastName;

    @Schema(description = "ParticipantDeletedAt")
    private Timestamp deletedAt;

    @Schema(description = "ParticipantCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "ParticipantModifiedAt")
    private Timestamp modifiedAt;

    public ParticipantDTO(Participant participantEntity) {
        this.id = participantEntity.getId();
        this.participantType = participantEntity.getParticipantType();
        this.firstName = participantEntity.getFirstName();
        this.lastName = participantEntity.getLastName();
        this.deletedAt = participantEntity.getDeletedAt();
        this.createdAt = participantEntity.getCreatedAt();
        this.modifiedAt = participantEntity.getModifiedAt();
    }
}
