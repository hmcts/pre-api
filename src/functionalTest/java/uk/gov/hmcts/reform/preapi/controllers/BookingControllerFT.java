package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateBookingDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.dto.RegionDTO;
import uk.gov.hmcts.reform.preapi.dto.RoomDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.sql.Timestamp;
import java.time.OffsetDateTime;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class BookingControllerFT extends FunctionalTestBase {

    private static final String BOOKINGS_ENDPOINT = "/bookings/";
    private static final String RECORDINGS_ENDPOINT = "/recordings/";

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldDeleteRecordingsForBooking() {

        var testIds = doPostRequest("/testing-support/should-delete-recordings-for-booking").body().jsonPath();

        var bookingId = testIds.get("bookingId");
        var recordingId = testIds.get("recordingId");

        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + bookingId);
        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookingResponse.body().jsonPath().getString("id")).isEqualTo(bookingId);

        var recordingResponse = doGetRequest(RECORDINGS_ENDPOINT + recordingId);
        assertThat(recordingResponse.statusCode()).isEqualTo(200);
        assertThat(recordingResponse.body().jsonPath().getString("id")).isEqualTo(recordingId);
        assertThat(recordingResponse.body().jsonPath().getString("created_at")).isNotBlank();

        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + bookingId);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        var recordingResponse2 = doGetRequest(RECORDINGS_ENDPOINT + recordingId);
        assertThat(recordingResponse2.statusCode()).isEqualTo(404);
    }

    @Test
    @DisplayName("Scenario: The schedule date should not be amended to the past date")
    void recordingScheduleDateShouldNotBeAmendedToThePast() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking")
            .body()
            .jsonPath()
            .getUUID("bookingId");

        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + bookingId);

        assertThat(bookingResponse.statusCode()).isEqualTo(200);

        BookingDTO booking = bookingResponse.body().as(BookingDTO.class);

        // assert the booking is well-formed
        assertThat(booking.getId()).isEqualTo(bookingId);
        var region = booking.getCaseDTO().getCourt().getRegions().stream().findFirst();
        assertThat(region.orElseGet(RegionDTO::new).getName()).isEqualTo("Foo Region");
        var rooms = booking.getCaseDTO().getCourt().getRooms().stream().findFirst();
        assertThat(rooms.orElseGet(RoomDTO::new).getName()).isEqualTo("Foo Room");

        // validate the court referenced does exist
        var courtResponse = doGetRequest("/courts/" + booking.getCaseDTO().getCourt().getId());
        assertThat(courtResponse.statusCode()).isEqualTo(200);
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

        var putResponse = doPutRequest(BOOKINGS_ENDPOINT + bookingId, OBJECT_MAPPER.writeValueAsString(createBooking));

        assertThat(putResponse.statusCode()).isEqualTo(400);
    }

    @Test
    @DisplayName("Deleting a non-existent booking should return 404")
    void deletingNonExistentBookingShouldReturn404() {
        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + "00000000-0000-0000-0000-000000000000");
        assertThat(deleteResponse.statusCode()).isEqualTo(404);
    }
}
