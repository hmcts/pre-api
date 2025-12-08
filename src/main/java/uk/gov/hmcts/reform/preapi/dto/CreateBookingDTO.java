package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.validators.ParticipantTypeConstraint;
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
    @NotNull(message = "id is required")
    private UUID id;

    @Schema(description = "CreateBookingCaseId")
    @NotNull(message = "case_id is required")
    private UUID caseId;

    @Schema(description = "CreateBookingScheduledFor")
    @NotNull(message = "scheduled_for is required and must not be before today")
    private Timestamp scheduledFor;

    @Schema(description = "CreateBookingParticipants")
    @ParticipantTypeConstraint
    private Set<CreateParticipantDTO> participants;

    public CreateBookingDTO(Booking bookingEntity) {
        this.id = bookingEntity.getId();
        this.caseId = bookingEntity.getCaseId().getId();
        this.scheduledFor = bookingEntity.getScheduledFor();
        this.participants = Stream.ofNullable(bookingEntity.getParticipants())
            .flatMap(participants -> participants.stream().map(CreateParticipantDTO::new))
            .collect(Collectors.toSet());
    }
}
