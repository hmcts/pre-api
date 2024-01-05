package uk.gov.hmcts.reform.preapi.controllers.params;

import java.util.Map;
import java.util.UUID;

public record SearchRecordings(UUID captureSessionId, UUID parentRecordingId) {

    private static final String PARAM_CAPTURE_SESSION_ID = "captureSessionId";
    private static final String PARAM_PARENT_RECORDING_ID = "parentRecordingId";

    public static SearchRecordings from(Map<String, String> allParams) {
        return new SearchRecordings(
            allParams.containsKey(PARAM_CAPTURE_SESSION_ID)
                ? UUID.fromString(allParams.get(PARAM_CAPTURE_SESSION_ID))
                : null,
            allParams.containsKey(PARAM_PARENT_RECORDING_ID)
                ? UUID.fromString(allParams.get(PARAM_PARENT_RECORDING_ID))
                : null
        );
    }
}
