package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

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

    @DisplayName("Scenario: Create and update a recording")
    @Test
    void shouldCreateAndUpdateRecording() throws JsonProcessingException {
        var captureSession = createCaptureSession();
        var putCaptureSession = doPutRequest(
            CAPTURE_SESSIONS_ENDPOINT + "/" + captureSession.getId(),
            OBJECT_MAPPER.writeValueAsString(captureSession),
            true
        );
        assertResponseCode(putCaptureSession, 201);
        assertCaptureSessionExists(captureSession.getId(), true);

        // create recording
        var recording = createRecording(captureSession.getId());
        var putRecording1 = doPutRequest(
            RECORDINGS_ENDPOINT + "/" + recording.getId(),
            OBJECT_MAPPER.writeValueAsString(recording),
            true
        );
        assertResponseCode(putRecording1, 201);
        var response = assertRecordingExists(recording.getId(), true);
        assertThat(response.body().jsonPath().getString("filename")).isEqualTo("example.file");

        // update recording
        recording.setFilename("updated.file");
        var putRecording2 = doPutRequest(
            RECORDINGS_ENDPOINT + "/" + recording.getId(),
            OBJECT_MAPPER.writeValueAsString(recording),
            true
        );
        assertResponseCode(putRecording2, 204);
        var response2 = assertRecordingExists(recording.getId(), true);
        assertThat(response2.body().jsonPath().getString("filename")).isEqualTo("updated.file");
    }

    @DisplayName("Delete a recording")
    @Test
    void shouldDeleteRecording() {
        var recording = createRecording();
        assertRecordingExists(recording.recordingId, true);

        var deleteResponse = doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recording.recordingId, true);
        assertResponseCode(deleteResponse, 200);
        assertRecordingExists(recording.recordingId, false);
    }

    @DisplayName("Delete a recording that does not exist")
    @Test
    void deleteRecordingThatDoesntExist() {
        var id = UUID.randomUUID();
        assertRecordingExists(id, false);

        var deleteResponse = doDeleteRequest(RECORDINGS_ENDPOINT + "/" + id, true);
        assertResponseCode(deleteResponse, 404);
    }

    private CreateRecordingDTO createRecording(UUID captureSessionId) {
        var dto = new CreateRecordingDTO();
        dto.setId(UUID.randomUUID());
        dto.setCaptureSessionId(captureSessionId);
        dto.setDuration(null);
        dto.setEditInstructions("{}");
        dto.setVersion(1);
        dto.setUrl("example url");
        dto.setFilename("example.file");
        return dto;
    }

    private CreateRecordingResponse createRecording() {
        var response = doPostRequest("/testing-support/should-delete-recordings-for-booking", false);
        assertResponseCode(response, 200);
        return response.body().jsonPath().getObject("", CreateRecordingResponse.class);
    }

    private CreateCaptureSessionDTO createCaptureSession() {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath().getUUID("bookingId");

        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        dto.setOrigin(RecordingOrigin.PRE);
        return dto;
    }
}
