package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.BookingDTO;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportControllerFT extends FunctionalTestBase {

    private static final String DATE_FORMAT_REGEX = "^\\d{2}/\\d{2}/\\d{4}$";
    private static final String TIME_FORMAT_REGEX = "^\\d{2}:\\d{2}:\\d{2}$";

    @DisplayName("Scenario: Should format Duration and date correctly")
    @Test
    void shouldFormatDurationAndDateCorrectly() throws JsonProcessingException {
        var bookingId = doPostRequest("/testing-support/should-delete-recordings-for-booking",
                                             TestingSupportRoles.SUPER_USER)
            .body().jsonPath().getUUID("bookingId");
        var booking = doGetRequest("/bookings/" + bookingId, TestingSupportRoles.SUPER_USER)
            .body().jsonPath().getObject("", BookingDTO.class);

        var response = doGetRequest(REPORTS_ENDPOINT + "/capture-sessions-concurrent", TestingSupportRoles.SUPER_USER);
        var list = response.getBody().jsonPath().getList("", ConcurrentCaptureSessionReportDTO.class);
        var reportItemIndex = IntStream.range(0, list.size())
            .filter(i -> list.get(i).getCaseReference().equals(booking.getCaseDTO().getReference()))
            .findFirst()
            .orElseThrow();

        var jsonItem = OBJECT_MAPPER.readTree(response.getBody().asString()).get(reportItemIndex);
        assertThat(jsonItem.get("date").asText()).matches(DATE_FORMAT_REGEX);
        assertThat(jsonItem.get("start_time").asText()).matches(TIME_FORMAT_REGEX);
        assertThat(jsonItem.get("end_time").asText()).matches(TIME_FORMAT_REGEX);
        assertThat(jsonItem.get("duration").asText()).matches(TIME_FORMAT_REGEX);
    }
}
