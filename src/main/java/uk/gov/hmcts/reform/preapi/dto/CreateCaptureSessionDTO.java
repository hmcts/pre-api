package uk.gov.hmcts.reform.preapi.dto;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;
import lombok.Data;
import lombok.NoArgsConstructor;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.sql.Timestamp;
import java.util.UUID;

@Data
@NoArgsConstructor
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
public class CreateCaptureSessionDTO {
    private UUID id;
    private UUID bookingId;
    private RecordingOrigin origin;
    private String ingestAddress;
    private String liveOutputUrl;
    private Timestamp startedAt;
    private UUID startedByUserId;
    private Timestamp finishedAt;
    private UUID finishedByUserId;
    private RecordingStatus status;
}
