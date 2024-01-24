package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.util.Date;
import java.util.UUID;

@Data
public class SearchCaptureSessions {
    private String caseReference;
    private UUID bookingId;
    private RecordingOrigin origin;
    private RecordingStatus recordingStatus;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date scheduledFor;
}
