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

    @DisplayName("Scenario: Duplicate email address should fail")
    @Test
    void shouldFailCreateUserWithDuplicateEmail() throws JsonProcessingException {
        var dto1 = createUserDto();
        var putResponse1 = putUser(dto1);
        assertResponseCode(putResponse1, 201);
        assertThat(putResponse1.header(LOCATION_HEADER)).isEqualTo(testUrl + USERS_ENDPOINT + "/" + dto1.getId());
        assertPutResponseMatchesDto(dto1);

        var dto2 = createUserDto();
        dto2.setEmail(dto1.getEmail());
        var putResponse2 = putUser(dto2);
        assertResponseCode(putResponse2, 409);
        assertThat(putResponse2.getBody().jsonPath().getString("message"))
            .isEqualTo("Conflict: User with email: " + dto2.getEmail() + " already exists");
    }

    @DisplayName("Scenario: Restore a user")
    @Test
    void shouldRestoreUser() throws JsonProcessingException {
        var dto = createUserDto();
        var createResponse = putUser(dto);
        assertResponseCode(createResponse, 201);
        assertUserExists(dto.getId(), true);

        var deleteResponse = doDeleteRequest(USERS_ENDPOINT + "/" + dto.getId(), true);
        assertResponseCode(deleteResponse, 200);
        assertUserExists(dto.getId(), false);

        var undeleteResponse = doPostRequest(USERS_ENDPOINT + "/" + dto.getId() + "/undelete", true);
        assertResponseCode(undeleteResponse, 200);
        assertUserExists(dto.getId(), true);
    }

    @DisplayName("Get user by email (ignore case)")
    @Test
    void redeemInviteWithEmailIgnoreCase() throws JsonProcessingException {
        // Check matches when db has a value with capitalized email
        var user1 = createUserDto();
        user1.setEmail(user1.getEmail().toUpperCase());

        var putResponse = putUser(user1);
        assertResponseCode(putResponse, 201);
        assertUserExists(user1.getId(), true);

        user1.setEmail(user1.getEmail().toLowerCase());
        var getResponse1 = doGetRequest(USERS_ENDPOINT + "/by-email/" + user1.getEmail().toLowerCase(), false);
        assertResponseCode(getResponse1, 200);
        assertThat(getResponse1.body().jsonPath().getUUID("user.id")).isEqualTo(user1.getId());

        // Check matches when db has a lowercase and searching with uppercase
        var user2 = createUserDto();

        var putResponse2 = putUser(user2);
        assertResponseCode(putResponse2, 201);
        assertUserExists(user2.getId(), true);

        user2.setEmail(user2.getEmail().toUpperCase());
        var getResponse2 = doGetRequest(USERS_ENDPOINT + "/by-email/" + user1.getEmail().toUpperCase(), false);
        assertResponseCode(getResponse2, 200);
        assertThat(getResponse2.body().jsonPath().getUUID("user.id")).isEqualTo(user1.getId());

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
