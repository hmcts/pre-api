package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
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

    public String getCaseReference() {
        return caseReference != null && !caseReference.isEmpty() ? caseReference : null;
    }
}
