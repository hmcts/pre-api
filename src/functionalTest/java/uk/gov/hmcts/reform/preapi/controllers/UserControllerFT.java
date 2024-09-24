package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
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

        var deleteResponse = doDeleteRequest(USERS_ENDPOINT + "/" + dto.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(deleteResponse, 200);
        assertUserExists(dto.getId(), false);

        var undeleteResponse =
            doPostRequest(USERS_ENDPOINT + "/" + dto.getId() + "/undelete", TestingSupportRoles.SUPER_USER);
        assertResponseCode(undeleteResponse, 200);
        assertUserExists(dto.getId(), true);
    }

    @DisplayName("Get user by email (ignore case)")
    @Test
    void userByEmailIgnoreCase() throws JsonProcessingException {
        // Check matches when db has a value with capitalized email
        var user1 = createUserDto();
        user1.setEmail(user1.getEmail().toUpperCase());

        var putResponse = putUser(user1);
        assertResponseCode(putResponse, 201);
        assertUserExists(user1.getId(), true);

        user1.setEmail(user1.getEmail().toLowerCase());
        var getResponse1 = doGetRequest(USERS_ENDPOINT + "/by-email/" + user1.getEmail().toLowerCase(), null);
        assertResponseCode(getResponse1, 200);
        assertThat(getResponse1.body().jsonPath().getUUID("user.id")).isEqualTo(user1.getId());

        // Check matches when db has a lowercase and searching with uppercase
        var user2 = createUserDto();

        var putResponse2 = putUser(user2);
        assertResponseCode(putResponse2, 201);
        assertUserExists(user2.getId(), true);

        user2.setEmail(user2.getEmail().toUpperCase());
        var getResponse2 = doGetRequest(USERS_ENDPOINT + "/by-email/" + user1.getEmail().toUpperCase(), null);
        assertResponseCode(getResponse2, 200);
        assertThat(getResponse2.body().jsonPath().getUUID("user.id")).isEqualTo(user1.getId());
    }

    @DisplayName("Get users by app active status")
    @Test
    void userFilteredByAppActiveStatus() throws JsonProcessingException {
        var user = createUserDto();
        var roleId = createRole();
        var court1 = createCourt();
        var court2 = createCourt();
        var access1 = createAppAccessDto(user.getId(), court1.getId(), roleId);
        var access2 = createAppAccessDto(user.getId(), court2.getId(), roleId);
        access2.setActive(false);
        access2.setDefaultCourt(false);
        user.setAppAccess(Set.of(access1, access2));
        putCourt(court1);
        assertCourtExists(court1.getId(), true);

        putCourt(court2);
        assertCourtExists(court2.getId(), true);

        putUser(user);
        assertUserExists(user.getId(), true);

        // has at least one active app access
        var responseActiveTrue =
            doGetRequest(USERS_ENDPOINT + "?appActive=true&email=" + user.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(responseActiveTrue, 200);
        assertThat(responseActiveTrue.body().jsonPath().getUUID("_embedded.userDTOList[0].id")).isEqualTo(user.getId());

        // app access for court is active
        var responseActiveTrueByCourt = doGetRequest(
            USERS_ENDPOINT + "?appActive=true&courtId=" + court1.getId() + "&email=" + user.getId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(responseActiveTrueByCourt, 200);
        assertThat(responseActiveTrueByCourt.body().jsonPath().getUUID("_embedded.userDTOList[0].id"))
            .isEqualTo(user.getId());

        // app access for court is inactive (searching for active)
        var responseActiveTrueByCourt2 = doGetRequest(
            USERS_ENDPOINT + "?appActive=true&courtId=" + court2.getId() + "&email=" + user.getId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(responseActiveTrueByCourt2, 200);
        assertThat(responseActiveTrueByCourt2.body().jsonPath().getInt("page.totalElements"))
            .isEqualTo(0);

        // has at least one inactive app access
        var responseActiveFalse =
            doGetRequest(USERS_ENDPOINT + "?appActive=false&email=" + user.getId(), TestingSupportRoles.SUPER_USER);
        assertResponseCode(responseActiveFalse, 200);
        assertThat(responseActiveFalse.body().jsonPath().getUUID("_embedded.userDTOList[0].id"))
            .isEqualTo(user.getId());

        // app access for court is active (searching for inactive)
        var responseActiveFalseByCourt = doGetRequest(
            USERS_ENDPOINT + "?appActive=false&courtId=" + court1.getId() + "&email=" + user.getId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(responseActiveFalseByCourt, 200);
        assertThat(responseActiveFalseByCourt.body().jsonPath().getInt("page.totalElements"))
            .isEqualTo(0);

        // app access for court is inactive
        var responseActiveFalseByCourt2 = doGetRequest(
            USERS_ENDPOINT + "?appActive=false&courtId=" + court2.getId() + "&email=" + user.getId(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(responseActiveFalseByCourt2, 200);
        assertThat(responseActiveFalseByCourt2.body().jsonPath().getUUID("_embedded.userDTOList[0].id"))
            .isEqualTo(user.getId());

        // delete app access
        user.setAppAccess(Set.of(access1));
        putUser(user);
        assertUserExists(user.getId(), true);

        // app access deleted, filter by appActive=false response empty
        var responseActiveTrueForDeletedCourtAccess = doGetRequest(
            USERS_ENDPOINT + "?appActive=false&courtId=" + court2.getId() + "&email=" + user.getEmail(),
            TestingSupportRoles.SUPER_USER
        );
        assertResponseCode(responseActiveTrueForDeletedCourtAccess, 200);
        responseActiveTrueForDeletedCourtAccess.prettyPrint();
        assertThat(responseActiveTrueForDeletedCourtAccess.body().jsonPath().getInt("page.totalElements"))
            .isEqualTo(0);
    }

    private void assertPutResponseMatchesDto(CreateUserDTO dto) {
        var getResponse = doGetRequest(USERS_ENDPOINT + "/" + dto.getId(), TestingSupportRoles.SUPER_USER);
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

    private CreateAppAccessDTO createAppAccessDto(UUID userId, UUID courtId, UUID roleId) {
        var dto = new CreateAppAccessDTO();
        dto.setId(UUID.randomUUID());
        dto.setUserId(userId);
        dto.setCourtId(courtId);
        dto.setRoleId(roleId);
        dto.setActive(true);
        dto.setDefaultCourt(true);
        return dto;
    }

    private UUID createRole() {
        return doPostRequest("/testing-support/create-role?roleName=SUPER_USER", TestingSupportRoles.SUPER_USER)
            .body()
            .jsonPath().getUUID("roleId");
    }
}
