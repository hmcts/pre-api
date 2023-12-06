package uk.gov.hmcts.reform.preapi.model;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class Booking {

    private UUID id;
    private UUID caseId;
    private Timestamp scheduledFor;
    private Timestamp deletedAt;
    private Timestamp createdAt;
    private Timestamp modifiedAt;
    private Set<Participant> participants;

    public Booking(uk.gov.hmcts.reform.preapi.entities.Booking bookingEntity) {
        this.id = bookingEntity.getId();
        this.caseId = bookingEntity.getCaseId().getId();
        this.scheduledFor = bookingEntity.getScheduledFor();
        this.participants = Stream.ofNullable(bookingEntity.getParticipants())
            .flatMap(participants -> participants.stream().map(Participant::new))
            .collect(Collectors.toSet());
        this.deletedAt = bookingEntity.getDeletedAt();
        this.createdAt = bookingEntity.getCreatedAt();
        this.modifiedAt = bookingEntity.getModifiedAt();
    }
}
