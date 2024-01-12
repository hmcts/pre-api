package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ShareBookingDTO {
    private UUID id;
    private UUID bookingId;
    private UUID sharedWithUserId;
    private UUID sharedByUserId;

    public ShareBookingDTO(ShareBooking shareBookingEntity) {
        this.id = shareBookingEntity.getId();
        this.bookingId = shareBookingEntity.getBooking().getId();
        this.sharedWithUserId = shareBookingEntity.getSharedWith().getId();
        this.sharedByUserId = shareBookingEntity.getSharedBy().getId();
    }
}
