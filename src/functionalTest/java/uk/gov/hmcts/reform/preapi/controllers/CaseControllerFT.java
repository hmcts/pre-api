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
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CaseControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Create/update a case")
    @Test
    void shouldCreateAndUpdateCase() throws JsonProcessingException {
        var dto = createCase();

        // create a case
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 201);
        assertMatchesDto(dto);

        // update a case
        dto.setTest(true);
        var putCase2 = putCase(dto);
        assertResponseCode(putCase2, 204);
        assertMatchesDto(dto);
    }

    @DisplayName("Scenario: Update a case when case is closed (not updating state)")
    @Test
    void updateCaseClosedBadRequest() throws JsonProcessingException {
        // create a closed case
        var dto = createCase();
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 201);
        assertMatchesDto(dto);

        // attempt update case
        dto.setTest(true);
        var putCase2 = doPutRequest(
            CASES_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putCase2, 400);
        assertThat(putCase2.body().jsonPath().getString("message"))
            .isEqualTo("Resource Case("
                           + dto.getId()
                           + ") is in state CLOSED. Cannot update case unless in state OPEN.");
    }

    @DisplayName("Scenario: Update case's state when case is closed")
    @Test
    void updateCaseClosedToOpenSuccess() throws JsonProcessingException {
        // create a closed case
        var dto = createCase();
        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(86400)));
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 201);
        assertMatchesDto(dto);

        // update case state
        dto.setState(CaseState.OPEN);
        dto.setClosedAt(null);
        var putCase2 = putCase(dto);
        assertResponseCode(putCase2, 204);
        assertMatchesDto(dto);
    }

    @Test
    @DisplayName("Scenario: Update case to pending closure with open bookings")
    void updateCaseToPendingClosureWithOpenBookings() throws JsonProcessingException {
        // create an open case
        var dto = createCase();
        dto.setState(CaseState.OPEN);
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 201);
        assertMatchesDto(dto);

        // update case to PENDING_CLOSURE when there is a booking without a capture session
        var booking = createBooking(dto.getId(), dto.getParticipants());
        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 201);
        assertMatchesDto(dto);

        dto.setState(CaseState.PENDING_CLOSURE);
        dto.setClosedAt(Timestamp.from(Instant.now()));
        var putCase2 = putCase(dto);
        assertResponseCode(putCase2, 400);

        var errorMessage = "Resource Case("
            + dto.getId()
            + ") has open bookings which must not be present when updating state to PENDING_CLOSURE";
        assertThat(putCase2.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);

        // update case to PENDING_CLOSURE when there is a booking with a capture session in state STANDBY
        var captureSession = createCaptureSession(booking.getId());
        captureSession.setStatus(RecordingStatus.STANDBY);
        var putCaptureSession1 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession1, 201);

        var putCase3 = putCase(dto);
        assertResponseCode(putCase3, 400);
        assertThat(putCase2.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);

        // update case to PENDING_CLOSURE when there is a booking with a capture session in state INITIALISING
        captureSession.setStatus(RecordingStatus.INITIALISING);
        var putCaptureSession2 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession2, 204);

        var putCase4 = putCase(dto);
        assertResponseCode(putCase4, 400);
        assertThat(putCase4.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);
        // update case to PENDING_CLOSURE when there is a booking with a capture session in state RECORDING
        captureSession.setStatus(RecordingStatus.RECORDING);
        var putCaptureSession3 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession3, 204);

        var putCase5 = putCase(dto);
        assertResponseCode(putCase5, 400);
        assertThat(putCase5.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);
        // update case to PENDING_CLOSURE when there is a booking with a capture session in state PROCESSING
        captureSession.setStatus(RecordingStatus.PROCESSING);
        var putCaptureSession4 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession4, 204);

        var putCase6 = putCase(dto);
        assertResponseCode(putCase6, 400);
        assertThat(putCase6.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Scenario: Update case to closed with open bookings")
    void updateCaseToClosedWithOpenBookings() throws JsonProcessingException {
        // create an open case
        var dto = createCase();
        dto.setState(CaseState.OPEN);
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 201);
        assertMatchesDto(dto);

        // update case to CLOSED when there is a booking without a capture session
        var booking = createBooking(dto.getId(), dto.getParticipants());
        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 201);
        assertMatchesDto(dto);

        dto.setState(CaseState.CLOSED);
        dto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(86400)));
        var putCase2 = putCase(dto);
        assertResponseCode(putCase2, 400);

        var errorMessage = "Resource Case("
            + dto.getId()
            + ") has open bookings which must not be present when updating state to CLOSED";
        assertThat(putCase2.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);

        // update case to PENDING_CLOSURE when there is a booking with a capture session in state STANDBY
        var captureSession = createCaptureSession(booking.getId());
        captureSession.setStatus(RecordingStatus.STANDBY);
        var putCaptureSession1 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession1, 201);

        var putCase3 = putCase(dto);
        assertResponseCode(putCase3, 400);
        assertThat(putCase2.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);

        // update case to PENDING_CLOSURE when there is a booking with a capture session in state INITIALISING
        captureSession.setStatus(RecordingStatus.INITIALISING);
        var putCaptureSession2 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession2, 204);

        var putCase4 = putCase(dto);
        assertResponseCode(putCase4, 400);
        assertThat(putCase4.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);
        // update case to PENDING_CLOSURE when there is a booking with a capture session in state RECORDING
        captureSession.setStatus(RecordingStatus.RECORDING);
        var putCaptureSession3 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession3, 204);

        var putCase5 = putCase(dto);
        assertResponseCode(putCase5, 400);
        assertThat(putCase5.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);
        // update case to PENDING_CLOSURE when there is a booking with a capture session in state PROCESSING
        captureSession.setStatus(RecordingStatus.PROCESSING);
        var putCaptureSession4 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession4, 204);

        var putCase6 = putCase(dto);
        assertResponseCode(putCase6, 400);
        assertThat(putCase6.body().jsonPath().getString("message"))
            .isEqualTo(errorMessage);
    }

    @Test
    @DisplayName("Scenario: Update case to pending closure without open bookings")
    void updateCaseToPendingClosureWithoutOpenBookings() throws JsonProcessingException {
        // create an open case
        var dto = createCase();
        dto.setState(CaseState.OPEN);
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 201);
        assertMatchesDto(dto);

        // update case to PENDING_CLOSURE when there are no associated bookings
        dto.setState(CaseState.PENDING_CLOSURE);
        dto.setClosedAt(Timestamp.from(Instant.now()));
        var putCase2 = putCase(dto);
        assertResponseCode(putCase2, 204);
        assertMatchesDto(dto);

        // reset
        dto.setState(CaseState.OPEN);
        dto.setClosedAt(null);
        var putCase3 = putCase(dto);
        assertResponseCode(putCase3, 204);
        assertMatchesDto(dto);

        // update case to PENDING_CLOSURE when there is a booking with a capture session in state FAILURE
        var booking = createBooking(dto.getId(), dto.getParticipants());
        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 201);
        assertMatchesDto(dto);

        var captureSession = createCaptureSession(booking.getId());
        captureSession.setStatus(RecordingStatus.FAILURE);
        var putCaptureSession1 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession1, 201);

        dto.setState(CaseState.PENDING_CLOSURE);
        dto.setClosedAt(Timestamp.from(Instant.now()));
        var putCase4 = putCase(dto);
        assertResponseCode(putCase4, 204);
        assertMatchesDto(dto);

        // reset
        dto.setState(CaseState.OPEN);
        dto.setClosedAt(null);
        var putCase5 = putCase(dto);
        assertResponseCode(putCase5, 204);
        assertMatchesDto(dto);

        // update case to PENDING_CLOSURE when there is a booking with a capture session in state NO_RECORDING
        captureSession.setStatus(RecordingStatus.NO_RECORDING);
        var putCaptureSession2 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession2, 204);

        dto.setState(CaseState.PENDING_CLOSURE);
        dto.setClosedAt(Timestamp.from(Instant.now()));
        var putCase6 = putCase(dto);
        assertResponseCode(putCase6, 204);
        assertMatchesDto(dto);

        // reset
        dto.setState(CaseState.OPEN);
        dto.setClosedAt(null);
        var putCase7 = putCase(dto);
        assertResponseCode(putCase7, 204);
        assertMatchesDto(dto);

        // update case to PENDING_CLOSURE when there is a booking with a capture session in state RECORDING_AVAILABLE
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        var putCaptureSession3 = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession3, 204);

        dto.setState(CaseState.PENDING_CLOSURE);
        dto.setClosedAt(Timestamp.from(Instant.now()));
        var putCase8 = putCase(dto);
        assertResponseCode(putCase8, 204);
        assertMatchesDto(dto);
    }

    @DisplayName("Scenario: Create a case with a non-existing court")
    @Test
    void shouldNotCreateAndUpdateCaseWithNonExistingCourt() throws JsonProcessingException {
        var dto = createCase();
        dto.setCourtId(UUID.randomUUID());
        var putCase1 = putCase(dto);
        assertResponseCode(putCase1, 404);
        assertThat(putCase1.body().jsonPath().getString("message")).isEqualTo("Not found: Court: " + dto.getCourtId());
    }

    @DisplayName("Should create a case with participants")
    @Test
    void shouldCreateACaseWithParticipants() throws JsonProcessingException {
        var participant1 = new CreateParticipantDTO();
        participant1.setId(UUID.randomUUID());
        participant1.setFirstName("John");
        participant1.setLastName("Smith");
        participant1.setParticipantType(ParticipantType.DEFENDANT);
        var participant2 = new CreateParticipantDTO();
        participant2.setId(UUID.randomUUID());
        participant2.setFirstName("John");
        participant2.setLastName("Smith");
        participant2.setParticipantType(ParticipantType.WITNESS);

        var createCase = createCase();
        createCase.setParticipants(Set.of(
            participant1,
            participant2
        ));

        var putResponse = putCase(createCase);
        assertResponseCode(putResponse, 201);

        var getResponse = assertCaseExists(createCase.getId(), true);
        var caseResponse = OBJECT_MAPPER.readValue(getResponse.body().asString(), CaseDTO.class);
        assertThat(caseResponse.getParticipants().size()).isEqualTo(2);
    }

    @DisplayName("Unauthorised use of endpoints should return 401")
    @Test
    void unauthorisedRequestsReturn401() throws JsonProcessingException {
        var getCaseByIdResponse = doGetRequest(CASES_ENDPOINT + "/" + UUID.randomUUID(), null);
        assertResponseCode(getCaseByIdResponse, 401);

        var getCasesResponse = doGetRequest(CASES_ENDPOINT, null);
        assertResponseCode(getCasesResponse, 401);

        var putCaseResponse = doPutRequest(
            CASES_ENDPOINT + "/" + UUID.randomUUID(),
            OBJECT_MAPPER.writeValueAsString(new CreateBookingDTO()),
            null
        );
        assertResponseCode(putCaseResponse, 401);

        var deleteCaseResponse = doDeleteRequest(CASES_ENDPOINT + "/" + UUID.randomUUID(), null);
        assertResponseCode(deleteCaseResponse, 401);
    }

    @DisplayName("Scenario: Delete case")
    @Test
    void shouldDeleteCaseWithExistingId() throws JsonProcessingException {
        var caseDTO = createCase();

        var putCase = putCase(caseDTO);

        assertResponseCode(putCase, 201);
        assertCaseExists(caseDTO.getId(), true);

        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertCaseExists(caseDTO.getId(), false);
    }

    @DisplayName("Should fail to delete a case when it is already deleted")
    @Test
    void shouldDeleteCaseWithExistingIdFail() throws JsonProcessingException {
        var caseDTO = createCase();
        var putCase = putCase(caseDTO);
        assertResponseCode(putCase, 201);

        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);

        var deleteResponse2 = doDeleteRequest(CASES_ENDPOINT + "/" + caseDTO.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse2, 404);
    }

    @DisplayName("Should fail to delete a case that doesn't exist")
    @Test
    void shouldDeleteCaseWithNonExistingIdFail() {
        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + UUID.randomUUID(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 404);
    }

    @DisplayName("Scenario: Remove Case Reference")
    @Test
    void shouldFailToRemoveCaseReference() throws JsonProcessingException {
        var caseDTO = createCase();

        caseDTO.setReference(null);
        var putResponse1 = putCase(caseDTO);
        assertResponseCode(putResponse1, 400);
        assertThat(putResponse1.getBody().jsonPath().getString("reference")).isEqualTo("must not be null");

        caseDTO.setReference("");
        var putResponse2 = putCase(caseDTO);
        assertResponseCode(putResponse2, 400);
        assertThat(putResponse2.getBody().jsonPath().getString("reference")).isEqualTo("size must be between 9 and 13");
    }

    @DisplayName("Scenario: Cannot create a case with reference of more than 13 characters")
    @Test
    void shouldFailTopUpdateCaseWithLongReference() throws JsonProcessingException {
        var caseDTO = createCase();

        caseDTO.setReference("FOURTEEN_CHARS");
        var putResponse1 = putCase(caseDTO);
        assertResponseCode(putResponse1, 400);
        assertThat(putResponse1.getBody().jsonPath().getString("reference")).isEqualTo("size must be between 9 and 13");
    }

    @DisplayName("Scenario: Cannot create a case with reference of less that 9 characters")
    @Test
    void shouldFailTopUpdateCaseWithShortReference() throws JsonProcessingException {
        var caseDTO = createCase();
        caseDTO.setReference("12345678");

        var putResponse1 = putCase(caseDTO);
        assertResponseCode(putResponse1, 400);
        assertThat(putResponse1.getBody().jsonPath().getString("reference")).isEqualTo("size must be between 9 and 13");
    }

    @DisplayName("Scenario: Create a case with a duplicate case reference in the same court")
    @Test
    void shouldFailCreateCaseWithDuplicateReferenceInSameCourt() throws JsonProcessingException {
        var caseDTO1 = createCase();
        var putResponse1 = putCase(caseDTO1);
        assertResponseCode(putResponse1, 201);

        var caseDTO2 = new CreateCaseDTO();
        caseDTO2.setId(UUID.randomUUID());
        caseDTO2.setReference(caseDTO1.getReference());
        caseDTO2.setCourtId(caseDTO1.getCourtId());
        caseDTO2.setParticipants(Set.of());
        caseDTO2.setTest(false);
        caseDTO2.setParticipants(Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        ));

        var putResponse2 = putCase(caseDTO2);
        assertResponseCode(putResponse2, 409);
        assertThat(putResponse2.getBody().jsonPath().getString("message"))
            .isEqualTo("Conflict: Case reference is already in use for this court");
    }

    @DisplayName("Scenario: Create a case with a duplicate case reference in different court")
    @Test
    void shouldCreateCaseWithDuplicateReferenceInDifferentCourt() throws JsonProcessingException {
        var caseDTO1 = createCase();
        var putResponse1 = putCase(caseDTO1);
        assertResponseCode(putResponse1, 201);

        var caseDTO2 = createCase();
        caseDTO2.setReference(caseDTO1.getReference());

        var putResponse2 = putCase(caseDTO2);
        assertResponseCode(putResponse2, 201);
    }

    @DisplayName("Scenario: Restore case")
    @Test
    void shouldUndeleteCase() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + dto.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertCaseExists(dto.getId(), false);

        var undeleteResponse =
            doPostRequest(CASES_ENDPOINT + "/" + dto.getId() + "/undelete", TestingSupportRoles.SUPER_USER);
        assertResponseCode(undeleteResponse, 200);
        assertCaseExists(dto.getId(), true);
    }

    @DisplayName("Scenario: Get Case by case id")
    @Test
    void shouldGetCaseByCaseId() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        var getCase = assertCaseExists(dto.getId(), true);
        assertThat(getCase.body().jsonPath().getUUID("id")).isEqualTo(dto.getId());
    }

    @DisplayName("Scenario: Get non-existing case by case id ")
    @Test
    void shouldGetNonExistingCaseByCaseId() {
        var id = UUID.randomUUID();
        assertCaseExists(id, false);
    }

    @DisplayName("Scenario: Search Cases by case reference")
    @Test
    void shouldSearchCaseByCaseReference() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        // match
        var getCases1 =
            doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(getCases1, 200);
        assertThat(getCases1.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases1.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
        assertThat(getCases1.body().jsonPath().getString("_embedded.caseDTOList[0].reference"))
            .isEqualTo(dto.getReference());

        // match lowercase
        var getCases2 = doGetRequest(
            CASES_ENDPOINT + "?reference=" + dto.getReference().toLowerCase(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getCases2, 200);
        assertThat(getCases2.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases2.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());

        // match uppercase
        var getCases3 = doGetRequest(
            CASES_ENDPOINT + "?reference=" + dto.getReference().toUpperCase(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getCases3, 200);
        assertThat(getCases3.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases3.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());

        // match partial
        var getCases4 = doGetRequest(
            CASES_ENDPOINT + "?reference=" + dto.getReference().substring(1, 12),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getCases4, 200);
        assertThat(getCases4.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases4.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
    }

    @DisplayName("Scenario: Search Cases by court id")
    @Test
    void shouldSearchCaseByCourtId() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        var getCases1 = doGetRequest(CASES_ENDPOINT + "?courtId=" + dto.getCourtId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(getCases1, 200);
        assertThat(getCases1.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases1.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
        assertThat(getCases1.body().jsonPath().getUUID("_embedded.caseDTOList[0].court.id"))
            .isEqualTo(dto.getCourtId());
    }

    @DisplayName("Scenario: Search by Cases and include deleted case")
    @Test
    void shouldSearchCaseAndIncludeDeletedCase() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertCaseExists(dto.getId(), true);

        // delete the case
        var deleteCase = doDeleteRequest(CASES_ENDPOINT + "/" + dto.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteCase, 200);
        assertCaseExists(dto.getId(), false);

        // search without including deleted
        var getCases1 =
            doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(getCases1, 200);
        assertThat(getCases1.body().jsonPath().getList("_embedded.caseDTOList")).isNullOrEmpty();

        // search including deleted
        var getCases2 = doGetRequest(
            CASES_ENDPOINT + "?reference=" + dto.getReference() + "&includeDeleted=true",
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(getCases2, 200);
        assertThat(getCases2.body().jsonPath().getList("_embedded.caseDTOList").size()).isEqualTo(1);
        assertThat(getCases2.body().jsonPath().getUUID("_embedded.caseDTOList[0].id")).isEqualTo(dto.getId());
        assertThat(getCases2.body().jsonPath().getString("_embedded.caseDTOList[0].reference"))
            .isEqualTo(dto.getReference());
    }

    @DisplayName("Scenario: Close case and delete shares")
    @Test
    void shouldCloseCaseAndDeleteShares() throws JsonProcessingException {
        // setup
        var dto = createCase();
        var putCase = putCase(dto);
        assertResponseCode(putCase, 201);
        assertCaseExists(dto.getId(), true);

        var booking = createBooking(dto.getId(), dto.getParticipants());
        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 201);
        assertBookingExists(booking.getId(), true);

        var captureSession = createCaptureSession(booking.getId());
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);

        var user1 = createUser("aaa");
        var putUser1 = putUser(user1);
        assertResponseCode(putUser1, 201);

        var share1 = createShareBooking(booking.getId(), user1.getId());
        var putShare1 = putShareBooking(share1);
        assertResponseCode(putShare1, 201);

        var b1 = assertBookingExists(booking.getId(), true).body().jsonPath().getObject("", BookingDTO.class);
        assertThat(b1.getShares()).isNotEmpty();
        assertThat(b1.getShares()).hasSize(1);
        assertThat(b1.getShares().getFirst().getId()).isEqualTo(share1.getId());
        assertThat(b1.getShares().getFirst().getDeletedAt()).isNull();

        // close case
        dto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(86400)));
        dto.setState(CaseState.CLOSED);
        var putCase2 = putCase(dto);
        assertResponseCode(putCase2, 204);
        assertCaseExists(dto.getId(), true);

        // check share
        var b2 = assertBookingExists(booking.getId(), true).body().jsonPath().getObject("", BookingDTO.class);
        assertThat(b2.getShares()).isNotEmpty();
        assertThat(b2.getShares().getFirst().getId()).isEqualTo(share1.getId());
        assertThat(b2.getShares().getFirst().getDeletedAt()).isNotNull();
    }

    @DisplayName("Scenario: Update case status as SUPER USER, LEVEL 1 and LEVEL 2")
    @Test
    void updateCaseStatusSuccess() throws JsonProcessingException {
        var roles = new TestingSupportRoles[] {
            TestingSupportRoles.SUPER_USER,
            TestingSupportRoles.LEVEL_1,
            TestingSupportRoles.LEVEL_2
        };

        for (var role : roles) {
            var dto = createCase();
            dto.setCourtId(authenticatedUserIds.get(role).courtId());
            var putResponse = putCase(dto, role);
            assertResponseCode(putResponse, 201);
            assertCaseExists(dto.getId(), true);
            assertMatchesDto(dto);

            // update OPEN -> PENDING_CLOSURE
            dto.setState(CaseState.PENDING_CLOSURE);
            dto.setClosedAt(Timestamp.from(Instant.now()));
            var putResponse2 = putCase(dto, role);
            assertResponseCode(putResponse2, 204);
            assertCaseExists(dto.getId(), true);
            assertMatchesDto(dto);

            // update PENDING_CLOSURE -> CLOSED
            dto.setState(CaseState.CLOSED);
            dto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
            var putResponse3 = putCase(dto, role);
            assertResponseCode(putResponse3, 204);
            assertCaseExists(dto.getId(), true);
            assertMatchesDto(dto);

            // update CLOSED -> OPEN
            dto.setState(CaseState.OPEN);
            dto.setClosedAt(null);
            var putResponse4 = putCase(dto, role);
            assertResponseCode(putResponse4, 204);
            assertCaseExists(dto.getId(), true);
            assertMatchesDto(dto);

            // update PENDING_CLOSURE -> OPEN
            dto.setState(CaseState.PENDING_CLOSURE);
            dto.setClosedAt(Timestamp.from(Instant.now()));
            var putResponse5 = putCase(dto, role);
            assertResponseCode(putResponse5, 204);
            assertCaseExists(dto.getId(), true);
            assertMatchesDto(dto);
            dto.setState(CaseState.OPEN);
            dto.setClosedAt(null);
            var putResponse6 = putCase(dto, role);
            assertResponseCode(putResponse6, 204);
            assertCaseExists(dto.getId(), true);
            assertMatchesDto(dto);
        }
    }

    @DisplayName("Scenario: Update case status as LEVEL 3 and LEVEL 4")
    @Test
    void updateCaseStatusAuthError() throws JsonProcessingException {
        var roles = new TestingSupportRoles[] {
            TestingSupportRoles.LEVEL_3,
            TestingSupportRoles.LEVEL_4
        };

        for (var role : roles) {
            var dto = createCase();
            dto.setCourtId(authenticatedUserIds.get(role).courtId());
            var putResponse = putCase(dto);
            assertResponseCode(putResponse, 201);
            assertCaseExists(dto.getId(), true);
            assertMatchesDto(dto);

            // Outstanding issue with this whole test method - fails for reason not related to the case status
            // update OPEN -> NULL
            // var putResponse1 = putCase(dto, role);
            // assertResponseCode(putResponse1, 204);

            // update OPEN -> PENDING_CLOSURE
            dto.setState(CaseState.PENDING_CLOSURE);
            dto.setClosedAt(Timestamp.from(Instant.now()));
            var putResponse2 = putCase(dto, role);
            assertResponseCode(putResponse2, 403);

            // force the update the PENDING_CLOSURE
            var forcedPut = putCase(dto);
            assertResponseCode(forcedPut, 204);

            // update PENDING_CLOSURE -> OPEN
            dto.setState(CaseState.OPEN);
            dto.setClosedAt(null);
            var putResponse3 = putCase(dto, role);
            assertResponseCode(putResponse3, 403);

            // update PENDING_CLOSURE -> CLOSED
            dto.setState(CaseState.CLOSED);
            dto.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
            var putResponse4 = putCase(dto, role);
            assertResponseCode(putResponse4, 403);

            // force the update the CLOSED
            var forcedPut2 = putCase(dto);
            assertResponseCode(forcedPut2, 204);

            // Outstanding issue with this whole test method - fails for reason not related to the case status
            // update OPEN -> NULL
            // dto.setState(null);
            // var putResponse5 = putCase(dto, role);
            // assertResponseCode(putResponse5, 403);

            // update CLOSED -> OPEN
            dto.setState(CaseState.OPEN);
            dto.setClosedAt(null);
            var putResponse6 = putCase(dto, role);
            assertResponseCode(putResponse6, 403);
        }
    }

    @Test
    @DisplayName("Create a case without participants")
    void createCaseWithoutParticipants() throws JsonProcessingException {
        var dto = createCase();
        dto.setParticipants(Set.of());
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 400);

        assertThat(putResponse.jsonPath().getString("participants"))
            .isEqualTo("Participants must consist of at least 1 defendant and 1 witness");
    }

    @Test
    @DisplayName("Update a case without participants")
    void updateCaseWithoutParticipants() throws JsonProcessingException {
        var dto = createCase();
        var putResponse = putCase(dto);
        assertResponseCode(putResponse, 201);
        assertMatchesDto(dto);

        dto.setParticipants(Set.of());
        var putResponse2 = putCase(dto);

        assertThat(putResponse2.jsonPath().getString("participants"))
            .isEqualTo("Participants must consist of at least 1 defendant and 1 witness");
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=false")
    class WithMigratedDataDisabled extends FunctionalTestBase {
        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not allow access to VODAFONE cases to non super user requests")
        void getById(TestingSupportRoles role) throws JsonProcessingException {
            var dto = createCase();
            dto.setOrigin(RecordingOrigin.VODAFONE);
            dto.setCourtId(authenticatedUserIds.get(TestingSupportRoles.SUPER_USER).courtId());
            var putCase = putCase(dto);
            assertResponseCode(putCase, 201);
            // ensures that super users can access
            assertCaseExists(dto.getId(), true);

            var request = doGetRequest(CASES_ENDPOINT + "/" + dto.getId(), role);
            assertResponseCode(request, 403);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class,
            names = {"SUPER_USER", "LEVEL_3"},
            mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not allow access to VODAFONE cases to non super user requests")
        void findAllCasesHideVodafoneCasesForNonSuperUser(TestingSupportRoles role) throws JsonProcessingException {
            var dto = createCase();
            dto.setOrigin(RecordingOrigin.VODAFONE);
            dto.setCourtId(authenticatedUserIds.get(role).courtId());
            var putCase = putCase(dto);
            assertResponseCode(putCase, 201);

            var getCases = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(), role);
            assertResponseCode(getCases, 200);
            assertThat(getCases.body().jsonPath().getInt("page.totalElements")).isEqualTo(0);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class,
            names = {"SUPER_USER", "LEVEL_3"},
            mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should allow access to VODAFONE_VISIBLE cases to non super user requests")
        void findAllCasesShowVodafoneVisibleCasesForNonSuperUser(TestingSupportRoles role)
            throws JsonProcessingException {
            CreateCaseDTO dto = createCase();
            dto.setOrigin(RecordingOrigin.VODAFONE_VISIBLE);
            dto.setCourtId(authenticatedUserIds.get(role).courtId());
            Response putCase = putCase(dto);
            assertResponseCode(putCase, 201);

            Response getCases = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(), role);
            assertResponseCode(getCases, 200);
            assertThat(getCases.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);
        }

        @Test
        @DisplayName("Should not hide VODAFONE cases for super users")
        void findAllCasesNotHideVodafoneCasesForSuperUser() throws JsonProcessingException {
            var dto = createCase();
            dto.setOrigin(RecordingOrigin.VODAFONE);
            dto.setCourtId(authenticatedUserIds.get(TestingSupportRoles.SUPER_USER).courtId());
            var putCase = putCase(dto);
            assertResponseCode(putCase, 201);
            assertCaseExists(dto.getId(), true);

            var getCases = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(),
                                        TestingSupportRoles.SUPER_USER);
            assertResponseCode(getCases, 200);
            assertThat(getCases.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);
        }
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=true")
    class WithMigratedDataEnabled extends FunctionalTestBase {
        @Test
        @DisplayName("Should allow access to VODAFONE cases when feature flag enabled")
        void getById() throws JsonProcessingException {
            var dto = createCase();
            dto.setOrigin(RecordingOrigin.VODAFONE);
            dto.setCourtId(authenticatedUserIds.get(TestingSupportRoles.SUPER_USER).courtId());
            var putCase = putCase(dto);
            assertResponseCode(putCase, 201);
            assertCaseExists(dto.getId(), true);

            // SUPER USER
            var request = doGetRequest(CASES_ENDPOINT + "/" + dto.getId(), TestingSupportRoles.SUPER_USER);
            assertResponseCode(request, 200);

            // LEVEL 1
            var request2 = doGetRequest(CASES_ENDPOINT + "/" + dto.getId(), TestingSupportRoles.LEVEL_1);
            assertResponseCode(request2, 200);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "LEVEL_3", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should allow access to VODAFONE cases when toggled on")
        void findAllCasesNotHideVodafoneCases(TestingSupportRoles role) throws JsonProcessingException {
            var dto = createCase();
            dto.setOrigin(RecordingOrigin.VODAFONE);
            dto.setCourtId(authenticatedUserIds.get(role).courtId());
            var putCase = putCase(dto);
            assertResponseCode(putCase, 201);
            assertCaseExists(dto.getId(), true);

            var getCases = doGetRequest(CASES_ENDPOINT + "?reference=" + dto.getReference(), role);
            assertResponseCode(getCases, 200);
            assertThat(getCases.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);
        }
    }

    private Response putCase(CreateCaseDTO dto, TestingSupportRoles role) throws JsonProcessingException {
        return doPutRequest(
            CASES_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            role
        );
    }

    private void assertMatchesDto(CreateCaseDTO dto) {
        var getCase = assertCaseExists(dto.getId(), true);
        var res = getCase.body().as(CaseDTO.class);
        assertThat(res).isNotNull();
        assertThat(res.getCourt().getId()).isEqualTo(dto.getCourtId());
        assertThat(res.getReference()).isEqualTo(dto.getReference());
        assertThat(res.getParticipants()).hasSize(dto.getParticipants().size());
        assertThat(res.isTest()).isEqualTo(dto.isTest());
        assertThat(res.getState()).isEqualTo(dto.getState());
        assertThat(res.getCreatedAt()).isNotNull();
        assertThat(res.getModifiedAt()).isNotNull();
        assertThat(res.getDeletedAt()).isNull();
    }
}
