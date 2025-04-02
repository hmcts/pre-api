package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.reports.ConcurrentCaptureSessionReportDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;

public class ReportControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Should format Duration and date correctly")
    @Test
    void shouldFormatDurationAndDateCorrectly() throws JsonProcessingException {
        var captureSessionId = doPostRequest("/testing-support/should-delete-recordings-for-booking",
                                             TestingSupportRoles.SUPER_USER)
            .body().jsonPath().getUUID("captureSessionId");

        var response = doGetRequest(REPORTS_ENDPOINT + "/capture-sessions-concurrent", TestingSupportRoles.SUPER_USER);
        var list = response.getBody().jsonPath().getList("", ConcurrentCaptureSessionReportDTO.class);

        int index = 0;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).getId().equals(captureSessionId)) {
                index = i;
                break;
            }
        }

        var json = OBJECT_MAPPER.readTree(response.getBody().asString());

        assertThat(json.get(index).get("duration").asText()).isEqualTo("PT3M");
        assertThat(json.get(index).get("start_time")
                       .asText()).matches("\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}.\\d{3}Z");
    }
}
