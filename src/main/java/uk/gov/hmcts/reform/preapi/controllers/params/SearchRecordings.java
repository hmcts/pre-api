package uk.gov.hmcts.reform.preapi.controllers.params;

import lombok.Data;

import java.util.UUID;

@Data
public class SearchRecordings {
    private UUID captureSessionId;
    private UUID parentRecordingId;
    private UUID participantId;
}
