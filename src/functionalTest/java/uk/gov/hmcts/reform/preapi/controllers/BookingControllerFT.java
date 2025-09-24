package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.test.context.TestPropertySource;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.dto.ShareBookingDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BookingControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Delete booking with recordings")
    @Test
    void shouldNotDeleteRecordingsForBooking() {

        var testIds = doPostRequest("/testing-support/should-delete-recordings-for-booking", null).body().jsonPath();

        var bookingId = testIds.getUUID("bookingId");
        var recordingId = testIds.getUUID("recordingId");

        assertBookingExists(bookingId, true);
        var recordingResponse = assertRecordingExists(recordingId, true);
        assertThat(recordingResponse.body().jsonPath().getString("created_at")).isNotBlank();

        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + bookingId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 400);
        assertThat(deleteResponse.getBody().jsonPath().getString("message"))
            .isEqualTo("Cannot delete because an associated recording has not been deleted.");
        assertRecordingExists(recordingId, true);
    }

    @DisplayName("Scenario: Delete booking")
    @Test
    void shouldDeleteRecordingsForBooking() {
        var testIds = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath();
        var bookingId = testIds.getUUID("bookingId");
        var courtId = testIds.getUUID("courtId");

        // see it is available before deletion
        assertBookingExists(bookingId, true);

        var searchResponse1 = doGetRequest(BOOKINGS_ENDPOINT + "?courtId=" + courtId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(searchResponse1, 200);
        var responseData1 = searchResponse1.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData1.size()).isEqualTo(1);
        assertThat(responseData1.getFirst().getId()).isEqualTo(bookingId);

        // delete booking
        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + bookingId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 204);

        // see it is no longer available after deletion
        assertBookingExists(bookingId, false);

        var searchResponse2 = doGetRequest(BOOKINGS_ENDPOINT + "?courtId=" + courtId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(searchResponse2, 200);
        assertThat(searchResponse2.jsonPath().getInt("page.totalElements"))
            .isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario: The schedule date should not be amended to the past date")
    void recordingScheduleDateShouldNotBeAmendedToThePast() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "/" + bookingId, TestingSupportRoles.SUPER_USER);
        assertResponseCode(bookingResponse, 200);

        BookingDTO booking = bookingResponse.body().as(BookingDTO.class);

        // assert the booking is well-formed
        assertThat(booking.getId()).isEqualTo(bookingId);
        var region = booking.getCaseDTO().getCourt().getRegions().stream().findFirst();
        assertThat(region.orElseGet(RegionDTO::new).getName()).isEqualTo("Foo Region");

        // validate the court referenced does exist
        var courtResponse = assertCourtExists(booking.getCaseDTO().getCourt().getId(), true);
        assertThat(courtResponse.body().jsonPath().getString("name"))
            .isEqualTo(booking.getCaseDTO().getCourt().getName());

        var createBooking = new CreateBookingDTO();
        createBooking.setId(booking.getId());
        createBooking.setParticipants(booking.getParticipants().stream().map(p -> {
            var createParticipant = new CreateParticipantDTO();
            createParticipant.setId(p.getId());
            createParticipant.setFirstName(p.getFirstName());
            createParticipant.setLastName(p.getLastName());
            createParticipant.setParticipantType(p.getParticipantType());
            return createParticipant;
        }).collect(Collectors.toSet()));
        createBooking.setCaseId(booking.getCaseDTO().getId());
        // set scheduledFor to yesterday
        createBooking.setScheduledFor(Timestamp.from(OffsetDateTime.now().minusDays(1).toInstant()));

         putBooking(createBooking);
        var putResponse = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + createBooking.getId(),
            OBJECT_MAPPER.writeValueAsString(createBooking),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putResponse, 400);
    }

    @Test
    @DisplayName("Scenario: Search By Capture Session Status")
    void searchByCaptureSessionStatus() throws JsonProcessingException {
        // setup
        var aCase = createCase();
        aCase.setTest(true);
        var putCase = putCase(aCase);
        assertResponseCode(putCase, 201);
        assertCaseExists(aCase.getId(), true);

        var booking1 = createBooking(aCase.getId(), aCase.getParticipants());
        var putBooking1 = putBooking(booking1);
        assertResponseCode(putBooking1, 201);
        assertBookingExists(booking1.getId(), true);

        var booking2 = createBooking(aCase.getId(), aCase.getParticipants());
        var putBooking2 = putBooking(booking2);
        assertResponseCode(putBooking2, 201);
        assertBookingExists(booking2.getId(), true);

        var captureSession1 = createCaptureSession(booking1.getId());
        captureSession1.setStatus(RecordingStatus.STANDBY);
        var putCaptureSession1 = putCaptureSession(captureSession1);
        assertResponseCode(putCaptureSession1, 201);
        assertCaptureSessionExists(captureSession1.getId(), true);

        var captureSession2 = createCaptureSession(booking2.getId());
        captureSession2.setStatus(RecordingStatus.PROCESSING);
        var putCaptureSession2 = putCaptureSession(captureSession2);
        assertResponseCode(putCaptureSession2, 201);
        assertCaptureSessionExists(captureSession2.getId(), true);

        // search by standby
        var getBookings1 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusIn=STANDBY",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings1, 200);
        var responseData1 = getBookings1.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData1.size()).isEqualTo(1);
        assertThat(responseData1.getFirst().getId()).isEqualTo(booking1.getId());

        // search by processing
        var getBookings2 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusIn=PROCESSING",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings2, 200);
        var responseData2 = getBookings2.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData2.size()).isEqualTo(1);
        assertThat(responseData2.getFirst().getId()).isEqualTo(booking2.getId());

        // search by failure
        var getBookings3 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusIn=FAILURE",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings3, 200);
        var responseData3 = getBookings3.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData3.size()).isEqualTo(0);

        // search by standby OR processing
        var getBookings4 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusIn=STANDBY,PROCESSING",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings4, 200);
        var responseData4 = getBookings4.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData4.size()).isEqualTo(2);
        assertThat(responseData4.stream().anyMatch(res -> res.getId().equals(booking1.getId()))).isTrue();
        assertThat(responseData4.stream().anyMatch(res -> res.getId().equals(booking2.getId()))).isTrue();
    }

    @Test
    @DisplayName("Scenario: Search By Not Capture Session Status")
    void searchByNotCaptureSessionStatus() throws JsonProcessingException {
        // setup
        var aCase = createCase();
        aCase.setTest(true);
        var putCase = putCase(aCase);
        assertResponseCode(putCase, 201);
        assertCaseExists(aCase.getId(), true);

        var booking1 = createBooking(aCase.getId(), aCase.getParticipants());
        var putBooking1 = putBooking(booking1);
        assertResponseCode(putBooking1, 201);
        assertBookingExists(booking1.getId(), true);

        var booking2 = createBooking(aCase.getId(), aCase.getParticipants());
        var putBooking2 = putBooking(booking2);
        assertResponseCode(putBooking2, 201);
        assertBookingExists(booking2.getId(), true);

        var captureSession1 = createCaptureSession(booking1.getId());
        captureSession1.setStatus(RecordingStatus.STANDBY);
        var putCaptureSession1 = putCaptureSession(captureSession1);
        assertResponseCode(putCaptureSession1, 201);
        assertCaptureSessionExists(captureSession1.getId(), true);

        var captureSession2 = createCaptureSession(booking2.getId());
        captureSession2.setStatus(RecordingStatus.PROCESSING);
        var putCaptureSession2 = putCaptureSession(captureSession2);
        assertResponseCode(putCaptureSession2, 201);
        assertCaptureSessionExists(captureSession2.getId(), true);

        // search by NOT standby
        var getBookings1 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusNotIn=STANDBY",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings1, 200);
        var responseData1 = getBookings1.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData1.size()).isEqualTo(1);
        assertThat(responseData1.getFirst().getId()).isEqualTo(booking2.getId());

        // search by NOT processing
        var getBookings2 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusNotIn=PROCESSING",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings2, 200);
        var responseData2 = getBookings2.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData2.size()).isEqualTo(1);
        assertThat(responseData2.getFirst().getId()).isEqualTo(booking1.getId());

        // search by NOT failure
        var getBookings3 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusNotIn=FAILURE",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings3, 200);
        var responseData3 = getBookings3.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData3.size()).isEqualTo(2);
        assertThat(responseData3.stream().anyMatch(res -> res.getId().equals(booking1.getId()))).isTrue();
        assertThat(responseData3.stream().anyMatch(res -> res.getId().equals(booking2.getId()))).isTrue();


        // search by NOT standby OR processing
        var getBookings4 = doGetRequest(BOOKINGS_ENDPOINT
                                            + "?caseId="
                                            + booking1.getCaseId()
                                            + "&captureSessionStatusNotIn=STANDBY,PROCESSING",
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings4, 200);
        var responseData4 = getBookings4.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData4.size()).isEqualTo(0);
    }

    @Test
    @DisplayName("Deleting a non-existent booking should return 404")
    void deletingNonExistentBookingShouldReturn404() {
        var deleteResponse = doDeleteRequest(
            BOOKINGS_ENDPOINT + "/00000000-0000-0000-0000-000000000000",
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(deleteResponse, 404);
    }

    @DisplayName("Unauthorised use of endpoints should return 401")
    @Test
    void unauthorisedRequestsReturn401() throws JsonProcessingException {
        var getBookingsResponse = doGetRequest("/bookings", null);
        assertResponseCode(getBookingsResponse, 401);

        var getBookingsByIdResponse = doGetRequest(BOOKINGS_ENDPOINT + "/" + UUID.randomUUID(), null);
        assertResponseCode(getBookingsByIdResponse, 401);

        var putBookingResponse = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + UUID.randomUUID(),
            OBJECT_MAPPER.writeValueAsString(new CreateBookingDTO()),
            null
        );
        assertResponseCode(putBookingResponse, 401);

        var deleteBookingResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + UUID.randomUUID(), null);
        assertResponseCode(deleteBookingResponse, 401);

        var putShareBookingResponse = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + UUID.randomUUID() + "/share",
            OBJECT_MAPPER.writeValueAsString(new CreateShareBookingDTO()),
            null
        );
        assertResponseCode(putShareBookingResponse, 401);

        var deleteShareBookingResponse = doDeleteRequest(
            BOOKINGS_ENDPOINT + "/" + UUID.randomUUID() + "/share",
            null
        );
        assertResponseCode(deleteShareBookingResponse, 401);
    }

    @DisplayName("Should return list of shares for a booking sorted by shared with user's first name")
    @Test
    void listBookingSharesSuccess() throws JsonProcessingException {
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);
        var booking = createBooking(caseEntity.getId(), participants);

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putCase, 201);

        // create booking
        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 201);
        assertThat(putBooking.header(LOCATION_HEADER))
            .isEqualTo(testUrl + BOOKINGS_ENDPOINT + "/" + booking.getId());
        assertBookingExists(booking.getId(), true);

        // create users
        var user1 = createUser("AAA");
        var putUser1 = putUser(user1);
        assertResponseCode(putUser1, 201);
        assertUserExists(user1.getId(), true);
        var user2 = createUser("BBB");
        var putUser2 = putUser(user2);
        assertResponseCode(putUser2, 201);
        assertUserExists(user2.getId(), true);
        var user3 = createUser("CCC");
        var putUser3 = putUser(user3);
        assertResponseCode(putUser3, 201);
        assertUserExists(user3.getId(), true);

        // share with users
        var putShare2 = putShareBooking(createShareBooking(booking.getId(), user2.getId()));
        assertResponseCode(putShare2, 201);
        var putShare3 = putShareBooking(createShareBooking(booking.getId(), user3.getId()));
        assertResponseCode(putShare3, 201);
        var putShare1 = putShareBooking(createShareBooking(booking.getId(), user1.getId()));
        assertResponseCode(putShare1, 201);

        // see shares are sorted
        var getShares =
            doGetRequest(BOOKINGS_ENDPOINT + "/" + booking.getId() + "/share", TestingSupportRoles.SUPER_USER);
        assertResponseCode(getShares, 200);

        var shares = getShares.getBody().jsonPath().getList("_embedded.shareBookingDTOList", ShareBookingDTO.class);
        assertThat(shares.size()).isEqualTo(3);
        assertThat(shares.getFirst().getSharedWithUser().getId()).isEqualTo(user1.getId());
        assertThat(shares.getFirst().getSharedWithUser().getFirstName()).isEqualTo("AAA");

        assertThat(shares.get(1).getSharedWithUser().getId()).isEqualTo(user2.getId());
        assertThat(shares.get(1).getSharedWithUser().getFirstName()).isEqualTo("BBB");

        assertThat(shares.getLast().getSharedWithUser().getId()).isEqualTo(user3.getId());
        assertThat(shares.getLast().getSharedWithUser().getFirstName()).isEqualTo("CCC");

    }

    @DisplayName("Scenario: Create and update a booking")
    @Test
    void createBookingScenario() throws JsonProcessingException {
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);
        var booking = createBooking(caseEntity.getId(), participants);

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putCase, 201);

        // create booking
        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 201);
        assertThat(putBooking.header(LOCATION_HEADER))
            .isEqualTo(testUrl + BOOKINGS_ENDPOINT + "/" + booking.getId());
        var getResponse1 = assertBookingExists(booking.getId(), true);
        assertThat(getResponse1.body().jsonPath().getUUID("case_dto.id")).isEqualTo(caseEntity.getId());

        // update booking
        var caseEntity2 = createCase();
        caseEntity2.setParticipants(participants);
        var putCase2 = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity2.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity2),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putCase2, 201);
        booking.setCaseId(caseEntity2.getId());

        var updateBooking = putBooking(booking);
        assertResponseCode(updateBooking, 204);

        var getResponse2 = assertBookingExists(booking.getId(), true);
        assertThat(getResponse2.body().jsonPath().getUUID("case_dto.id")).isEqualTo(caseEntity2.getId());
    }

    @DisplayName("Scenario: Create a booking with scheduled for in the past")
    @Test
    void createBookingWithScheduledForThePast() throws JsonProcessingException {
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);
        var booking = createBooking(caseEntity.getId(), participants);
        booking.setScheduledFor(Timestamp.valueOf(LocalDateTime.now().minusWeeks(1)));

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putCase, 201);

        var putBooking = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + booking.getId(),
            OBJECT_MAPPER.writeValueAsString(booking),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putBooking, 400);
        assertThat(putBooking.body().jsonPath().getString("message"))
            .isEqualTo("Scheduled date must not be in the past");
    }

    @DisplayName("Create a booking with a participant that is not part of the case")
    @Test
    void createBookingWithParticipantNotInCase() throws JsonProcessingException {
        var caseEntity = createCase();
        var participant1 = createParticipant(ParticipantType.WITNESS);
        var participant2 = createParticipant(ParticipantType.DEFENDANT);
        var participants = Set.of(
            participant1,
            participant2
        );
        caseEntity.setParticipants(participants);

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putCase, 201);
        var participant3 = createParticipant(ParticipantType.DEFENDANT);
        var booking = createBooking(
            caseEntity.getId(),
            Set.of(participant1, participant2, participant3)
        );

        // create booking
        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 404);
        assertThat(putBooking.body().jsonPath().getString("message"))
            .isEqualTo("Not found: Participant: " + participant3.getId() + " in case: " + caseEntity.getId());
    }

    @DisplayName("Scenario: Restore booking")
    @Test
    void undeleteBooking() throws JsonProcessingException {
        // create booking
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(putCase, 201);

        var booking = createBooking(caseEntity.getId(), participants);
        var putResponse = putBooking(booking);
        assertResponseCode(putResponse, 201);
        assertBookingExists(booking.getId(), true);
        assertCaseExists(caseEntity.getId(), true);

        // delete case (and associated booking)
        var deleteResponse = doDeleteRequest(CASES_ENDPOINT + "/" + caseEntity.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertBookingExists(booking.getId(), false);
        assertCaseExists(caseEntity.getId(), false);

        // undelete booking
        var undeleteResponse =
            doPostRequest(BOOKINGS_ENDPOINT + "/" + booking.getId() + "/undelete", TestingSupportRoles.SUPER_USER);
        assertResponseCode(undeleteResponse, 200);
        assertBookingExists(booking.getId(), true);
        assertCaseExists(caseEntity.getId(), true);
    }

    @DisplayName("Scenario: Share a booking for a closed case")
    @Test
    void shareBookingForClosedCase() throws JsonProcessingException {
        // create booking
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);

        var putCase1 = putCase(caseEntity);
        assertResponseCode(putCase1, 201);

        var booking = createBooking(caseEntity.getId(), participants);
        var putResponse = putBooking(booking);
        assertResponseCode(putResponse, 201);
        assertBookingExists(booking.getId(), true);
        assertCaseExists(caseEntity.getId(), true);

        var captureSession = createCaptureSession(booking.getId());
        captureSession.setStatus(RecordingStatus.NO_RECORDING);
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);

        // create share target
        var user1 = createUser("AAA");
        var putUser1 = putUser(user1);
        assertResponseCode(putUser1, 201);
        assertUserExists(user1.getId(), true);

        // close case
        caseEntity.setState(CaseState.CLOSED);
        caseEntity.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        var putCase2 = putCase(caseEntity);
        assertResponseCode(putCase2, 204);

        // attempt share
        var share = createShareBooking(booking.getId(), user1.getId());
        var putShare = putShareBooking(share);
        assertResponseCode(putShare, 400);
        assertThat(putShare.body().jsonPath().getString("message"))
            .isEqualTo(
                "Resource Booking("
                    + booking.getId()
                    + ") is associated with a case in the state CLOSED. Must be in state OPEN or PENDING_CLOSURE.");
    }

    @DisplayName("Scenario: Create/update a booking for a closed case")
    @Test
    void upsertBookingForClosedCase() throws JsonProcessingException {
        // create booking
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);

        var putCase1 = putCase(caseEntity);
        assertResponseCode(putCase1, 201);

        var booking = createBooking(caseEntity.getId(), participants);
        var putResponse = putBooking(booking);
        assertResponseCode(putResponse, 201);
        assertBookingExists(booking.getId(), true);
        assertCaseExists(caseEntity.getId(), true);

        var captureSession = createCaptureSession(booking.getId());
        captureSession.setStatus(RecordingStatus.RECORDING_AVAILABLE);
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);

        // close case
        caseEntity.setState(CaseState.CLOSED);
        caseEntity.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        var putCase2 = putCase(caseEntity);
        assertResponseCode(putCase2, 204);

        // attempt update
        booking.setScheduledFor(Timestamp.valueOf(LocalDate.now().atStartOfDay().plusDays(1)));
        var putBooking2 = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + booking.getId(),
            OBJECT_MAPPER.writeValueAsString(booking),
            TestingSupportRoles.LEVEL_1
        );

        assertResponseCode(putBooking2, 400);
        assertThat(putBooking2.body().jsonPath().getString("message"))
            .isEqualTo(
                "Resource Booking("
                    + booking.getId()
                    + ") is associated with a case in the state CLOSED. Must be in state OPEN.");

        // attempt create
        var booking2 = createBooking(caseEntity.getId(), participants);
        var putBooking3 = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + booking2.getId(),
            OBJECT_MAPPER.writeValueAsString(booking2),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putBooking3, 400);
        assertThat(putBooking3.body().jsonPath().getString("message"))
            .isEqualTo(
                "Resource Booking("
                    + booking2.getId()
                    + ") is associated with a case in the state CLOSED. Must be in state OPEN.");
    }

    @DisplayName("Scenario: Create/update a booking for a case pending closure")
    @Test
    void upsertBookingForPendingClosureCase() throws JsonProcessingException {
        // create booking
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);

        var putCase1 = putCase(caseEntity);
        assertResponseCode(putCase1, 201);

        var booking = createBooking(caseEntity.getId(), participants);
        var putResponse = putBooking(booking);
        assertResponseCode(putResponse, 201);
        assertBookingExists(booking.getId(), true);
        assertCaseExists(caseEntity.getId(), true);

        var captureSession = createCaptureSession(booking.getId());
        captureSession.setStatus(RecordingStatus.NO_RECORDING);
        var putCaptureSession = putCaptureSession(captureSession);
        assertResponseCode(putCaptureSession, 201);

        // close case
        caseEntity.setState(CaseState.PENDING_CLOSURE);
        caseEntity.setClosedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        var putCase2 = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putCase2, 204);

        // attempt update
        booking.setScheduledFor(Timestamp.valueOf(LocalDate.now().atStartOfDay().plusDays(1)));
        var putBooking2 = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + booking.getId(),
            OBJECT_MAPPER.writeValueAsString(booking),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putBooking2, 400);
        assertThat(putBooking2.body().jsonPath().getString("message"))
            .isEqualTo(
                "Resource Booking("
                    + booking.getId()
                    + ") is associated with a case in the state PENDING_CLOSURE. Must be in state OPEN.");

        // attempt create
        var booking2 = createBooking(caseEntity.getId(), participants);
        var putBooking3 = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + booking2.getId(),
            OBJECT_MAPPER.writeValueAsString(booking2),
            TestingSupportRoles.LEVEL_1
        );
        assertResponseCode(putBooking3, 400);
        assertThat(putBooking3.body().jsonPath().getString("message"))
            .isEqualTo(
                "Resource Booking("
                    + booking2.getId()
                    + ") is associated with a case in the state PENDING_CLOSURE. Must be in state OPEN.");
    }

    @Test
    @DisplayName("Scenario: Get bookings ordered by capture session's finishedAt")
    void getBookingsSortByFinishedAt() throws JsonProcessingException {
        // create booking
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);

        var putCase1 = putCase(caseEntity);
        assertResponseCode(putCase1, 201);
        assertCaseExists(caseEntity.getId(), true);

        var booking1 = createBooking(caseEntity.getId(), participants);
        var putBooking1 = putBooking(booking1);
        assertResponseCode(putBooking1, 201);
        assertBookingExists(booking1.getId(), true);

        var captureSession1 = createCaptureSession(booking1.getId());
        captureSession1.setFinishedAt(Timestamp.from(Instant.now().minusSeconds(36000)));
        var putCaptureSession1 = putCaptureSession(captureSession1);
        assertResponseCode(putCaptureSession1, 201);
        assertCaptureSessionExists(captureSession1.getId(), true);

        var booking2 = createBooking(caseEntity.getId(), participants);
        var putBooking2 = putBooking(booking2);
        assertResponseCode(putBooking2, 201);
        assertBookingExists(booking2.getId(), true);

        var captureSession2 = createCaptureSession(booking2.getId());
        captureSession2.setFinishedAt(Timestamp.from(Instant.now()));
        var putCaptureSession2 = putCaptureSession(captureSession2);
        assertResponseCode(putCaptureSession2, 201);
        assertCaptureSessionExists(captureSession2.getId(), true);

        var booking3 = createBooking(caseEntity.getId(), participants);
        var putBooking3 = putBooking(booking3);
        assertResponseCode(putBooking3, 201);
        assertBookingExists(booking3.getId(), true);

        var captureSession3 = createCaptureSession(booking3.getId());
        captureSession3.setFinishedAt(Timestamp.from(Instant.now().plusSeconds(36000)));
        var putCaptureSession3 = putCaptureSession(captureSession3);
        assertResponseCode(putCaptureSession3, 201);
        assertCaptureSessionExists(captureSession3.getId(), true);

        var getBookings1 = doGetRequest(BOOKINGS_ENDPOINT + "?sort=cs.finishedAt,asc&caseId=" + caseEntity.getId(),
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings1, 200);
        var responseData1 = getBookings1.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData1.size()).isEqualTo(3);
        assertThat(responseData1.get(0).getId()).isEqualTo(booking1.getId());
        assertThat(responseData1.get(1).getId()).isEqualTo(booking2.getId());
        assertThat(responseData1.get(2).getId()).isEqualTo(booking3.getId());

        var getBookings2 = doGetRequest(BOOKINGS_ENDPOINT + "?sort=cs.finishedAt,desc&caseId=" + caseEntity.getId(),
                                        TestingSupportRoles.SUPER_USER);
        assertResponseCode(getBookings2, 200);
        var responseData2 = getBookings2.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData2.size()).isEqualTo(3);
        assertThat(responseData2.get(0).getId()).isEqualTo(booking3.getId());
        assertThat(responseData2.get(1).getId()).isEqualTo(booking2.getId());
        assertThat(responseData2.get(2).getId()).isEqualTo(booking1.getId());
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=false")
    class WithMigratedDataDisabled extends FunctionalTestBase {
        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not allow access to VODAFONE bookings to non super user requests")
        void getById(TestingSupportRoles role) throws JsonProcessingException {
            var caseDto = createCase();
            var participants = Set.of(
                createParticipant(ParticipantType.WITNESS),
                createParticipant(ParticipantType.DEFENDANT)
            );
            caseDto.setOrigin(RecordingOrigin.VODAFONE);
            caseDto.setCourtId(authenticatedUserIds.get(TestingSupportRoles.SUPER_USER).courtId());

            caseDto.setParticipants(participants);
            var booking = createBooking(caseDto.getId(), participants);

            var putCase = doPutRequest(
                CASES_ENDPOINT + "/" + caseDto.getId(),
                OBJECT_MAPPER.writeValueAsString(caseDto),
                TestingSupportRoles.SUPER_USER
            );
            assertResponseCode(putCase, 201);

            var putBooking = putBooking(booking);
            assertResponseCode(putBooking, 201);

            assertBookingExists(booking.getId(), true);

            var request = doGetRequest(BOOKINGS_ENDPOINT + "/" + booking.getId(), role);
            assertResponseCode(request, 403);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class,
            names = {"SUPER_USER", "LEVEL_3"},
            mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not allow access to VODAFONE bookings to non super user requests")
        void findAllBookingsHideVodafoneCasesForNonSuperUser(TestingSupportRoles role) throws JsonProcessingException {
            var caseDto = createCase();
            var participants = Set.of(
                createParticipant(ParticipantType.WITNESS),
                createParticipant(ParticipantType.DEFENDANT)
            );
            caseDto.setOrigin(RecordingOrigin.VODAFONE);
            caseDto.setCourtId(authenticatedUserIds.get(TestingSupportRoles.SUPER_USER).courtId());

            caseDto.setParticipants(participants);
            var booking = createBooking(caseDto.getId(), participants);

            var putCase = doPutRequest(
                CASES_ENDPOINT + "/" + caseDto.getId(),
                OBJECT_MAPPER.writeValueAsString(caseDto),
                TestingSupportRoles.SUPER_USER
            );
            assertResponseCode(putCase, 201);

            var putBooking = putBooking(booking);
            assertResponseCode(putBooking, 201);

            var getBookings = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=" + caseDto.getReference(), role);
            assertResponseCode(getBookings, 200);
            assertThat(getBookings.body().jsonPath().getInt("page.totalElements")).isEqualTo(0);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class,
            names = {"SUPER_USER", "LEVEL_3"},
            mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should allow access to VODAFONE_VISIBLE bookings to non super user requests")
        void findAllBookingsVodafoneVisibleCasesForNonSuperUser(TestingSupportRoles role)
            throws JsonProcessingException {
            var caseDto = createCase();
            var participants = Set.of(
                createParticipant(ParticipantType.WITNESS),
                createParticipant(ParticipantType.DEFENDANT)
            );
            caseDto.setOrigin(RecordingOrigin.VODAFONE_VISIBLE);
            caseDto.setCourtId(authenticatedUserIds.get(role).courtId());

            caseDto.setParticipants(participants);
            var booking = createBooking(caseDto.getId(), participants);

            var putCase = doPutRequest(
                CASES_ENDPOINT + "/" + caseDto.getId(),
                OBJECT_MAPPER.writeValueAsString(caseDto),
                TestingSupportRoles.SUPER_USER
            );
            assertResponseCode(putCase, 201);

            var putBooking = putBooking(booking);
            assertResponseCode(putBooking, 201);

            var getBookings = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=" + caseDto.getReference(), role);
            assertResponseCode(getBookings, 200);
            assertThat(getBookings.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class,
            names = {"SUPER_USER", "LEVEL_3"},
            mode = EnumSource.Mode.EXCLUDE)
        @DisplayName("Should not hide bookings with VODAFONE capture session in non super user requests")
        void findAllBookingsHideVodafoneCasesForNonSuperUserWhereCaptureSessionIsVf(TestingSupportRoles role)
            throws JsonProcessingException {
            var caseDto = createCase();
            var participants = Set.of(
                createParticipant(ParticipantType.WITNESS),
                createParticipant(ParticipantType.DEFENDANT)
            );
            caseDto.setOrigin(RecordingOrigin.PRE);
            caseDto.setCourtId(authenticatedUserIds.get(role).courtId());

            caseDto.setParticipants(participants);
            var booking = createBooking(caseDto.getId(), participants);

            var putCase = putCase(caseDto);
            assertResponseCode(putCase, 201);

            var putBooking = putBooking(booking);
            assertResponseCode(putBooking, 201);

            // should show when booking doesn't have a vf capture session
            var getBookings1 = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=" + caseDto.getReference(), role);
            assertResponseCode(getBookings1, 200);
            assertThat(getBookings1.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);

            CreateCaptureSessionDTO captureSession = createCaptureSession(booking.getId());
            captureSession.setOrigin(RecordingOrigin.VODAFONE);
            var putCaptureSession = putCaptureSession(captureSession);
            assertResponseCode(putCaptureSession, 201);

            var getBookings2 = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=" + caseDto.getReference(), role);
            assertResponseCode(getBookings2, 200);
            assertThat(getBookings2.body().jsonPath().getInt("page.totalElements")).isEqualTo(0);
        }

        @Test
        @DisplayName("Should not hide VODAFONE bookings for super users")
        void findAllBookingsNotHideVodafoneCasesForSuperUser() throws JsonProcessingException {
            var caseDto = createCase();
            var participants = Set.of(
                createParticipant(ParticipantType.WITNESS),
                createParticipant(ParticipantType.DEFENDANT)
            );
            caseDto.setOrigin(RecordingOrigin.VODAFONE);
            caseDto.setCourtId(authenticatedUserIds.get(TestingSupportRoles.SUPER_USER).courtId());

            caseDto.setParticipants(participants);
            var booking = createBooking(caseDto.getId(), participants);

            var putCase = doPutRequest(
                CASES_ENDPOINT + "/" + caseDto.getId(),
                OBJECT_MAPPER.writeValueAsString(caseDto),
                TestingSupportRoles.SUPER_USER
            );
            assertResponseCode(putCase, 201);

            var putBooking = putBooking(booking);
            assertResponseCode(putBooking, 201);
            assertBookingExists(booking.getId(), true);

            var getBookings = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=" + caseDto.getReference(),
                                        TestingSupportRoles.SUPER_USER);
            assertResponseCode(getBookings, 200);
            assertThat(getBookings.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);
        }
    }

    @Nested
    @TestPropertySource(properties = "migration.enableMigratedData=true")
    class WithMigratedDataEnabled extends FunctionalTestBase {
        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class)
        @DisplayName("Should allow access to VODAFONE bookings when feature flag enabled")
        void getById(TestingSupportRoles role) throws JsonProcessingException {
            var caseDto = createCase();
            var participants = Set.of(
                createParticipant(ParticipantType.WITNESS),
                createParticipant(ParticipantType.DEFENDANT)
            );
            caseDto.setOrigin(RecordingOrigin.VODAFONE);
            caseDto.setCourtId(authenticatedUserIds.get(role).courtId());

            caseDto.setParticipants(participants);
            var booking = createBooking(caseDto.getId(), participants);

            var putCase = doPutRequest(
                CASES_ENDPOINT + "/" + caseDto.getId(),
                OBJECT_MAPPER.writeValueAsString(caseDto),
                TestingSupportRoles.SUPER_USER
            );
            assertResponseCode(putCase, 201);

            var putBooking = putBooking(booking);
            assertResponseCode(putBooking, 201);

            var request = doGetRequest(BOOKINGS_ENDPOINT + "/" + booking.getId(), role);
            assertResponseCode(request, 200);
        }

        @ParameterizedTest
        @EnumSource(value = TestingSupportRoles.class)
        @DisplayName("Should allow access to VODAFONE bookings when toggled on")
        void findAllBookingsNotHideVodafoneCases(TestingSupportRoles role) throws JsonProcessingException {
            var caseDto = createCase();
            var participants = Set.of(
                createParticipant(ParticipantType.WITNESS),
                createParticipant(ParticipantType.DEFENDANT)
            );
            caseDto.setOrigin(RecordingOrigin.VODAFONE);
            caseDto.setCourtId(authenticatedUserIds.get(role).courtId());

            caseDto.setParticipants(participants);
            var booking = createBooking(caseDto.getId(), participants);

            var putCase = doPutRequest(
                CASES_ENDPOINT + "/" + caseDto.getId(),
                OBJECT_MAPPER.writeValueAsString(caseDto),
                TestingSupportRoles.SUPER_USER
            );
            assertResponseCode(putCase, 201);

            var putBooking = putBooking(booking);
            assertResponseCode(putBooking, 201);

            var getBookings = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=" + caseDto.getReference(), role);
            assertResponseCode(getBookings, 200);
            assertThat(getBookings.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Scenario: Search for a booking by schedule date")
    void searchBookingByScheduleDate() {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        var dateToSearchStr = LocalDate.now().plusWeeks(1).toString();
        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr,
                                           TestingSupportRoles.SUPER_USER);
        var bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().anyMatch(b -> b.getId().equals(bookingId))).isTrue();

        dateToSearchStr = LocalDate.now().toString();
        bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr,
                                       TestingSupportRoles.SUPER_USER);
        bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().noneMatch(b -> b.getId().equals(bookingId))).isTrue();
    }

    @Test
    @DisplayName("Scenario: Search for a booking by schedule date and case reference")
    void searchBookingByScheduleDateAndCaseReference() {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        var dateToSearchStr = LocalDate.now().plusWeeks(1).toString();
        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr
                                               + "&caseReference=4567890123", TestingSupportRoles.SUPER_USER);
        var bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertResponseCode(bookingResponse, 200);
        assertThat(bookings.stream().anyMatch(b -> b.getId().equals(bookingId))).isTrue();
    }

    @Test
    @DisplayName("Scenario: Search for a booking by partial case reference")
    void searchBookingByPartialCaseReference() {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", null)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=456789", TestingSupportRoles.SUPER_USER);
        var bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().anyMatch(b -> b.getId().equals(bookingId))).isTrue();
    }
}
