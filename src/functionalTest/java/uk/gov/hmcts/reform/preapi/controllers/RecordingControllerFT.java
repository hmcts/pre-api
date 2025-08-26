package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RecordingControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Restore recording")
    @Test
    void undeleteRecording() {
        var recordingDetails = createRecording();
        assertRecordingExists(recordingDetails.recordingId(), true);

        var deleteResponse =
            doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertRecordingExists(recordingDetails.recordingId(), false);

        var undeleteResponse = doPostRequest(
            RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId() + "/undelete",
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(undeleteResponse, 200);
        assertRecordingExists(recordingDetails.recordingId(), true);
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

    @DisplayName("Scenario: Create and update a recording in closed case")
    @Test
    void upsertRecordingCaseClosed() throws JsonProcessingException {
        // create case, booking and capture session
        var captureSession = createCaptureSession();
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);
        assertCaptureSessionExists(captureSession.getId(), true);

        // create recording (to be updated)
        var recording1 = createRecording(captureSession.getId());
        var putRecording1 = putRecording(recording1);
        assertResponseCode(putRecording1, 201);
        var response = assertRecordingExists(recording1.getId(), true);
        assertThat(response.body().jsonPath().getString("filename")).isEqualTo("example.file");

        // update case to closed
        var aCase = convertDtoToCreateDto(assertBookingExists(captureSession.getBookingId(), true)
                                              .jsonPath().getObject("", BookingDTO.class).getCaseDTO());
        aCase.setState(CaseState.CLOSED);
        aCase.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        var putCase = putCase(aCase);
        assertResponseCode(putCase, 204);

        // attempt to update recording
        recording1.setFilename("updated.file");
        var putRecording2 = putRecording(recording1);
        assertResponseCode(putRecording2, 400);
        assertThat(putRecording2.body().jsonPath().getString("message"))
            .isEqualTo("Resource Recording("
                           + recording1.getId()
                           + ") is associated with a case in the state CLOSED. Must be in state OPEN.");

        // attempt to create recording
        var recording2 = createRecording(captureSession.getId());
        var putRecording3 = putRecording(recording2);
        assertResponseCode(putRecording3, 400);
        assertThat(putRecording3.body().jsonPath().getString("message"))
            .isEqualTo("Resource Recording("
                           + recording2.getId()
                           + ") is associated with a case in the state CLOSED. Must be in state OPEN.");
    }

    @DisplayName("Scenario: Create and update a recording in case pending closure")
    @Test
    void upsertRecordingCasePendingClosure() throws JsonProcessingException {
        // create case, booking and capture session
        var captureSession = createCaptureSession();
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);
        assertCaptureSessionExists(captureSession.getId(), true);

        // create recording (to be updated)
        var recording1 = createRecording(captureSession.getId());
        var putRecording1 = putRecording(recording1);
        assertResponseCode(putRecording1, 201);
        var response = assertRecordingExists(recording1.getId(), true);
        assertThat(response.body().jsonPath().getString("filename")).isEqualTo("example.file");

        // update case to pending closure
        var aCase = convertDtoToCreateDto(assertBookingExists(captureSession.getBookingId(), true)
                                              .jsonPath().getObject("", BookingDTO.class).getCaseDTO());
        aCase.setState(CaseState.PENDING_CLOSURE);
        aCase.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        var putCase = putCase(aCase);
        assertResponseCode(putCase, 204);

        // attempt to update recording
        recording1.setFilename("updated.file");
        var putRecording2 = putRecording(recording1);
        assertResponseCode(putRecording2, 400);
        assertThat(putRecording2.body().jsonPath().getString("message"))
            .isEqualTo("Resource Recording("
                           + recording1.getId()
                           + ") is associated with a case in the state PENDING_CLOSURE. Must be in state OPEN.");

        // attempt to create recording
        var recording2 = createRecording(captureSession.getId());
        var putRecording3 = putRecording(recording2);
        assertResponseCode(putRecording3, 400);
        assertThat(putRecording3.body().jsonPath().getString("message"))
            .isEqualTo("Resource Recording("
                           + recording2.getId()
                           + ") is associated with a case in the state PENDING_CLOSURE. Must be in state OPEN.");
    }

    @DisplayName("Delete a recording")
    @Test
    void shouldDeleteRecording() {
        var recording = createRecording();
        assertRecordingExists(recording.recordingId(), true);

        var deleteResponse =
            doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recording.recordingId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertRecordingExists(recording.recordingId(), false);
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
        assertRecordingExists(recordingDetails.recordingId(), true);
        assertCaptureSessionExists(recordingDetails.captureSessionId(), true);
        assertBookingExists(recordingDetails.bookingId(), true);
        assertCaseExists(recordingDetails.caseId(), true);

        // must delete all recordings associated to case before deleting case
        var deleteRecording =
            doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteRecording, 200);

        // delete case (deleting associated bookings + capture sessions)
        var deleteCase =
            doDeleteRequest(CASES_ENDPOINT + "/" + recordingDetails.caseId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCase, 200);
        assertRecordingExists(recordingDetails.recordingId(), false);
        assertCaptureSessionExists(recordingDetails.captureSessionId(), false);
        assertBookingExists(recordingDetails.bookingId(), false);
        assertCaseExists(recordingDetails.caseId(), false);

        // undelete recording (and associated capture session, booking, case)
        var undeleteRecording = doPostRequest(
            RECORDINGS_ENDPOINT + "/" + recordingDetails.recordingId() + "/undelete",
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(undeleteRecording, 200);
        assertRecordingExists(recordingDetails.recordingId(), true);
        assertCaptureSessionExists(recordingDetails.captureSessionId(), true);
        assertBookingExists(recordingDetails.bookingId(), true);
        assertCaseExists(recordingDetails.caseId(), true);
    }

    @DisplayName("Should sort by created at desc when sort param not set and by sort param otherwise")
    @Test
    void getRecordingsSortBy() throws JsonProcessingException {
        var details = createRecording();
        assertRecordingExists(details.recordingId(), true);
        assertCaptureSessionExists(details.captureSessionId(), true);
        assertBookingExists(details.bookingId(), true);
        assertCaseExists(details.caseId(), true);

        var recording2 = createRecording(details.captureSessionId());
        recording2.setParentRecordingId(details.recordingId());
        recording2.setVersion(2);
        var putRecording2 = putRecording(recording2);
        assertResponseCode(putRecording2, 201);
        assertRecordingExists(recording2.getId(), true);

        var getRecordings1 = doGetRequest(
            RECORDINGS_ENDPOINT + "?captureSessionId=" + details.captureSessionId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getRecordings1, 200);
        var recordings1 = getRecordings1.jsonPath().getList("_embedded.recordingDTOList", RecordingDTO.class);

        // default sort by createdAt desc
        assertThat(recordings1.size()).isEqualTo(2);
        assertThat(recordings1.getFirst().getId()).isEqualTo(recording2.getId());
        assertThat(recordings1.getLast().getId()).isEqualTo(details.recordingId());
        assertThat(recordings1.getFirst().getCreatedAt()).isAfter(recordings1.getLast().getCreatedAt());

        var getRecordings2 = doGetRequest(
            RECORDINGS_ENDPOINT + "?sort=createdAt,asc&captureSessionId=" + details.captureSessionId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getRecordings2, 200);
        var recordings2 = getRecordings2.jsonPath().getList("_embedded.recordingDTOList", RecordingDTO.class);

        // sort in opposite direction (createdAt asc)
        assertThat(recordings2.size()).isEqualTo(2);
        assertThat(recordings2.getFirst().getId()).isEqualTo(details.recordingId());
        assertThat(recordings2.getLast().getId()).isEqualTo(recording2.getId());
        assertThat(recordings2.getFirst().getCreatedAt()).isBefore(recordings2.getLast().getCreatedAt());
    }

    @Test
    @DisplayName("Should search recordings by version number")
    void getRecordingsByVersion() throws JsonProcessingException {
        CreateRecordingResponse details = createRecording();
        assertRecordingExists(details.recordingId(), true);
        assertCaptureSessionExists(details.captureSessionId(), true);
        assertBookingExists(details.bookingId(), true);
        assertCaseExists(details.caseId(), true);

        CreateRecordingDTO recording2 = createRecording(details.captureSessionId());
        recording2.setParentRecordingId(details.recordingId());
        recording2.setVersion(2);
        Response putRecording2 = putRecording(recording2);
        assertResponseCode(putRecording2, 201);
        assertRecordingExists(recording2.getId(), true);

        // search version 1
        Response getRecordings1 = doGetRequest(
            RECORDINGS_ENDPOINT + "?version=1&captureSessionId=" + details.captureSessionId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getRecordings1, 200);
        List<RecordingDTO> recordings1 = getRecordings1.jsonPath()
            .getList("_embedded.recordingDTOList", RecordingDTO.class);
        assertThat(recordings1.size()).isEqualTo(1);
        assertThat(recordings1.getFirst().getId()).isEqualTo(details.recordingId());
        assertThat(recordings1.getFirst().getVersion()).isEqualTo(1);

        // search version 2
        Response getRecordings2 = doGetRequest(
            RECORDINGS_ENDPOINT + "?version=2&captureSessionId=" + details.captureSessionId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getRecordings2, 200);
        List<RecordingDTO> recordings2 = getRecordings2.jsonPath()
            .getList("_embedded.recordingDTOList", RecordingDTO.class);
        assertThat(recordings2.size()).isEqualTo(1);
        assertThat(recordings2.getFirst().getId()).isEqualTo(recording2.getId());
        assertThat(recordings2.getFirst().getVersion()).isEqualTo(2);
    }

    @Test
    @DisplayName("Should show correct total version count")
    void getRecordingTotalVersionCount() throws JsonProcessingException {
        // create parent recording
        var details = createRecording();
        var getRecording1 = assertRecordingExists(details.recordingId(), true);
        getRecording1.prettyPrint();
        assertThat(getRecording1.getBody().as(RecordingDTO.class).getTotalVersionCount()).isEqualTo(1);

        // create child recording
        var recording2 = createRecording(details.captureSessionId());
        recording2.setParentRecordingId(details.recordingId());
        recording2.setVersion(2);
        var putRecording2 = putRecording(recording2);
        assertResponseCode(putRecording2, 201);
        var getRecording2 = assertRecordingExists(recording2.getId(), true);
        assertThat(getRecording2.getBody().as(RecordingDTO.class).getTotalVersionCount()).isEqualTo(2);

        // check parent recording
        var getRecording3 = assertRecordingExists(details.recordingId(), true);
        assertThat(getRecording3.getBody().as(RecordingDTO.class).getTotalVersionCount()).isEqualTo(2);
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

    @Test
    @DisplayName("Should throw 403 when LEVEL 4 user attempts to search recordings")
    void getRecordingsLevel4() {
        Response getRecordings = doGetRequest(RECORDINGS_ENDPOINT, TestingSupportRoles.LEVEL_4);
        assertResponseCode(getRecordings, 403);
    }

    @Test
    @DisplayName("Should throw 403 when LEVEL 4 user attempts to get recording by id")
    void getRecordingByIdLevel4() {
        Response getRecordings = doGetRequest(RECORDINGS_ENDPOINT + "/" + UUID.randomUUID(),
                                              TestingSupportRoles.LEVEL_4);
        assertResponseCode(getRecordings, 403);
    }

    @Override
    protected CreateCaptureSessionDTO createCaptureSession() {
        var dto = super.createCaptureSession();
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        return dto;
    }
}
