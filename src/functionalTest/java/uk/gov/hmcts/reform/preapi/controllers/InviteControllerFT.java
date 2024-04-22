package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.dto.CreateInviteDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;

import static org.assertj.core.api.Assertions.assertThat;

public class InviteControllerFT extends FunctionalTestBase {
    @DisplayName("Create a portal invite for new user")
    @Test
    void createPortalInvite() throws JsonProcessingException {
        var dto = createInvite(null);
        assertUserExists(dto.getUserId(), false);

        var putResponse = putInvite(dto);
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + INVITES_ENDPOINT + "/" + dto.getUserId());

        assertUserExists(dto.getUserId(), true);
        assertInviteExists(dto.getUserId(), true);
    }

    @DisplayName("Create a portal invite for a user that already exists")
    @Test
    void createPortalInviteForExistingUser() throws JsonProcessingException {
        var user = createUser();
        var putUser = putUser(user);
        assertResponseCode(putUser, 201);
        assertUserExists(user.getId(), true);

        var dto = createInvite(user.getId());

        var putResponse = putInvite(dto);
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + INVITES_ENDPOINT + "/" + dto.getUserId());

        assertInviteExists(dto.getUserId(), true);
    }

    @DisplayName("Attempt to create a portal invite for a user that already has a portal invite")
    @Test
    void attemptCreatePortalInviteForExistingUser() throws JsonProcessingException {
        var dto = createInvite(null);

        var putResponse = putInvite(dto);
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + INVITES_ENDPOINT + "/" + dto.getUserId());

        var putResponse2 = putInvite(dto);
        assertResponseCode(putResponse2, 204);

        assertUserExists(dto.getUserId(), true);
        assertInviteExists(dto.getUserId(), true);
    }

    @DisplayName("Redeem an invite")
    @Test
    void redeemInvite() throws JsonProcessingException {
        var dto = createInvite(null);
        var putResponse = putInvite(dto);
        assertResponseCode(putResponse, 201);
        assertThat(putResponse.header(LOCATION_HEADER))
            .isEqualTo(testUrl + INVITES_ENDPOINT + "/" + dto.getUserId());
        assertInviteExists(dto.getUserId(), true);

        var redeemResponse = postRedeem(dto);
        assertResponseCode(redeemResponse, 204);
        assertInviteExists(dto.getUserId(), false);

        var getResponse = doGetRequest(INVITES_ENDPOINT + "?email=" + dto.getEmail() + "&accessStatus=ACTIVE", true);
        assertResponseCode(getResponse, 200);
        assertThat(getResponse.body().jsonPath().getInt("page.totalElements")).isEqualTo(1);
        assertThat(getResponse.body().jsonPath().getUUID("_embedded.inviteDTOList[0].user_id"))
            .isEqualTo(dto.getUserId());
    }

    @DisplayName("Redeem an invite that does not exist")
    @Test
    void redeemInviteNotFound() {
        var dto = createInvite(null);

        assertUserExists(dto.getUserId(), false);
        assertInviteExists(dto.getUserId(), false);

        var redeemResponse = postRedeem(dto);
        assertResponseCode(redeemResponse, 404);
        assertInviteExists(dto.getUserId(), false);
        assertUserExists(dto.getUserId(), false);
    }

    @DisplayName("Delete an invite")
    @Test
    void deleteInvite() throws JsonProcessingException {
        var dto = createInvite(null);
        var putResponse = putInvite(dto);
        assertResponseCode(putResponse, 201);
        assertInviteExists(dto.getUserId(), true);
        assertUserExists(dto.getUserId(), true);

        var deleteResponse = doDeleteRequest(INVITES_ENDPOINT + "/" + dto.getUserId(), true);
        assertResponseCode(deleteResponse, 200);
        assertInviteExists(dto.getUserId(), false);
    }

    @DisplayName("Delete an invite that doesn't exist for a user")
    @Test
    void deleteInviteForExistingUser() throws JsonProcessingException {
        var userDto = createUser();
        var putUser = putUser(userDto);
        assertResponseCode(putUser, 201);
        assertUserExists(userDto.getId(), true);

        assertInviteExists(userDto.getId(), false);

        var deleteResponse = doDeleteRequest(INVITES_ENDPOINT + "/" + userDto.getId(), true);
        assertResponseCode(deleteResponse, 404);
    }

    @DisplayName("Delete an invite for a non-existing user")
    @Test
    void deleteInviteForNonExistingUser() throws JsonProcessingException {
        var userId = UUID.randomUUID();
        assertUserExists(userId, false);

        var deleteResponse = doDeleteRequest(INVITES_ENDPOINT + "/" + userId, true);
        assertResponseCode(deleteResponse, 404);
    }

    private CreateInviteDTO createInvite(@Nullable UUID userId) {
        var dto = new CreateInviteDTO();
        dto.setUserId(userId != null ? userId : UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Person");
        dto.setEmail(dto.getUserId() + "@example.com");
        dto.setOrganisation("Example Organisation");
        dto.setPhone("0123456789");
        return dto;
    }

    private CreateUserDTO createUser() {
        var dto = new CreateUserDTO();
        dto.setId(UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Person");
        dto.setEmail(dto.getId() + "@example.com");
        dto.setOrganisation("Example Organisation");
        dto.setPhoneNumber("0123456789");
        dto.setAppAccess(Set.of());
        dto.setPortalAccess(Set.of());
        return dto;
    }

    private Response putInvite(CreateInviteDTO dto) throws JsonProcessingException {
        return doPutRequest(INVITES_ENDPOINT + "/" + dto.getUserId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }

    private Response putUser(CreateUserDTO dto) throws JsonProcessingException {
        return doPutRequest(USERS_ENDPOINT + "/" + dto.getId(), OBJECT_MAPPER.writeValueAsString(dto), true);
    }

    private Response postRedeem(CreateInviteDTO dto) {
        return doPostRequest(INVITES_ENDPOINT + "/redeem?email=" + dto.getEmail(), false);
    }
}
