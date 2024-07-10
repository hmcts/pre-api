package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportControllerFT extends FunctionalTestBase {


    @DisplayName("Scenario: Should format Duration and date correctly")
    @Test
    void shouldFormatDurationAndDateCorrectly() throws JsonProcessingException {
        var postResponseData = doPostRequest("/testing-support/should-delete-recordings-for-booking", false)
            .body().jsonPath();

        var response = doGetRequest(REPORTS_ENDPOINT + "/capture-sessions-concurrent", true);

        var om = new ObjectMapper();

        System.out.println(response.getBody().asString());

        var json = om.readTree(response.getBody().asString());

        assertThat(json.get(0).get("duration").asText()).isEqualTo("PT30M");
        assertThat(json.get(0).get("start_time").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z");


    }
}
