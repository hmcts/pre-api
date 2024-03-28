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
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BookingControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Delete booking with recordings")
    @Test
    void shouldNotDeleteRecordingsForBooking() {

        var testIds = doPostRequest("/testing-support/should-delete-recordings-for-booking", false).body().jsonPath();

        var bookingId = testIds.get("bookingId");
        var recordingId = testIds.get("recordingId");

        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "/" + bookingId, true);
        assertResponseCode(bookingResponse, 200);
        assertThat(bookingResponse.body().jsonPath().getString("id")).isEqualTo(bookingId);

        var recordingResponse = doGetRequest(RECORDINGS_ENDPOINT + "/" + recordingId, true);
        assertResponseCode(recordingResponse, 200);
        assertThat(recordingResponse.body().jsonPath().getString("id")).isEqualTo(recordingId);
        assertThat(recordingResponse.body().jsonPath().getString("created_at")).isNotBlank();

        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "/" + bookingId, true);
        assertResponseCode(deleteResponse, 400);
        assertThat(deleteResponse.getBody().jsonPath().getString("message"))
            .isEqualTo("Cannot delete because and associated recording has not been deleted.");

        var recordingResponse2 = doGetRequest(RECORDINGS_ENDPOINT + "/" + recordingId, true);
        assertResponseCode(recordingResponse2, 200);
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
        var courtResponse = doGetRequest("/courts/" + booking.getCaseDTO().getCourt().getId(), true);
        assertResponseCode(courtResponse, 200);
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

        var putResponse = doPutRequest(
            BOOKINGS_ENDPOINT + "/" + bookingId,
            OBJECT_MAPPER.writeValueAsString(createBooking),
            true
        );
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

    /*
    @DisplayName("Scenario: Search for a booking by schedule date")
    @Test
    void searchBookingByScheduleDate() throws JsonProcessingException {
        var dateToSearch = Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant());
        var dateToSearchStr = dateToSearch.toString().split(" ")[0];

        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath()
            .getUUID("bookingId");

        Response bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr, true);

        var bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().anyMatch(b -> b.getId().equals(bookingId))).isTrue();


        dateToSearch = Timestamp.from(OffsetDateTime.now().toInstant());
        dateToSearchStr = dateToSearch.toString().split(" ")[0];

        bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + "?scheduledFor=" + dateToSearchStr, true);

        bookings = bookingResponse.body().jsonPath().getList("_embedded.bookingDTOList", BookingDTO.class);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookings.stream().noneMatch(b -> b.getId().equals(bookingId))).isTrue();
    }
    */

    @DisplayName("Scenario: Search for a booking by schedule date and case reference")
    @Test
    void searchBookingByScheduleDateAndCaseReference() throws JsonProcessingException {
        var dateToSearch = Timestamp.from(OffsetDateTime.now().plusWeeks(1).toInstant());
        var dateToSearchStr = dateToSearch.toString().split(" ")[0];

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
}
