package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class BookingControllerFT extends FunctionalTestBase {

    private static final String BOOKINGS_ENDPOINT = "/bookings/";
    private static final String RECORDINGS_ENDPOINT = "/recordings/";

    @Test
    void shouldDeleteRecordingsForBooking() {

        var testIds = doPostRequest("/testing-support/should-delete-recordings-for-booking").body().jsonPath();

        var caseId = testIds.get("caseId");
        var bookingId = testIds.get("bookingId");
        var recordingId = testIds.get("recordingId");

        var bookingResponse = doGetRequest(BOOKINGS_ENDPOINT + bookingId);
        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookingResponse.body().jsonPath().getString("id")).isEqualTo(bookingId);

        var recordingResponse = doGetRequest(RECORDINGS_ENDPOINT + recordingId);
        assertThat(recordingResponse.statusCode()).isEqualTo(200);
        assertThat(recordingResponse.body().jsonPath().getString("id")).isEqualTo(recordingId);
        var recordingCreatedAt = recordingResponse.body().prettyPrint();
        assertThat(recordingResponse.body().jsonPath().getString("created_at")).isNotBlank();

        var deleteResponse = doDeleteRequest(BOOKINGS_ENDPOINT + bookingId);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        var recordingResponse2 = doGetRequest(RECORDINGS_ENDPOINT + recordingId);
        assertThat(recordingResponse2.statusCode()).isEqualTo(404);
    }
}
