package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateCaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.enums.RecordingOrigin;
import uk.gov.hmcts.reform.preapi.enums.RecordingStatus;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CaptureSessionControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Delete capture session")
    @Test
    void shouldDeleteCaptureSession() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/create-well-formed-booking", false)
            .body()
            .jsonPath().getUUID("bookingId");

        var dto = new CreateCaptureSessionDTO();
        dto.setId(UUID.randomUUID());
        dto.setBookingId(bookingId);
        dto.setStatus(RecordingStatus.STANDBY);
        dto.setOrigin(RecordingOrigin.PRE);

        // create capture session
        var putResponse = doPutRequest(
            CAPTURE_SESSIONS_ENDPOINT + '/' + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            true
        );
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId());

        // see it is available before deletion
        var getCaptureSession1 = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(getCaptureSession1, 200);
        assertThat(getCaptureSession1.getBody().jsonPath().getUUID("id")).isEqualTo(dto.getId());

        // delete capture session
        var deleteResponse = doDeleteRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(deleteResponse, 200);

        // see it is no longer available after deletion
        var getCaptureSession2 = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(getCaptureSession2, 404);

        var searchCaptureSessionResponse = doGetRequest(CAPTURE_SESSIONS_ENDPOINT + "?bookingId=" + bookingId, true);
        assertResponseCode(searchCaptureSessionResponse, 200);
        assertThat(searchCaptureSessionResponse.getBody().jsonPath().getInt("page.totalElements")).isEqualTo(0);
    }
}
