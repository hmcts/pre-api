package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import static org.assertj.core.api.Assertions.assertThat;

class CourtControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Create and update a court")
    @Test
    void createAndUpdateCourt() throws JsonProcessingException {
        var dto = createCourt();

        // create court
        var createResponse = putCourt(dto);
        assertResponseCode(createResponse, 201);
        var courtResponse1 = assertCourtExists(dto.getId(), true);
        assertThat(courtResponse1.body().jsonPath().getString("name")).isEqualTo(dto.getName());

        // update court
        dto.setName("Updated Court");
        var updateResponse = putCourt(dto);
        assertResponseCode(updateResponse, 204);
        var courtResponse2 = assertCourtExists(dto.getId(), true);
        assertThat(courtResponse2.body().jsonPath().getString("name")).isEqualTo(dto.getName());
    }
}
