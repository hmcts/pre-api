package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UserControllerFT extends FunctionalTestBase {
    @DisplayName("Scenario: Create/update a user")
    @Test
    void shouldCreateUser() throws JsonProcessingException {
        var dto = createUserDto();

        var createResponse = putUser(dto);
        assertResponseCode(createResponse, 201);
        assertThat(createResponse.header(LOCATION_HEADER)).isEqualTo(testUrl + USERS_ENDPOINT + "/" + dto.getId());
        assertPutResponseMatchesDto(dto);

        dto.setFirstName("Updated First Name");
        var updateResponse = putUser(dto);
        assertResponseCode(updateResponse, 204);
        assertThat(updateResponse.header(LOCATION_HEADER)).isEqualTo(testUrl + USERS_ENDPOINT + "/" + dto.getId());
        assertPutResponseMatchesDto(dto);
    }

    private Response putUser(CreateUserDTO dto) throws JsonProcessingException {
        return doPutRequest(
            USERS_ENDPOINT + "/" + dto.getId(),
            OBJECT_MAPPER.writeValueAsString(dto),
            true
        );
    }

    private void assertPutResponseMatchesDto(CreateUserDTO dto) {
        var getResponse = doGetRequest(USERS_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(getResponse, 200);
        assertThat(getResponse.getBody().jsonPath().getUUID("id")).isEqualTo(dto.getId());
        assertThat(getResponse.getBody().jsonPath().getString("email")).isEqualTo(dto.getEmail());
        assertThat(getResponse.getBody().jsonPath().getString("first_name")).isEqualTo(dto.getFirstName());
        assertThat(getResponse.getBody().jsonPath().getString("last_name")).isEqualTo(dto.getLastName());
    }

    private CreateUserDTO createUserDto() {
        var userId = UUID.randomUUID();
        var dto = new CreateUserDTO();
        dto.setId(userId);
        dto.setEmail(userId + "@test.com");
        dto.setFirstName("Example");
        dto.setLastName("User");
        dto.setPortalAccess(Set.of());
        dto.setAppAccess(Set.of());
        dto.setOrganisation("Example Organisation");
        dto.setPhoneNumber("1234567890");
        return dto;
    }
}
