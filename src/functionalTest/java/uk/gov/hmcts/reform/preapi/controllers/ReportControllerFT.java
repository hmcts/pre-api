package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportControllerFT extends FunctionalTestBase {


    @DisplayName("Scenario: Should format Duration and date correctly")
    @Test
    void shouldFormatDurationAndDateCorrectly() throws JsonProcessingException {
        doPostRequest("/testing-support/should-delete-recordings-for-booking", false);

        var response = doGetRequest(REPORTS_ENDPOINT + "/capture-sessions-concurrent", true);

        var json = OBJECT_MAPPER.readTree(response.getBody().asString());

        assertThat(json.get(0).get("start_time").asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z");
        // find a record which has a duration set (which is optional) and validate the format
        for (int i = 0; i < json.size(); i++) {
            if (json.get(i).has("duration")) {
                assertThat(json.get(i).get("duration").asText()).matches("PT(\\d{1,2}H)?\\d{1,2}M");
                break;
            }
        }
    }
}
