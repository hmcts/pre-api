package uk.gov.hmcts.reform.preapi.controllers;

import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.text.MessageFormat;

import static org.assertj.core.api.Assertions.assertThat;

class BookingControllerFT extends FunctionalTestBase {

    private static final String BOOKINGS_ENDPOINT = "/cases/{0}/bookings/";
    private static final String RECORDINGS_ENDPOINT = "/bookings/{0}/recordings/";

    @Test
    void shouldDeleteRecordingsForBooking() {

        var testIds = doPostRequest("/testing-support/should-delete-recordings-for-booking").body().jsonPath();

        var caseId = testIds.get("caseId");
        var bookingId = testIds.get("bookingId");
        var recordingId = testIds.get("recordingId");

        var bookingResponse = doGetRequest(
            MessageFormat.format(BOOKINGS_ENDPOINT, caseId) + bookingId);
        assertThat(bookingResponse.statusCode()).isEqualTo(200);
        assertThat(bookingResponse.body().jsonPath().getString("id")).isEqualTo(bookingId);

        var recordingResponse = doGetRequest(
            MessageFormat.format(RECORDINGS_ENDPOINT, bookingId) + recordingId);
        assertThat(recordingResponse.statusCode()).isEqualTo(200);
        assertThat(recordingResponse.body().jsonPath().getString("id")).isEqualTo(recordingId);
        assertThat(recordingResponse.body().jsonPath().getString("createdAt")).isNotBlank();

        var deleteResponse = doDeleteRequest(
            MessageFormat.format(BOOKINGS_ENDPOINT, caseId) + bookingId);
        assertThat(deleteResponse.statusCode()).isEqualTo(204);

        var recordingResponse2 = doGetRequest(
            MessageFormat.format(RECORDINGS_ENDPOINT, bookingId) + recordingId);
        assertThat(recordingResponse2.statusCode()).isEqualTo(404);
    }
}
