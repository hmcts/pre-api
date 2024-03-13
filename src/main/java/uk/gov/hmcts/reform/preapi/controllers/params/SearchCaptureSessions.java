package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.time.LocalDate;
import java.util.UUID;

@Data
public class SearchCaptureSessions {
    private String caseReference;
    private UUID bookingId;
    private RecordingOrigin origin;
    private RecordingStatus recordingStatus;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledFor;
    private UUID courtId;

    public String getCaseReference() {
        return caseReference != null && !caseReference.isEmpty() ? caseReference : null;
    }
}
