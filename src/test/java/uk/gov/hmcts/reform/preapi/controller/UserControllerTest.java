package uk.gov.hmcts.reform.preapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.UserController;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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

    @MockitoBean
    private UserService userService;

    @MockitoBean
    private RoleRepository roleRepository;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    @BeforeAll
    static void setUp() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

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
        var userId = UUID.randomUUID();
        var mockCourt = new UserDTO();
        mockCourt.setId(userId);
        var userList = new PageImpl<>(List.of(mockCourt));
        when(userService.findAllBy(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(false),
            isNull(),
            any()
        )).thenReturn(userList);

        mockMvc.perform(get("/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.userDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.userDTOList[0].id").value(userId.toString()));

        verify(userService, times(1)).findAllBy(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            eq(false),
            isNull(),
            any()
        );
    }

    @DisplayName("Should return a 404 when searching by a court that doesn't exist")
    @Test
    void getUsersCourtNotFound() throws Exception {
        UUID courtId = UUID.randomUUID();
        doThrow(new NotFoundException("Court: " + courtId))
            .when(userService)
            .findAllBy(isNull(), isNull(), isNull(), eq(courtId), isNull(), isNull(), eq(false), isNull(), any());

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
            .findAllBy(any(), any(), any(), any(), eq(roleId), any(), eq(false), any(), any());

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
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

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
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

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
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + UUID.randomUUID())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path userId does not match payload property createUserDTO.id\"}");
    }

    @DisplayName("Should fail to create/update a user with 404 response code when user has been deleted")
    @Test
    void createUserDeletedBadRequest() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        doThrow(new ResourceInDeletedStateException("UserDTO", user.getId().toString()))
            .when(userService).upsert((CreateUserDTO) any());

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

    @DisplayName("Should fail to create/update a user with 400 when user id is null")
    @Test
    void upsertUserIdNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"id\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user first name is null")
    @Test
    void upsertUserFirstNameNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"firstName\":\"must not be blank\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user first name is blank")
    @Test
    void upsertUserFirstNameBlank() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"firstName\":\"must not be blank\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user last name is null")
    @Test
    void upsertUserLastNameNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"lastName\":\"must not be blank\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user last name is blank")
    @Test
    void upsertUserLastNameBlank() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"lastName\":\"must not be blank\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user email is null")
    @Test
    void upsertUserEmailNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"email\":\"must not be blank\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user first name is blank")
    @Test
    void upsertUserEmailBlank() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"email\":\"must not be blank\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user app access is null")
    @Test
    void upsertUserAppAccessNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user app access id is null")
    @Test
    void upsertUserAppAccessIdNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        var appAccess = new CreateAppAccessDTO();
        appAccess.setUserId(UUID.randomUUID());
        appAccess.setCourtId(UUID.randomUUID());
        appAccess.setRoleId(UUID.randomUUID());
        appAccess.setDefaultCourt(true);
        user.setAppAccess(Set.of(appAccess));
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess[].id\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user app access user id is null")
    @Test
    void upsertUserAppAccessUserIdNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        var appAccess = new CreateAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        appAccess.setCourtId(UUID.randomUUID());
        appAccess.setRoleId(UUID.randomUUID());
        appAccess.setDefaultCourt(true);
        user.setAppAccess(Set.of(appAccess));
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess[].userId\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user app access court id is null")
    @Test
    void upsertUserAppAccessCourtIdNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        var appAccess = new CreateAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUserId(UUID.randomUUID());
        appAccess.setRoleId(UUID.randomUUID());
        appAccess.setDefaultCourt(true);
        user.setAppAccess(Set.of(appAccess));
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess[].courtId\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user app access role id is null")
    @Test
    void upsertUserAppAccessRoleIdNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        var appAccess = new CreateAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUserId(UUID.randomUUID());
        appAccess.setCourtId(UUID.randomUUID());
        appAccess.setDefaultCourt(true);
        user.setAppAccess(Set.of(appAccess));
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess[].roleId\":\"must not be null\"}"
            );
    }

    /*
    TODO Uncomment this when court access type is made required
    @DisplayName("Should fail to create/update a user with 400 when user app access court access type is null")
    @Test
    void upsertUserAppAccessCourtAccessTypeNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        var appAccess = new CreateAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUserId(UUID.randomUUID());
        appAccess.setCourtId(UUID.randomUUID());
        user.setAppAccess(Set.of(appAccess));
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess[].courtAccessType\":\"must not be null\"}"
            );
    }
     */

    @DisplayName("Should fail to create/update a user with 400 when app access doesnt meet NoDuplicateCourtsConstraint")
    @Test
    void upsertUserAppAccessNoDuplicateCourtsConstraint() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setPortalAccess(Set.of());
        var appAccess1 = createAppAccessDTO(true, userId);
        var appAccess2 = createAppAccessDTO(false, userId);
        appAccess2.setCourtId(appAccess1.getCourtId());
        user.setAppAccess(Set.of(appAccess1, appAccess2));

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess\":\"must not contain duplicate accesses to a court\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when app access doesnt meet PortalAppAccessConstraint")
    @Test
    void upsertUserAppAccessPortalAppAccessConstraint() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setPortalAccess(Set.of());
        var appAccess1 = createAppAccessDTO(true, userId);
        var appAccess2 = createAppAccessDTO(false, userId);
        user.setAppAccess(Set.of(appAccess1, appAccess2));

        var rolePortal = new Role();
        rolePortal.setName("Level 3");

        when(roleRepository.findById(appAccess1.getRoleId())).thenReturn(Optional.of(rolePortal));
        when(roleRepository.findById(appAccess2.getRoleId())).thenReturn(Optional.of(rolePortal));

        var response = mockMvc.perform(put("/users/" + userId)
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(user))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess\":\"must not have portal access role if you have secondary courts\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when app access doesnt meet PrimaryCourtConstraint")
    @Test
    void upsertUserAppAccessPrimaryCourtConstraint() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setPortalAccess(Set.of());
        var appAccess1 = createAppAccessDTO(false, userId);
        user.setAppAccess(Set.of(appAccess1));

        var response = mockMvc.perform(put("/users/" + userId)
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(user))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess\":\"must be empty or contain only one PRIMARY access and up to four SECONDARY access\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user first name is blank")
    @Test
    void upsertUserEmailNotFormattedCorrectly() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example not email");
        user.setAppAccess(Set.of());
        user.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"email\":\"must be a well-formed email address\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user portal access is null")
    @Test
    void upsertUserPortalAccessNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"portalAccess\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user portal access id is null")
    @Test
    void upsertUserPortalAccessIdNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        var access = new CreatePortalAccessDTO();
        access.setStatus(AccessStatus.INACTIVE);
        access.setInvitedAt(Timestamp.from(Instant.now()));
        user.setPortalAccess(Set.of(access));

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"portalAccess[].id\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user portal access status is null")
    @Test
    void upsertUserPortalAccessStatusNull() throws Exception {
        var userId = UUID.randomUUID();
        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");
        user.setAppAccess(Set.of());
        var access = new CreatePortalAccessDTO();
        access.setId(UUID.randomUUID());
        access.setInvitedAt(Timestamp.from(Instant.now()));
        user.setPortalAccess(Set.of(access));

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"portalAccess[].status\":\"must not be null\"}"
            );
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
        var mock = new AccessDTO();
        var mockUser = new BaseUserDTO();
        mockUser.setId(UUID.randomUUID());
        mock.setUser(mockUser);

        when(userService.findByEmail(userEmail)).thenReturn(mock);

        mockMvc.perform(get("/users/by-email/" + userEmail))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.user.id").value(mock.getUser().getId().toString()));
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

    @DisplayName("Should set include deleted param to false if not set")
    @Test
    public void testGetCasesIncludeDeletedNotSet() throws Exception {
        when(userService.findAllBy(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            anyBoolean(),
            isNull(),
            any()
        ))
            .thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(userService, times(1))
            .findAllBy(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), any());
    }

    @DisplayName("Should set include deleted param to false when set to false")
    @Test
    public void testGetCasesIncludeDeletedFalse() throws Exception {
        when(userService.findAllBy(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            anyBoolean(),
            isNull(),
            any()
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users")
                            .with(csrf())
                            .param("includeDeleted", "false")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(userService, times(1))
            .findAllBy(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(false), isNull(), any());
    }

    @DisplayName("Should set include deleted param to true when set to true")
    @Test
    public void testGetCasesIncludeDeletedTrue() throws Exception {
        when(userService.findAllBy(
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            isNull(),
            anyBoolean(),
            isNull(),
            any()
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users")
                            .with(csrf())
                            .param("includeDeleted", "true")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(userService, times(1))
            .findAllBy(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), eq(true), isNull(), any());
    }

    @DisplayName("Should undelete a user by id and return a 200 response")
    @Test
    void undeleteRecordingSuccess() throws Exception {
        var userId = UUID.randomUUID();
        doNothing().when(userService).undelete(userId);

        mockMvc.perform(post("/users/" + userId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should undelete a user by id and return a 404 response")
    @Test
    void undeleteRecordingNotFound() throws Exception {
        var userId = UUID.randomUUID();
        doThrow(
            new NotFoundException("User: " + userId)
        ).when(userService).undelete(userId);

        mockMvc.perform(post("/users/" + userId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: User: " + userId));
    }

    private CreateAppAccessDTO createAppAccessDTO(boolean isDefaultCourt, UUID userId) {
        var appAccess = new CreateAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUserId(userId);
        appAccess.setCourtId(UUID.randomUUID());
        appAccess.setRoleId(UUID.randomUUID());
        appAccess.setDefaultCourt(isDefaultCourt);
        return appAccess;
    }
}
