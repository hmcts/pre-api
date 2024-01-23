package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.util.Date;
import java.util.UUID;

@Data
public class SearchRecordings {
    private UUID captureSessionId;
    private UUID parentRecordingId;
    private UUID participantId;
    private String caseReference;
    @DateTimeFormat(pattern = "yyyy-MM-dd")
    private Date scheduledFor;
}
