package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

public class RecordingControllerFT extends FunctionalTestBase {
    private record CreateRecordingResponse(UUID bookingId, UUID captureSessionId, UUID recordingId) {
    }

    @DisplayName("Scenario: Restore recording")
    @Test
    void undeleteRecording() {
        var recordingDetails = createRecording();
        assertRecordingExists(recordingDetails.recordingId, true);

        var deleteResponse = doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId, true);
        assertResponseCode(deleteResponse, 200);
        assertRecordingExists(recordingDetails.recordingId, false);

        var undeleteResponse = doPostRequest(
            RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId + "/undelete",
            true
        );
        assertResponseCode(undeleteResponse, 200);
        assertRecordingExists(recordingDetails.recordingId, true);
    }

    private CreateRecordingResponse createRecording() {
        var response = doPostRequest("/testing-support/should-delete-recordings-for-booking", false);
        assertResponseCode(response, 200);
        return response.body().jsonPath().getObject("", CreateRecordingResponse.class);
    }
}
