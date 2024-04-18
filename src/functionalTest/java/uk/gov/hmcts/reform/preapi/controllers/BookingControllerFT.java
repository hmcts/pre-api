package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateShareBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.dto.RoomDTO;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
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

        var testIds = doPostRequest("/testing-support/should-delete-recordings-for-booking", false).body().jsonPath();

        var bookingId = testIds.getUUID("bookingId");
        var recordingId = testIds.getUUID("recordingId");

        assertBookingExists(bookingId, true);
        var recordingResponse = assertRecordingExists(recordingId, true);
        assertThat(recordingResponse.body().jsonPath().getString("created_at")).isNotBlank();

        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + bookingId, true);
        assertResponseCode(deleteResponse, 400);
        assertThat(deleteResponse.getBody().jsonPath().getString("message"))
            .isEqualTo("Cannot delete because and associated recording has not been deleted.");
        assertRecordingExists(recordingId, true);
    }

    @DisplayName("Scenario: Delete booking")
    @Test
    void shouldDeleteRecordingsForBooking() {
        var testIds = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath();
        var bookingId = testIds.getUUID("bookingId");
        var courtId = testIds.getUUID("courtId");

        // see it is available before deletion
        assertBookingExists(bookingId, true);

        var searchResponse1 = doGetRequest(BOOKINGS_ENDPOINT + "?courtId=" + courtId, true);
        assertResponseCode(searchResponse1, 200);
        var responseData1 = searchResponse1.jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);
        assertThat(responseData1.size()).isEqualTo(1);
        assertThat(responseData1.getFirst().getId()).isEqualTo(bookingId);

        // delete booking
        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + bookingId, true);
        assertResponseCode(deleteResponse, 204);

        // see it is no longer available after deletion
        assertBookingExists(bookingId, false);

        var searchResponse2 = doGetRequest(BOOKINGS_ENDPOINT + "?courtId=" + courtId, true);
        assertResponseCode(searchResponse2, 200);
        assertThat(searchResponse2.jsonPath().getInt("page.totalElements"))
            .isEqualTo(0);
    }

    @Test
    @DisplayName("Scenario: The schedule date should not be amended to the past date")
    void recordingScheduleDateShouldNotBeAmendedToThePast() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "/" + bookingId, true);
        assertResponseCode(bookingResponse, 200);

        BookingDTO booking = bookingResponse.body().as(BookingDTO.class);

        // assert the booking is well-formed
        assertThat(booking.getId()).isEqualTo(bookingId);
        var region = booking.getCaseDTO().getCourt().getRegions().stream().findFirst();
        assertThat(region.orElseGet(RegionDTO::new).getName()).isEqualTo("Foo Region");
        var rooms = booking.getCaseDTO().getCourt().getRooms().stream().findFirst();
        assertThat(rooms.orElseGet(RoomDTO::new).getName()).isEqualTo("Foo Room");

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

        var putResponse = putBooking(createBooking);
        assertResponseCode(putResponse, 400);
    }

    @Test
    @DisplayName("Deleting a non-existent booking should return 404")
    void deletingNonExistentBookingShouldReturn404() {
        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + "00000000-0000-0000-0000-000000000000", true);
        assertResponseCode(deleteResponse, 404);
    }

    @DisplayName("Unauthorised use of endpoints should return 401")
    @Test
    void unauthorisedRequestsReturn401() throws JsonProcessingException {
        var getBookingsResponse = doGetRequest("/bookings", false);
        assertResponseCode(getBookingsResponse, 401);

        var getBookingsByIdResponse = doGetRequest(BOOKINGS_ENDPOINT + "/" + UUID.randomUUID(), false);
        assertResponseCode(getBookingsByIdResponse, 401);

        var putBookingResponse = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + UUID.randomUUID(),
            OBJECT_MAPPER.writeValueAsString(new CreateBookingDTO()),
            false
        );
        assertResponseCode(putBookingResponse, 401);

        var deleteBookingResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + UUID.randomUUID(), false);
        assertResponseCode(deleteBookingResponse, 401);

        var putShareBookingResponse = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + UUID.randomUUID() + "/share",
            OBJECT_MAPPER.writeValueAsString(new CreateShareBookingDTO()),
            false
        );
        assertResponseCode(putShareBookingResponse, 401);

        var deleteShareBookingResponse = doDeleteRequest(
            BOOKINGS_ENDPOINT + "/" + UUID.randomUUID() + "/share",
            false
        );
        assertResponseCode(deleteShareBookingResponse, 401);
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
            true
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
            true
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
            true
        );
        assertResponseCode(putCase, 201);

        var putBooking = putBooking(booking);
        assertResponseCode(putBooking, 400);
        assertThat(putBooking.body().jsonPath().getString("scheduledFor"))
            .isEqualTo("scheduled_for is required and must not be before today");
    }

    @DisplayName("Scenario: Restore booking")
    @Test
    void undeleteBooking() throws JsonProcessingException {
        var caseEntity = createCase();
        var participants = Set.of(
            createParticipant(ParticipantType.WITNESS),
            createParticipant(ParticipantType.DEFENDANT)
        );
        caseEntity.setParticipants(participants);

        var putCase = doPutRequest(
            CASES_ENDPOINT + "/" + caseEntity.getId(),
            OBJECT_MAPPER.writeValueAsString(caseEntity),
            true
        );
        assertResponseCode(putCase, 201);

        var booking = createBooking(caseEntity.getId(), participants);
        var putResponse = putBooking(booking);
        assertResponseCode(putResponse, 201);
        assertBookingExists(booking.getId(), true);

        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + booking.getId(), true);
        assertResponseCode(deleteResponse, 204);
        assertBookingExists(booking.getId(), false);

        var undeleteResponse = doPostRequest(BOOKINGS_ENDPOINT + "/" + booking.getId() + "/undelete", true);
        assertResponseCode(undeleteResponse, 200);
        assertBookingExists(booking.getId(), true);
    }

    private CreateBookingDTO createBooking(UUID caseId, Set<CreateParticipantDTO> participants) {
        var dto = new CreateBookingDTO();
        dto.setId(UUID.randomUUID());
        dto.setCaseId(caseId);
        dto.setParticipants(participants);
        dto.setScheduledFor(Timestamp.valueOf(LocalDate.now().atStartOfDay()));
        return dto;
    }

    private CreateParticipantDTO createParticipant(ParticipantType type) {
        var dto = new CreateParticipantDTO();
        dto.setId(UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Example");
        dto.setParticipantType(type);
        return dto;
    }

    private Response putBooking(CreateBookingDTO dto) throws JsonProcessingException {
        return doPutRequest(
            BOOKINGS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            true
        );
    }

    @DisplayName("Scenario: Search for a booking by schedule date")
    @Test
    void searchBookingByScheduleDate() throws JsonProcessingException {
        var dateToSearchStr = LocalDate.now().plusWeeks(1).toString();

        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        Response bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr, true);

        var bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().anyMatch(b -> b.getId().equals(bookingId))).isTrue();

        dateToSearchStr = LocalDate.now().toString();

        bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr, true);

        bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().noneMatch(b -> b.getId().equals(bookingId))).isTrue();
    }

    @DisplayName("Scenario: Search for a booking by schedule date and case reference")
    @Test
    void searchBookingByScheduleDateAndCaseReference() throws JsonProcessingException {
        var dateToSearchStr = LocalDate.now().plusWeeks(1).toString();

        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        Response bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr
            + "&caseReference=4567890123", true);

        var bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertResponseCode(bookingResponse, 200);
        assertThat(bookings.stream().anyMatch(b -> b.getId().equals(bookingId))).isTrue();
    }

    @DisplayName("Scenario: Search for a booking by partial case reference")
    @Test
    void searchBookingByPartialCaseReference() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        Response bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?caseReference=456789", true);

        var bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().anyMatch(b -> b.getId().equals(bookingId))).isTrue();
    }
}
