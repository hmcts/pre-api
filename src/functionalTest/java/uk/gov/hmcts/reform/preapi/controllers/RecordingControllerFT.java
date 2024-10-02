package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordingControllerFT extends FunctionalTestBase {
    private record CreateRecordingResponse(UUID caseId, UUID bookingId, UUID captureSessionId, UUID recordingId) {
    }

    @DisplayName("Scenario: Restore recording")
    @Test
    void undeleteRecording() {
        var recordingDetails = createRecording();
        assertRecordingExists(recordingDetails.recordingId, true);

        var deleteResponse =
            doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertRecordingExists(recordingDetails.recordingId, false);

        var undeleteResponse = doPostRequest(
            RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId + "/undelete",
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(undeleteResponse, 200);
        assertRecordingExists(recordingDetails.recordingId, true);
    }

    @DisplayName("Scenario: Create and update a recording")
    @Test
    void shouldCreateAndUpdateRecording() throws JsonProcessingException {
        var captureSession = createCaptureSession();
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);
        assertCaptureSessionExists(captureSession.getId(), true);

        // create recording
        var recording = createRecording(captureSession.getId());
        var putRecording1 = putRecording(recording);
        assertResponseCode(putRecording1, 201);
        var response = assertRecordingExists(recording.getId(), true);
        assertThat(response.body().jsonPath().getString("filename")).isEqualTo("example.file");

        // update recording
        recording.setFilename("updated.file");
        var putRecording2 = putRecording(recording);
        assertResponseCode(putRecording2, 204);
        var response2 = assertRecordingExists(recording.getId(), true);
        assertThat(response2.body().jsonPath().getString("filename")).isEqualTo("updated.file");
    }

    @DisplayName("Delete a recording")
    @Test
    void shouldDeleteRecording() {
        var recording = createRecording();
        assertRecordingExists(recording.recordingId, true);

        var deleteResponse =
            doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recording.recordingId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertRecordingExists(recording.recordingId, false);
    }

    @DisplayName("Delete a recording that does not exist")
    @Test
    void deleteRecordingThatDoesntExist() {
        var id = UUID.randomUUID();
        assertRecordingExists(id, false);

        var deleteResponse = doDeleteRequest(RECORDINGS_ENDPOINT + "/" + id, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 404);
    }

    @DisplayName("Undelete a recording should cascade to associated capture sessions, bookings and cases")
    @Test
    void shouldUndeleteRecording() {
        // create recording
        var recordingDetails = createRecording();
        assertRecordingExists(recordingDetails.recordingId, true);
        assertCaptureSessionExists(recordingDetails.captureSessionId, true);
        assertBookingExists(recordingDetails.bookingId, true);
        assertCaseExists(recordingDetails.caseId, true);

        // must delete all recordings associated to case before deleting case
        var deleteRecording =
            doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteRecording, 200);

        // delete case (deleting associated bookings + capture sessions)
        var deleteCase =
            doDeleteRequest(CASES_ENDPOINT + "/" + recordingDetails.caseId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCase, 200);
        assertRecordingExists(recordingDetails.recordingId, false);
        assertCaptureSessionExists(recordingDetails.captureSessionId, false);
        assertBookingExists(recordingDetails.bookingId, false);
        assertCaseExists(recordingDetails.caseId, false);

        // undelete recording (and associated capture session, booking, case)
        var undeleteRecording = doPostRequest(
            RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId + "/undelete",
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(undeleteRecording, 200);
        assertRecordingExists(recordingDetails.recordingId, true);
        assertCaptureSessionExists(recordingDetails.captureSessionId, true);
        assertBookingExists(recordingDetails.bookingId, true);
        assertCaseExists(recordingDetails.caseId, true);
    }

    @DisplayName("Should sort by created at desc when sort param not set and by sort param otherwise")
    @Test
    void getRecordingsSortBy() throws JsonProcessingException {
        var details = createRecording();
        assertRecordingExists(details.recordingId, true);
        assertCaptureSessionExists(details.captureSessionId, true);
        assertBookingExists(details.bookingId, true);
        assertCaseExists(details.caseId, true);

        var recording2 = createRecording(details.captureSessionId);
        recording2.setParentRecordingId(details.recordingId);
        recording2.setVersion(2);
        var putRecording2 = putRecording(recording2);
        assertResponseCode(putRecording2, 201);
        assertRecordingExists(recording2.getId(), true);

        var getRecordings1 = doGetRequest(
            RECORDINGS_ENDPOINT + "?captureSessionId=" + details.captureSessionId,
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getRecordings1, 200);
        var recordings1 = getRecordings1.jsonPath().getList("_embedded.recordingDTOList", RecordingDTO.class);

        // default sort by createdAt desc
        assertThat(recordings1.size()).isEqualTo(2);
        assertThat(recordings1.getFirst().getId()).isEqualTo(recording2.getId());
        assertThat(recordings1.getLast().getId()).isEqualTo(details.recordingId);
        assertThat(recordings1.getFirst().getCreatedAt()).isAfter(recordings1.getLast().getCreatedAt());

        var getRecordings2 = doGetRequest(
            RECORDINGS_ENDPOINT + "?sort=createdAt,asc&captureSessionId=" + details.captureSessionId,
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getRecordings2, 200);
        var recordings2 = getRecordings2.jsonPath().getList("_embedded.recordingDTOList", RecordingDTO.class);

        // sort in opposite direction (createdAt asc)
        assertThat(recordings2.size()).isEqualTo(2);
        assertThat(recordings2.getFirst().getId()).isEqualTo(details.recordingId);
        assertThat(recordings2.getLast().getId()).isEqualTo(recording2.getId());
        assertThat(recordings2.getFirst().getCreatedAt()).isBefore(recordings2.getLast().getCreatedAt());
    }

    @DisplayName("Should throw 400 error when sort param is invalid")
    @Test
    void getRecordingsSortInvalidParam() {
        var getRecordings = doGetRequest(
            RECORDINGS_ENDPOINT + "?sort=invalidParam,asc",
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getRecordings, 400);

        assertThat(getRecordings.body().jsonPath().getString("message"))
            .isEqualTo("Invalid sort parameter 'invalidParam' for 'uk.gov.hmcts.reform.preapi.entities.Recording'");
    }

    private CreateRecordingDTO createRecording(UUID captureSessionId) {
        var dto = new CreateRecordingDTO();
        dto.setId(UUID.randomUUID());
        dto.setCaptureSessionId(captureSessionId);
        dto.setEditInstructions("{}");
        dto.setVersion(1);
        dto.setUrl("example url");
        dto.setFilename("example.file");
        return dto;
    }

    private CreateRecordingResponse createRecording() {
        var response = doPostRequest("/testing-support/should-delete-recordings-for-booking", null);
        assertResponseCode(response, 200);
        return response.body().jsonPath().getObject("", CreateRecordingResponse.class);
    }

    private CreateCaptureSessionDTO createCaptureSession() {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath().getUUID("bookingId");

        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        dto.setOrigin(RecordingOrigin.PRE);
        return dto;
    }

    private Response putCaptureSession(CreateCaptureSessionDTO dto) throws JsonProcessingException {
        return doPutRequest(
            CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }

    private Response putRecording(CreateRecordingDTO dto) throws JsonProcessingException {
        return doPutRequest(
            RECORDINGS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.SUPER_USER
        );
    }
}
