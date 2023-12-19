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
@Schema(description = "BookingDTO")
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class BookingDTO {
    @Schema(description = "BookingId")
    private UUID id;

    @Schema(description = "BookingCase")
    private CaseDTO caseDTO;

    @Schema(description = "BookingScheduledFor")
    private Timestamp scheduledFor;

    @Schema(description = "BookingParticipants")
    private Set<ParticipantDTO> participants;

    @Schema(description = "BookingDeletedAt")
    private Timestamp deletedAt;

    @Schema(description = "BookingCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "BookingModifiedAt")
    private Timestamp modifiedAt;

    // room?

    public BookingDTO(Booking bookingEntity) {
        this.id = bookingEntity.getId();
        this.caseDTO = new CaseDTO(bookingEntity.getCaseId());
        this.scheduledFor = bookingEntity.getScheduledFor();
        this.participants = Stream.ofNullable(bookingEntity.getParticipants())
            .flatMap(participants -> participants.stream().map(ParticipantDTO::new))
            .collect(Collectors.toSet());
        this.deletedAt = bookingEntity.getDeletedAt();
        this.createdAt = bookingEntity.getCreatedAt();
        this.modifiedAt = bookingEntity.getModifiedAt();
    }
}
