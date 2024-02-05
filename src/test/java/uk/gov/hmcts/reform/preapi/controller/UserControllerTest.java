package uk.gov.hmcts.reform.preapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.UserController;
import uk.gov.hmcts.reform.preapi.dto.AppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class UserControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private UserService userService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    @DisplayName("Should get user by id with 200 response code")
    @Test
    void getUserByIdSuccess() throws Exception {
        var userId = UUID.randomUUID();
        var mockUser = new UserDTO();
        mockUser.setId(userId);

        when(userService.findById(userId)).thenReturn(mockUser);


        mockMvc.perform(get("/users/" + userId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId.toString()));
    }

    @DisplayName("Should return 404 when trying to get non-existing user")
    @Test
    void getUserByIdNotFound() throws Exception {
        var userId = UUID.randomUUID();
        var mockUser = new UserDTO();
        mockUser.setId(userId);

        doThrow(new NotFoundException("User: " + userId)).when(userService).findById(userId);

        mockMvc.perform(get("/users/" + userId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: User: " + userId));
    }

    @DisplayName("Should return a list of users with 200 response code")
    @Test
    void getUsersSuccess() throws Exception {
        UUID userId = UUID.randomUUID();
        BaseUserDTO mockCourt = new BaseUserDTO();
        mockCourt.setId(userId);
        Page<BaseUserDTO> userList = new PageImpl<>(List.of(mockCourt));
        when(userService.findAllBy(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(userList);

        mockMvc.perform(get("/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.baseUserDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.baseUserDTOList[0].id").value(userId.toString()));
    }

    @DisplayName("Should return a 404 when searching by a court that doesn't exist")
    @Test
    void getUsersCourtNotFound() throws Exception {
        UUID courtId = UUID.randomUUID();
        doThrow(new NotFoundException("Court: " + courtId))
            .when(userService)
            .findAllBy(isNull(), isNull(), isNull(), isNull(), eq(courtId), isNull(), isNull(), any());

        mockMvc.perform(get("/users")
                            .param("courtId", courtId.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Court: " + courtId));
    }

    @DisplayName("Should return a 404 when searching by a role that doesn't exist")
    @Test
    void getUsersRoleNotFound() throws Exception {
        UUID roleId = UUID.randomUUID();
        doThrow(new NotFoundException("Role: " + roleId))
            .when(userService)
            .findAllBy(any(), any(), any(), any(), any(), eq(roleId), any(), any());

        mockMvc.perform(get("/users")
                            .param("roleId", roleId.toString()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Role: " + roleId));
    }

    @DisplayName("Should delete user with 200 response code")
    @Test
    void deleteUserByIdSuccess() throws Exception {
        var userId = UUID.randomUUID();
        doNothing().when(userService).deleteById(userId);

        mockMvc.perform(delete("/users/" + userId)
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should return 404 when user doesn't exist")
    @Test
    void deleteUserByIdNotFound() throws Exception {
        var userId = UUID.randomUUID();
        doThrow(new NotFoundException("User: " + userId))
            .when(userService)
            .deleteById(userId);

        mockMvc.perform(delete("/users/" + userId)
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: User: " + userId));
    }

    @DisplayName("Should create a user with 201 response code")
    @Test
    void createUserCreated() throws Exception {
        var userId = UUID.randomUUID();
        var user =  new CreateUserDTO();
        user.setId(userId);

        when(userService.upsert(user)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/users/" + userId
        );
    }

    @DisplayName("Should update a user with 204 response code")
    @Test
    void updateUserNoContent() throws Exception {
        var userId = UUID.randomUUID();
        var user =  new CreateUserDTO();
        user.setId(userId);

        when(userService.upsert(user)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/users/" + userId
        );
    }

    @DisplayName("Should fail to create/update a user with 400 response code userId mismatch")
    @Test
    void createUserIdMismatch() throws Exception {
        var userId = UUID.randomUUID();
        var user =  new CreateUserDTO();
        user.setId(userId);

        MvcResult response = mockMvc.perform(put("/users/" + UUID.randomUUID())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path userId does not match payload property createUserDTO.userId\"}");
    }

    @DisplayName("Should fail to create/update a user with 404 response code when user has been deleted")
    @Test
    void createUserDeletedBadRequest() throws Exception {
        var userId = UUID.randomUUID();
        var user =  new CreateUserDTO();
        user.setId(userId);

        doThrow(new ResourceInDeletedStateException("UserDTO", user.getId().toString()))
            .when(userService).upsert(any());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"message\":\"Resource UserDTO(" + userId + ") is in a deleted state and cannot be updated\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 404 response code when court cannot be found")
    @Test
    void createUserCourtNotFound() throws Exception {
        var userId = UUID.randomUUID();
        var courtId = UUID.randomUUID();
        var user =  new CreateUserDTO();
        user.setId(userId);
        user.setCourtId(courtId);

        doThrow(new NotFoundException("Court: " + courtId)).when(userService).upsert(any());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Court: " + courtId + "\"}");
    }

    @DisplayName("Should fail to create/update a user with 404 response code when role cannot be found")
    @Test
    void createUserRoleNotFound() throws Exception {
        var userId = UUID.randomUUID();
        var roleId = UUID.randomUUID();
        var user =  new CreateUserDTO();
        user.setId(userId);
        user.setRoleId(roleId);

        doThrow(new NotFoundException("Role: " + roleId)).when(userService).upsert(any());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Role: " + roleId + "\"}");
    }

    @DisplayName("Should return 400 when user id is not a uuid")
    @Test
    void testFindByIdBadRequest() throws Exception {
        mockMvc.perform(get("/users/12345678")
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Invalid UUID string: 12345678"));
    }

    @DisplayName("Should get user's app access details by email with 200 response code")
    @Test
    void getUserByEmailSuccess() throws Exception {
        var userEmail = "example@example.com";
        var mock = new AppAccessDTO();
        mock.setId(UUID.randomUUID());

        when(userService.findByEmail(userEmail)).thenReturn(List.of(mock));

        mockMvc.perform(get("/users/by-email/" + userEmail))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value(mock.getId().toString()));
    }

    @DisplayName("Should return 404 when user's app access details by email that does not have any app access")
    @Test
    void getUserByEmailNotFound() throws Exception {
        var userEmail = "example@example.com";

        doThrow(new NotFoundException("User: " + userEmail)).when(userService).findByEmail(userEmail);

        mockMvc.perform(get("/users/by-email/" + userEmail))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: User: " + userEmail));
    }
}
