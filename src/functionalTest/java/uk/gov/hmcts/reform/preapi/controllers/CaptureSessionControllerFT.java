package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaptureSessionControllerFT extends FunctionalTestBase {
    @Test
    @DisplayName("Scenario: Delete capture session with recordings")
    void shouldNotDeleteCaptureSessionWithRecordings() {
        var postResponseData = doPostRequest("/testing-support/should-delete-recordings-for-booking", null)
            .body().jsonPath();
        var captureSessionId = postResponseData.getUUID("captureSessionId");
        var recordingId = postResponseData.getUUID("recordingId");

        assertCaptureSessionExists(captureSessionId, true);
        assertRecordingExists(recordingId, true);

        var deleteCaptureSessionResponse =
            doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCaptureSessionResponse, 400);
        assertThat(deleteCaptureSessionResponse.getBody().jsonPath().getString("message"))
            .isEqualTo("Cannot delete because an associated recording has not been deleted.");

        assertCaptureSessionExists(captureSessionId, true);
    }

    @Test
    @DisplayName("Scenario: Delete capture session without recordings")
    void shouldDeleteCaptureSessionWithoutRecordings() {
        var postResponseData = doPostRequest("/testing-support/should-delete-recordings-for-booking", null)
            .body().jsonPath();
        var captureSessionId = postResponseData.getUUID("captureSessionId");
        var recordingId = postResponseData.getUUID("recordingId");

        assertCaptureSessionExists(captureSessionId, true);
        assertRecordingExists(recordingId, true);

        var deleteRecordingResponse =
            doDeleteRequest(RECORDINGS_ENDPOINT + "/" + recordingId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteRecordingResponse, 200);
        assertRecordingExists(recordingId, false);

        var deleteCaptureSessionResponse =
            doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + captureSessionId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCaptureSessionResponse, 200);
        assertCaptureSessionExists(captureSessionId, false);
    }

    @Test
    @DisplayName("Scenario: Delete capture session")
    void shouldDeleteCaptureSession() throws JsonProcessingException {
        var dto = createCaptureSession();
        dto.setStatus(RecordingStatus.NO_RECORDING);

        // create capture session
        var putResponse = putCaptureSession(dto);
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId());

        // see it is available before deletion
        assertCaptureSessionExists(dto.getId(), true);

        // delete capture session
        var deleteResponse =
            doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);

        // see it is no longer available after deletion
        assertCaptureSessionExists(dto.getId(), false);

        var searchCaptureSessionResponse = doGetRequest(
            CAPTURE_SESSIONS_ENDPOINT + "?bookingId=" + dto.getBookingId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(searchCaptureSessionResponse, 200);
        assertThat(searchCaptureSessionResponse.getBody().jsonPath().getInt("page.totalElements")).isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario: Create and update a capture session")
    void shouldCreateCaptureSession() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", null)
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

    @Test
    @DisplayName("Scenario: Create and update a capture session when case is closed")
    void shouldCreateCaptureSessionCaseClosed() throws JsonProcessingException {
        var res = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath();
        var bookingId = res.getUUID("bookingId");

        // create capture session
        var dto = createCaptureSession(bookingId);
        dto.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        var putResponse = putCaptureSession(dto);
        assertResponseCode(putResponse, 201);
        assertCaptureSessionExists(dto.getId(), true);

        var caseId = res.getUUID("caseId");
        // update case to closed
        var aCase = assertCaseExists(caseId, true)
            .body().jsonPath().getObject("", CaseDTO.class);

        var updateCaseDto = convertDtoToCreateDto(aCase);
        updateCaseDto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        updateCaseDto.setState(CaseState.CLOSED);
        var putCase = putCase(updateCaseDto);
        assertResponseCode(putCase, 204);

        // attempt update capture session
        dto.setStatus(RecordingStatus.RECORDING);
        var putCaptureSession1 = putCaptureSession(dto);
        assertResponseCode(putCaptureSession1, 400);
        assertThat(putCaptureSession1.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Resource CaptureSession(" + dto.getId()
                    + ") is associated with a case in the state CLOSED. Must be in state OPEN."
            );

        // attempt create capture session
        var dto2 = createCaptureSession(bookingId);
        var putCaptureSession2 = putCaptureSession(dto2);
        assertResponseCode(putCaptureSession2, 400);
        assertThat(putCaptureSession2.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Resource CaptureSession(" + dto2.getId()
                    + ") is associated with a case in the state CLOSED. Must be in state OPEN."
            );
    }

    @Test
    @DisplayName("Scenario: Create and update a capture session when case is pending closure")
    void shouldCreateCaptureSessionCasePendingClosure() throws JsonProcessingException {
        var res = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath();
        var bookingId = res.getUUID("bookingId");

        // create capture session
        var dto = createCaptureSession(bookingId);
        dto.setStatus(RecordingStatus.NO_RECORDING);
        var putResponse = putCaptureSession(dto);
        assertResponseCode(putResponse, 201);
        assertCaptureSessionExists(dto.getId(), true);

        var caseId = res.getUUID("caseId");
        // update case to pending closure
        var aCase = assertCaseExists(caseId, true)
            .body().jsonPath().getObject("", CaseDTO.class);

        var updateCaseDto = convertDtoToCreateDto(aCase);
        updateCaseDto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        updateCaseDto.setState(CaseState.PENDING_CLOSURE);
        var putCase = putCase(updateCaseDto);
        assertResponseCode(putCase, 204);

        // attempt update capture session
        dto.setStatus(RecordingStatus.FAILURE);
        var putCaptureSession1 = putCaptureSession(dto);
        assertResponseCode(putCaptureSession1, 400);
        assertThat(putCaptureSession1.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Resource CaptureSession(" + dto.getId()
                    + ") is associated with a case in the state PENDING_CLOSURE. Must be in state OPEN."
            );

        // attempt create capture session
        var dto2 = createCaptureSession(bookingId);
        var putCaptureSession2 = putCaptureSession(dto2);
        assertResponseCode(putCaptureSession2, 400);
        assertThat(putCaptureSession2.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Resource CaptureSession(" + dto2.getId()
                    + ") is associated with a case in the state PENDING_CLOSURE. Must be in state OPEN."
            );
    }

    @Test
    @DisplayName("Scenario: Restore capture session")
    void undeleteCaptureSession() throws JsonProcessingException {
        // create capture session
        var dto = createCaptureSession();
        dto.setStatus(RecordingStatus.NO_RECORDING);
        var putResponse = putCaptureSession(dto);
        assertResponseCode(putResponse, 201);
        assertCaptureSessionExists(dto.getId(), true);
        var bookingResponse = assertBookingExists(dto.getBookingId(), true);
        var caseId = bookingResponse.as(BookingDTO.class).getCaseDTO().getId();
        assertCaseExists(caseId, true);

        // delete case (and associated bookings + capture session)
        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertCaptureSessionExists(dto.getId(), false);
        assertBookingExists(dto.getBookingId(), false);
        assertCaseExists(caseId, false);

        // undelete capture session
        var undeleteResponse =
            doPostRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId() + "/undelete", TestingSupportRoles.SUPER_USER);
        assertResponseCode(undeleteResponse, 200);
        assertCaptureSessionExists(dto.getId(), true);
        assertBookingExists(dto.getBookingId(), true);
        assertCaseExists(caseId, true);
    }

    @Test
    @DisplayName("Should return 400 when trying to delete a capture session in incorrect state")
    void deleteCaptureSessionWrongState() throws JsonProcessingException {
        // capture session state: RECORDING
        var dto1 = createCaptureSession();
        dto1.setStatus(RecordingStatus.RECORDING);
        var putResponse1 = putCaptureSession(dto1);
        assertResponseCode(putResponse1, 201);
        assertCaptureSessionExists(dto1.getId(), true);

        var deleteCaptureSessionRecording =
            doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto1.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCaptureSessionRecording, 400);
        assertThat(deleteCaptureSessionRecording.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Capture Session (" + dto1.getId()
                    + ") must be in state RECORDING_AVAILABLE, FAILURE or NO_RECORDING to be deleted. "
                    + "Current state is RECORDING"
            );

        // capture session state: STANDBY
        var dto2 = createCaptureSession();
        dto2.setStatus(RecordingStatus.STANDBY);
        var putResponse2 = putCaptureSession(dto2);
        assertResponseCode(putResponse2, 201);
        assertCaptureSessionExists(dto2.getId(), true);

        var deleteCaptureSessionStandby =
            doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto2.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCaptureSessionStandby, 400);
        assertThat(deleteCaptureSessionStandby.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Capture Session (" + dto2.getId()
                    + ") must be in state RECORDING_AVAILABLE, FAILURE or NO_RECORDING to be deleted."
                    + " Current state is STANDBY"
            );

        // capture session state: INITIALISING
        var dto3 = createCaptureSession();
        dto3.setStatus(RecordingStatus.INITIALISING);
        var putResponse3 = putCaptureSession(dto3);
        assertResponseCode(putResponse3, 201);
        assertCaptureSessionExists(dto3.getId(), true);

        var deleteCaptureSessionInit =
            doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto3.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCaptureSessionInit, 400);
        assertThat(deleteCaptureSessionInit.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Capture Session (" + dto3.getId()
                    + ") must be in state RECORDING_AVAILABLE, FAILURE or NO_RECORDING to be deleted. "
                    + "Current state is INITIALISING"
            );

        // capture session state: PROCESSING
        var dto4 = createCaptureSession();
        dto4.setStatus(RecordingStatus.PROCESSING);
        var putResponse4 = putCaptureSession(dto4);
        assertResponseCode(putResponse4, 201);
        assertCaptureSessionExists(dto4.getId(), true);

        var deleteCaptureSessionProcessing =
            doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto4.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCaptureSessionProcessing, 400);
        assertThat(deleteCaptureSessionProcessing.getBody().jsonPath().getString("message"))
            .isEqualTo(
                "Capture Session (" + dto4.getId()
                    + ") must be in state RECORDING_AVAILABLE, FAILURE or NO_RECORDING to be deleted. "
                    + "Current state is PROCESSING"
            );
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=true")
    class WithMigratedDataEnabled extends FunctionalTestBase {
        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should allow access to capture sessions with VODAFONE case to non super user requests")
        void getByIdVfCase(TestingSupportRoles role) throws JsonProcessingException {
            CreateCaptureSessionDTO dto = setupCaptureSessionWithOrigins(
                RecordingOrigin.VODAFONE,
                RecordingOrigin.PRE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession = putCaptureSession(dto);
            assertResponseCode(putCaptureSession, 201);
            assertCaptureSessionExists(dto.getId(), true);

            Response getResponse = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), role);
            assertResponseCode(getResponse, 200);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should allow access to VODAFONE capture sessions with PRE case to non super user requests")
        void getByIdVfCaptureSession(TestingSupportRoles role) throws JsonProcessingException {
            CreateCaptureSessionDTO dto = setupCaptureSessionWithOrigins(
                RecordingOrigin.PRE,
                RecordingOrigin.VODAFONE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession = putCaptureSession(dto);
            assertResponseCode(putCaptureSession, 201);
            assertCaptureSessionExists(dto.getId(), true);

            Response getResponse = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), role);
            assertResponseCode(getResponse, 200);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should allow access to capture session with VODAFONE origin to non super user requests in search")
        void getCases(TestingSupportRoles role) throws JsonProcessingException {
            // vf case, pre capture session
            CreateCaptureSessionDTO dto1 = setupCaptureSessionWithOrigins(
                RecordingOrigin.VODAFONE,
                RecordingOrigin.PRE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession1 = putCaptureSession(dto1);
            assertResponseCode(putCaptureSession1, 201);
            assertCaptureSessionExists(dto1.getId(), true);

            // pre case, vf capture session
            CreateCaptureSessionDTO dto2 = setupCaptureSessionWithOrigins(
                RecordingOrigin.PRE,
                RecordingOrigin.VODAFONE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession2 = putCaptureSession(dto2);
            assertResponseCode(putCaptureSession2, 201);
            assertCaptureSessionExists(dto2.getId(), true);

            Response getResponse = doGetRequest(CAPTURE_SESSIONS_ENDPOINT
                                                    + "?courtId="
                                                    + authenticatedUserIds.get(role).courtId(),
                                                role);
            assertResponseCode(getResponse, 200);

            List<UUID> foundCaptureSessions = getResponse.getBody()
                .jsonPath()
                .getList("_embedded.captureSessionDTOList", CaptureSessionDTO.class)
                .stream()
                .map(CreateCaptureSessionDTO::getId)
                .toList();
            assertThat(foundCaptureSessions).isNotEmpty();
            assertThat(foundCaptureSessions).containsAll(List.of(dto1.getId(), dto2.getId()));
        }
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=false")
    class WithMigratedDataDisabled extends FunctionalTestBase {
        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not allow access to capture sessions with VODAFONE case to non super user requests")
        void getByIdVfCase(TestingSupportRoles role) throws JsonProcessingException {
            CreateCaptureSessionDTO dto = setupCaptureSessionWithOrigins(
                RecordingOrigin.VODAFONE,
                RecordingOrigin.PRE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession = putCaptureSession(dto);
            assertResponseCode(putCaptureSession, 201);
            assertCaptureSessionExists(dto.getId(), true);

            Response getResponse = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), role);
            assertResponseCode(getResponse, 403);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not allow access to VODAFONE capture sessions with PRE case to non super user requests")
        void getByIdVfCaptureSession(TestingSupportRoles role) throws JsonProcessingException {
            CreateCaptureSessionDTO dto = setupCaptureSessionWithOrigins(
                RecordingOrigin.PRE,
                RecordingOrigin.VODAFONE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession = putCaptureSession(dto);
            assertResponseCode(putCaptureSession, 201);
            assertCaptureSessionExists(dto.getId(), true);

            Response getResponse = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), role);
            assertResponseCode(getResponse, 403);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not allow access to capture session with VODAFONE origin to non super user in search")
        void getCases(TestingSupportRoles role) throws JsonProcessingException {
            // vf case, pre capture session
            CreateCaptureSessionDTO dto1 = setupCaptureSessionWithOrigins(
                RecordingOrigin.VODAFONE,
                RecordingOrigin.PRE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession1 = putCaptureSession(dto1);
            assertResponseCode(putCaptureSession1, 201);
            assertCaptureSessionExists(dto1.getId(), true);

            // pre case, vf capture session
            CreateCaptureSessionDTO dto2 = setupCaptureSessionWithOrigins(
                RecordingOrigin.PRE,
                RecordingOrigin.VODAFONE,
                authenticatedUserIds.get(role).courtId()
            );
            Response putCaptureSession2 = putCaptureSession(dto2);
            assertResponseCode(putCaptureSession2, 201);
            assertCaptureSessionExists(dto2.getId(), true);

            Response getResponse = doGetRequest(CAPTURE_SESSIONS_ENDPOINT
                                                    + "?courtId="
                                                    + authenticatedUserIds.get(role).courtId(),
                                                role);
            assertResponseCode(getResponse, 200);

            List<UUID> foundCaptureSessions = getResponse.getBody()
                .jsonPath()
                .getList("_embedded.captureSessionDTOList", CaptureSessionDTO.class)
                .stream()
                .map(CreateCaptureSessionDTO::getId)
                .toList();
            assertThat(foundCaptureSessions).doesNotContainAnyElementsOf(List.of(dto1.getId(), dto2.getId()));
        }
    }
}
