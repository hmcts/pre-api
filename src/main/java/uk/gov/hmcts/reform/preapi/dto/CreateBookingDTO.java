package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Booking;

import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateBookingDTO {

    private UUID id;
    private UUID caseId;
    private Timestamp scheduledFor;
    private Set<CreateParticipantDTO> participants;

    // room?

    public CreateBookingDTO(Booking bookingEntity) {
        this.id = bookingEntity.getId();
        this.caseId = bookingEntity.getCaseId().getId();
        this.scheduledFor = bookingEntity.getScheduledFor();
        this.participants = Stream.ofNullable(bookingEntity.getParticipants())
            .flatMap(participants -> participants.stream().map(CreateParticipantDTO::new))
            .collect(Collectors.toSet());
    }
}
