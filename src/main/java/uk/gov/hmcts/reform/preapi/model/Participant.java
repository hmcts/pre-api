package uk.gov.hmcts.reform.preapi.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Participant {
    private UUID id;
    private ParticipantType participantType;
    private String firstName;
    private String lastName;
    private Timestamp deletedAt;
    private Timestamp createdAt;
    private Timestamp modifiedAt;
}
