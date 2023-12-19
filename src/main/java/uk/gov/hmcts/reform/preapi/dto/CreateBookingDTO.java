package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "CreateBookingDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateBookingDTO {
    @Schema(description = "CreateBookingId")
    private UUID id;

    @Schema(description = "CreateBookingCaseId")
    private UUID caseId;

    @Schema(description = "CreateBookingScheduledFor")
    private Timestamp scheduledFor;

    @Schema(description = "CreateBookingParticipants")
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
