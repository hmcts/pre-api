package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;

import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateShareBookingDTO {
    private UUID id;
    private UUID bookingId;
    private BaseUserDTO sharedWithUser;
    private BaseUserDTO sharedByUser;
}
