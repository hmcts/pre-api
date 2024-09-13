package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CaptureSessionControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Delete capture session with recordings")
    @Test
    void shouldNotDeleteCaptureSessionWithRecordings() {
        var postResponseData = doPostRequest("/testing-support/should-delete-recordings-for-booking", false)
            .body().jsonPath();
        var captureSessionId = postResponseData.getUUID("captureSessionId");
        var recordingId = postResponseData.getUUID("recordingId");

        assertCaptureSessionExists(captureSessionId, true);
        assertRecordingExists(recordingId, true);

        var deleteCaptureSessionResponse = doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(deleteCaptureSessionResponse, 400);
        assertThat(deleteCaptureSessionResponse.getBody().jsonPath().getString("message"))
            .isEqualTo("Cannot delete because and associated recording has not been deleted.");

        assertCaptureSessionExists(captureSessionId, true);
    }

    @DisplayName("Scenario: Delete capture session without recordings")
    @Test
    void shouldDeleteCaptureSessionWithoutRecordings() {
        var postResponseData = doPostRequest("/testing-support/should-delete-recordings-for-booking", false)
            .body().jsonPath();
        var captureSessionId = postResponseData.getUUID("captureSessionId");
        var recordingId = postResponseData.getUUID("recordingId");

        assertCaptureSessionExists(captureSessionId, true);
        assertRecordingExists(recordingId, true);

        var deleteRecordingResponse = doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingId, true);
        assertResponseCode(deleteRecordingResponse, 200);
        assertRecordingExists(recordingId, false);

        var deleteCaptureSessionResponse = doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, true);
        assertResponseCode(deleteCaptureSessionResponse, 200);
        assertCaptureSessionExists(captureSessionId, false);
    }

    @DisplayName("Scenario: Delete capture session")
    @Test
    void shouldDeleteCaptureSession() throws JsonProcessingException {
        var dto = createCaptureSession();

        // create capture session
        var putResponse = putCaptureSession(dto);
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId());

        // see it is available before deletion
        assertCaptureSessionExists(dto.getId(), true);

        // delete capture session
        var deleteResponse = doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(deleteResponse, 200);

        // see it is no longer available after deletion
        assertCaptureSessionExists(dto.getId(), false);

        var searchCaptureSessionResponse = doGetRequest(
            CAPTURE_SESSIONS_ENDPOINT + "?bookingId=" + dto.getBookingId(),
            true
        );
        assertResponseCode(searchCaptureSessionResponse, 200);
        assertThat(searchCaptureSessionResponse.getBody().jsonPath().getInt("page.totalElements")).isEqualTo(0);
    }

    @DisplayName("Scenario: Create and update a capture session")
    @Test
    void shouldCreateCaptureSession() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath().getUUID("bookingId");

        var dto = createCaptureSession(bookingId);

        // create capture session
        var putResponse = putCaptureSession(dto);
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId());

        var getCaptureSession1 = assertCaptureSessionExists(dto.getId(), true);
        assertThat(getCaptureSession1.getBody().jsonPath().getString("status"))
            .isEqualTo(RecordingStatus.STANDBY.toString());

        // update capture session
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        var updateResponse = putCaptureSession(dto);
        assertResponseCode(updateResponse, 204);

        var getCaptureSession2 = assertCaptureSessionExists(dto.getId(), true);
        assertThat(getCaptureSession2.getBody().jsonPath().getString("status"))
            .isEqualTo(RecordingStatus.RECORDING_AVAILABLE.toString());
    }

    @DisplayName("Scenario: Restore capture session")
    @Test
    void undeleteCaptureSession() throws JsonProcessingException {
        // create capture session
        var dto = createCaptureSession();
        var putResponse = putCaptureSession(dto);
        assertResponseCode(putResponse, 201);
        assertCaptureSessionExists(dto.getId(), true);
        var bookingResponse = assertBookingExists(dto.getBookingId(), true);
        var caseId = bookingResponse.as(BookingDTO.class).getCaseDTO().getId();
        assertCaseExists(caseId, true);

        // delete case (and associated bookings + capture session)
        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseId, true);
        assertResponseCode(deleteResponse, 200);
        assertCaptureSessionExists(dto.getId(), false);
        assertBookingExists(dto.getBookingId(), false);
        assertCaseExists(caseId, false);

        // undelete capture session
        var undeleteResponse = doPostRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId() + "/undelete", true);
        assertResponseCode(undeleteResponse, 200);
        assertCaptureSessionExists(dto.getId(), true);
        assertBookingExists(dto.getBookingId(), true);
        assertCaseExists(caseId, true);
    }

    private CreateCaptureSessionDTO createCaptureSession(UUID bookingId) {
        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setOrigin(RecordingOrigin.PRE);
        return dto;
    }

    private CreateCaptureSessionDTO createCaptureSession() {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath().getUUID("bookingId");

        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setOrigin(RecordingOrigin.PRE);
        return dto;
    }
}
