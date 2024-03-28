package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;

public class CaptureSessionControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Delete capture session with recordings")
    @Test
    void shouldNotDeleteCaptureSessionWithRecordings() {
        var postResponseData = doPostRequest("/testing-support/should-delete-recordings-for-booking", false)
            .body().jsonPath();
        var captureSessionId = postResponseData.getUUID("captureSessionId");
        var recordingId = postResponseData.getUUID("recordingId");

        var captureSessionResponse1 = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(captureSessionResponse1, 200);
        assertThat(captureSessionResponse1.getBody().jsonPath().getUUID("id")).isEqualTo(captureSessionId);

        var recordingResponse = doGetRequest(RECORDINGS_ENDPOINT + "/" + recordingId, true);
        assertResponseCode(recordingResponse, 200);
        assertThat(recordingResponse.getBody().jsonPath().getUUID("id")).isEqualTo(recordingId);

        var deleteCaptureSessionResponse = doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(deleteCaptureSessionResponse, 400);
        assertThat(deleteCaptureSessionResponse.getBody().jsonPath().getString("message"))
            .isEqualTo("Cannot delete because and associated recording has not been deleted.");

        var captureSessionResponse2 = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(captureSessionResponse2, 200);
        assertThat(captureSessionResponse2.getBody().jsonPath().getUUID("id")).isEqualTo(captureSessionId);
    }

    @DisplayName("Scenario: Delete capture session without recordings")
    @Test
    void shouldDeleteCaptureSessionWithoutRecordings() {
        var postResponseData = doPostRequest("/testing-support/should-delete-recordings-for-booking", false)
            .body().jsonPath();
        var captureSessionId = postResponseData.getUUID("captureSessionId");
        var recordingId = postResponseData.getUUID("recordingId");

        var captureSessionResponse1 = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(captureSessionResponse1, 200);
        assertThat(captureSessionResponse1.getBody().jsonPath().getUUID("id")).isEqualTo(captureSessionId);

        var recordingResponse = doGetRequest(RECORDINGS_ENDPOINT + "/" + recordingId, true);
        assertResponseCode(recordingResponse, 200);
        assertThat(recordingResponse.getBody().jsonPath().getUUID("id")).isEqualTo(recordingId);

        var deleteRecordingResponse = doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingId, true);
        assertResponseCode(deleteRecordingResponse, 200);

        var recordingResponse2 = doGetRequest(RECORDINGS_ENDPOINT + "/" + recordingId, true);
        assertResponseCode(recordingResponse2, 404);

        var deleteCaptureSessionResponse = doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(deleteCaptureSessionResponse, 200);

        var captureSessionResponse2 = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(captureSessionResponse2, 404);
    }
}
