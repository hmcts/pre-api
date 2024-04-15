package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class CourtControllerFT extends FunctionalTestBase {
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

    private CreateCourtDTO createCourt() {
        var roomId = doPostRequest("/testing-support/create-room", false).body().jsonPath().getUUID("roomId");
        var regionId = doPostRequest("/testing-support/create-region", false).body().jsonPath().getUUID("regionId");

        var dto = new CreateCourtDTO();
        dto.setId(UUID.randomUUID());
        dto.setName("Example Court");
        dto.setCourtType(CourtType.CROWN);
        dto.setRooms(List.of(roomId));
        dto.setRegions(List.of(regionId));
        dto.setLocationCode("123456789");
        return dto;
    }

    private Response putCourt(CreateCourtDTO dto) throws JsonProcessingException {
        return doPutRequest(COURTS_ENDPOINT + "/" + dto.getId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }
}
