package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.ShareBooking;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class ShareBookingDTO {
    private UUID id;
    private UUID bookingId;
    private BaseUserDTO sharedWithUser;
    private BaseUserDTO sharedByUser;
    private Timestamp createdAt;
    private Timestamp deletedAt;

    public ShareBookingDTO(ShareBooking shareBookingEntity) {
        id = shareBookingEntity.getId();
        bookingId = shareBookingEntity.getBooking().getId();
        sharedWithUser = new BaseUserDTO(shareBookingEntity.getSharedWith());
        sharedByUser = new BaseUserDTO(shareBookingEntity.getSharedBy());
        createdAt = shareBookingEntity.getCreatedAt();
        deletedAt = shareBookingEntity.getDeletedAt();
    }
}
