package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.Booking;
import uk.gov.hmcts.reform.preapi.entities.Participant;

import java.sql.Timestamp;
import java.util.Comparator;
import java.util.List;
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
    private List<ParticipantDTO> participants;

    @Schema(description = "CaptureSessions")
    private List<CaptureSessionDTO> captureSessions;

    @Schema(description = "BookingShares")
    private List<ShareBookingDTO> shares;

    @Schema(description = "BookingDeletedAt")
    private Timestamp deletedAt;

    @Schema(description = "BookingCreatedAt")
    private Timestamp createdAt;

    @Schema(description = "BookingModifiedAt")
    private Timestamp modifiedAt;

    public BookingDTO(Booking bookingEntity) {
        this.id = bookingEntity.getId();
        this.caseDTO = new CaseDTO(bookingEntity.getCaseId());
        this.scheduledFor = bookingEntity.getScheduledFor();
        this.participants = Stream.ofNullable(bookingEntity.getParticipants())
            .flatMap(participants ->
                         participants
                             .stream()
                             .filter(participant -> participant.getDeletedAt() == null)
                             .sorted(Comparator.comparing(Participant::getFirstName))
                             .map(ParticipantDTO::new))
            .collect(Collectors.toList());
        this.captureSessions = Stream.ofNullable(bookingEntity.getCaptureSessions())
            .flatMap(captureSessions -> captureSessions.stream().map(CaptureSessionDTO::new))
            .sorted(Comparator.comparing(CaptureSessionDTO::getId))
            .collect(Collectors.toList());
        this.shares = Stream.ofNullable(bookingEntity.getShares())
            .flatMap(shares -> shares.stream().map(ShareBookingDTO::new))
            .sorted(Comparator.comparing(dto -> dto.getSharedWithUser().getFirstName()))
            .collect(Collectors.toList());
        this.deletedAt = bookingEntity.getDeletedAt();
        this.createdAt = bookingEntity.getCreatedAt();
        this.modifiedAt = bookingEntity.getModifiedAt();
    }
}
