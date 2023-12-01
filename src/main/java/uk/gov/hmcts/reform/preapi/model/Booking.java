package uk.gov.hmcts.reform.preapi.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Participant;

import java.sql.Timestamp;
import java.util.Set;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Booking {

    private Long id;
    private Long caseId;
    private Timestamp scheduledFor;
    private Timestamp deletedAt;
    private Timestamp createdAt;
    private Timestamp modifiedAt;
    private Set<Participant> participants;
}
