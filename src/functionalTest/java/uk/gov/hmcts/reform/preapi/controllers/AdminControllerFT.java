package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class AdminControllerFT extends FunctionalTestBase {

    protected static final String ADMIN_ENDPOINT = "/admin";

    @DisplayName("Should get back table type UUID belongs to")
    @Test
    void checkUuidExists() throws JsonProcessingException {
        UUID randomUuid = UUID.randomUUID();
        var user = createUserDto(randomUuid);
        putUser(user);

        var response =
            doGetRequest(ADMIN_ENDPOINT + "/" + user.getId(), TestingSupportRoles.SUPER_USER);

        assertThat(response.body().prettyPrint())
            .isEqualTo("Uuid relates to a USER");
        assertResponseCode(response, 200);
    }

    @DisplayName("Should return bad request when UUID not present in relevant tables")
    @Test
    void checkRequestFails() throws JsonProcessingException {

        UUID randomUuid = UUID.randomUUID();

        var response =
            doGetRequest(ADMIN_ENDPOINT + "/" + randomUuid, TestingSupportRoles.SUPER_USER);

        assertThat(response.body().jsonPath().getString("message"))
            .isEqualTo("Not found: " + randomUuid + " does not exist in any relevant table");
        assertResponseCode(response, 404);
    }

    @DisplayName("Should return not authorised due to role")
    @Test
    void checkIfUserAuthorised() throws JsonProcessingException {
        UUID randomUuid = UUID.randomUUID();
        var user = createUserDto(randomUuid);
        putUser(user);

        var response =
            doGetRequest(ADMIN_ENDPOINT + "/" + user.getId(), TestingSupportRoles.LEVEL_1);

        assertThat(response.body().jsonPath().getString("error"))
            .isEqualTo("Forbidden");
        assertResponseCode(response, 403);
    }

    private CreateUserDTO createUserDto(UUID uuid) {
        var dto = new CreateUserDTO();
        dto.setId(uuid);
        dto.setEmail("hello@test.com");
        dto.setFirstName("Example");
        dto.setLastName("User");
        dto.setPortalAccess(Set.of());
        dto.setAppAccess(Set.of());
        dto.setOrganisation("Example Organisation");
        dto.setPhoneNumber("1234567890");
        return dto;
    }

}

