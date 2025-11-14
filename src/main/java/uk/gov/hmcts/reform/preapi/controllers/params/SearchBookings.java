package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
public class SearchBookings {
    private UUID caseId;
    private String caseReference;
    private UUID courtId;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private LocalDate scheduledFor;
    private UUID participantId;
    private Boolean hasRecordings;
    private Boolean includeDeleted;
    private List<RecordingStatus> captureSessionStatusIn;
    private List<RecordingStatus> captureSessionStatusNotIn;

    public String getCaseReference() {
        return caseReference != null && !caseReference.isEmpty() ? caseReference : null;
    }
}
