package uk.gov.hmcts.reform.preapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.UserController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchUsers;
import uk.gov.hmcts.reform.preapi.dto.AccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreatePortalAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateUserDTO;
import uk.gov.hmcts.reform.preapi.dto.RoleDTO;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseAppAccessDTO;
import uk.gov.hmcts.reform.preapi.dto.base.BaseUserDTO;
import uk.gov.hmcts.reform.preapi.entities.Role;
import uk.gov.hmcts.reform.preapi.enums.AccessStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.repositories.RoleRepository;
import uk.gov.hmcts.reform.preapi.security.authentication.UserAuthentication;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.services.UserService;
import uk.gov.hmcts.reform.preapi.util.HelperFactory;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.context.SecurityContextHolder.getContext;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.preapi.enums.RoleType.ROLE_LEVEL_1;
import static uk.gov.hmcts.reform.preapi.enums.RoleType.ROLE_SUPER_USER;
import static uk.gov.hmcts.reform.preapi.util.HelperFactory.getMockAuth;
import static uk.gov.hmcts.reform.preapi.util.HelperFactory.mockUserFromDatabase;

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

    private static final UUID level1UserId = UUID.randomUUID();
    private static final UUID superUserId = UUID.randomUUID();

    private static final UserAuthentication level1RequesterAuth = getMockAuth(ROLE_LEVEL_1, level1UserId);
    private static final UserDTO level1UserDTO = mockUserFromDatabase(ROLE_LEVEL_1, level1UserId);

    private static final UserAuthentication mockSuperUserAuth = getMockAuth(ROLE_SUPER_USER, superUserId);
    private static final UserDTO superUserInDb = mockUserFromDatabase(ROLE_SUPER_USER, superUserId);

    private static final Role mockSuperUserRole = mock(Role.class);
    private static final UUID mockLevel1RoleId = UUID.randomUUID();
    private static final Role mockLevel1Role = mock(Role.class);

    private CreateUserDTO sampleUserToUpdate;
    private UserDTO sampleUserFromDatabase;

    @BeforeAll
    static void setUp() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

    @BeforeEach
    void setUpMocks() {

        // Role mocks
        when(mockSuperUserRole.getId()).thenReturn(UUID.randomUUID());
        when(mockSuperUserRole.getName()).thenReturn("Super User");
        when(mockLevel1Role.getId()).thenReturn(mockLevel1RoleId);
        when(mockLevel1Role.getName()).thenReturn("Level 1");

        sampleUserToUpdate = HelperFactory.createUserWithAppAccess(UUID.randomUUID());
        sampleUserFromDatabase = mockUserFromDatabase(ROLE_LEVEL_1, sampleUserToUpdate.getId());

        // User service mocks
        when(userService.findById(superUserInDb.getId())).thenReturn(superUserInDb);
        when(userService.findById(level1UserId)).thenReturn(level1UserDTO);
        when(userService.findById(sampleUserToUpdate.getId())).thenReturn(sampleUserFromDatabase);

        when(userService.getRoleById(sampleUserToUpdate.getAppAccess().iterator().next().getRoleId()))
                 .thenReturn(mockLevel1Role);
        when(userService.getRoleById(mockLevel1RoleId)).thenReturn(mockLevel1Role);
        when(userService.getRoleById(level1UserDTO.getAppAccess().getFirst().getRole().getId()))
            .thenReturn(mockLevel1Role);
        when(userService.getRoleById(mockSuperUserRole.getId())).thenReturn(mockSuperUserRole);
        when(userService.getRoleById(superUserInDb.getAppAccess().getFirst().getRole().getId()))
            .thenReturn(mockSuperUserRole);
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
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andReturn();
    }

    @DisplayName("Should not display user's app access ID")
    @Test
    void doNotDisplayAppAccessIdForGetUser() throws Exception {
        UUID userId = UUID.randomUUID();
        UserDTO user = new UserDTO();
        user.setId(userId);

        CourtDTO court = new CourtDTO();
        court.setId(UUID.randomUUID());
        court.setName("Example Court");

        RoleDTO role = new RoleDTO();
        role.setId(UUID.randomUUID());
        role.setName("Example Role");

        BaseAppAccessDTO access1 = new BaseAppAccessDTO();
        access1.setId(UUID.randomUUID());
        access1.setCourt(court);
        access1.setRole(role);
        access1.setActive(true);

        BaseAppAccessDTO access2 = new BaseAppAccessDTO();
        access2.setId(UUID.randomUUID());
        access2.setCourt(court);
        access2.setRole(role);
        access2.setActive(true);

        user.setAppAccess(List.of(access1, access2));

        when(userService.findById(userId)).thenReturn(user);

        MvcResult result = mockMvc.perform(get("/users/" + userId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(userId.toString()))
            .andExpect(jsonPath("$.app_access[0].court.name").value("Example Court"))
            .andExpect(jsonPath("$.app_access[1].role.name").value("Example Role"))
            .andExpect(jsonPath("$.app_access[0].id").doesNotExist())
            .andExpect(jsonPath("$.app_access[1].id").doesNotExist())
            .andReturn();
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

    @DisplayName("Should return a list of users with 200 response code with empty params")
    @Test
    void getUsersSuccess() throws Exception {
        var userId = UUID.randomUUID();
        var mockCourt = new UserDTO();
        mockCourt.setId(userId);
        var userList = new PageImpl<>(List.of(mockCourt));

        when(userService.findAllBy(
            any(),
            any(),
            any()
        )).thenReturn(userList);

        mockMvc.perform(get("/users"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.userDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.userDTOList[0].id").value(userId.toString()));

        ArgumentCaptor<SearchUsers> paramsCaptor = ArgumentCaptor.forClass(SearchUsers.class);
        verify(userService, times(1)).findAllBy(
            paramsCaptor.capture(), any(), any());

        SearchUsers parameters = paramsCaptor.getValue();
        assertThat(parameters.getCourtId()).isNull();
        assertThat(parameters.getRoleId()).isNull();
        assertThat(parameters.getEmail()).isNull();
        assertThat(parameters.getFirstName()).isNull();
        assertThat(parameters.getLastName()).isNull();
        assertThat(parameters.getOrganisation()).isNull();
        assertThat(parameters.getIncludeDeleted()).isNull();
        assertThat(parameters.getAppActive()).isNull();
        assertThat(parameters.getAccessType()).isNull();
    }

    @DisplayName("Should return a 404 when searching by a court that doesn't exist")
    @Test
    void getUsersCourtNotFound() throws Exception {
        UUID courtId = UUID.randomUUID();
        doThrow(new NotFoundException("Court: " + courtId))
            .when(userService)
            .findAllBy(
                any(),
                any(),
                any()
            );

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
            .findAllBy(any(), any(), any());

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
        when(userService.upsert(any(CreateUserDTO.class))).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .with(request -> {
                                                     getContext()
                                                         .setAuthentication(level1RequesterAuth);
                                                     return request;
                                                 })
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location"))
            .isEqualTo(TEST_URL + "/users/" + sampleUserToUpdate.getId());
    }

    @DisplayName("Should update a user with 204 response code")
    @Test
    void updateUserNoContent() throws Exception {
        when(userService.upsert(any(CreateUserDTO.class))).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .with(request -> {
                                                     getContext()
                                                         .setAuthentication(level1RequesterAuth);
                                                     return request;
                                                 })
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location"))
            .isEqualTo(TEST_URL + "/users/" + sampleUserToUpdate.getId());
    }

    @DisplayName("Should fail to create/update a user with 400 response code userId mismatch")
    @Test
    void createUserIdMismatch() throws Exception {
        MvcResult response = mockMvc.perform(put("/users/" + UUID.randomUUID())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        doThrow(new ResourceInDeletedStateException("UserDTO", sampleUserToUpdate.getId().toString()))
            .when(userService).upsert((CreateUserDTO) any());

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .with(request -> {
                                                     getContext()
                                                         .setAuthentication(level1RequesterAuth);
                                                     return request;
                                                 })
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"message\":\"Resource UserDTO(" + sampleUserToUpdate.getId()
                    + ") is in a deleted state and cannot be updated\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user id is null")
    @Test
    void upsertUserIdNull() throws Exception {
        var userWithNoID = new CreateUserDTO();
        // no ID set
        userWithNoID.setFirstName("Example");
        userWithNoID.setLastName("Person");
        userWithNoID.setEmail("example@example.com");
        userWithNoID.setAppAccess(Set.of());
        userWithNoID.setPortalAccess(Set.of());

        MvcResult response = mockMvc.perform(put("/users/" + UUID.randomUUID())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(userWithNoID))
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
        sampleUserToUpdate.setFirstName(null);

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.setFirstName("");

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.setLastName(null);

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.setLastName("");
        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.setEmail(null);
        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.setAppAccess(null);

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess\":\"must not be null\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when user app access user id is null")
    @Test
    void upsertUserAppAccessUserIdNull() throws Exception {
        sampleUserToUpdate.getAppAccess().iterator()
            .forEachRemaining(access -> access.setUserId(null));

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.getAppAccess().iterator()
            .forEachRemaining(access -> access.setCourtId(null));


        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.getAppAccess().iterator()
            .forEachRemaining(access -> access.setRoleId(null));

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        var appAccess1 = createAppAccessDTO(true, sampleUserToUpdate.getId());
        var appAccess2 = createAppAccessDTO(false, sampleUserToUpdate.getId());
        appAccess2.setCourtId(appAccess1.getCourtId());
        sampleUserToUpdate.setAppAccess(Set.of(appAccess1, appAccess2));

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        var appAccess1 = createAppAccessDTO(false, sampleUserToUpdate.getId());
        sampleUserToUpdate.setAppAccess(Set.of(appAccess1));

        var response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo(
                "{\"appAccess\":\"must be empty or contain only one PRIMARY access and up to four SECONDARY access\"}"
            );
    }

    @DisplayName("Should fail to create/update a user with 400 when email is malformed")
    @Test
    void upsertUserEmailNotFormattedCorrectly() throws Exception {
        sampleUserToUpdate.setEmail("example not email");

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        sampleUserToUpdate.setPortalAccess(null);

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        var access = new CreatePortalAccessDTO();
        access.setStatus(AccessStatus.INACTIVE);
        access.setInvitedAt(Timestamp.from(Instant.now()));
        sampleUserToUpdate.setPortalAccess(Set.of(access));

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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
        CreatePortalAccessDTO access = new CreatePortalAccessDTO();
        access.setId(UUID.randomUUID());
        access.setInvitedAt(Timestamp.from(Instant.now()));
        sampleUserToUpdate.setPortalAccess(Set.of(access));

        MvcResult response = mockMvc.perform(put("/users/" + sampleUserToUpdate.getId())
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
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

    @DisplayName("Should pass through params as they are set")
    @Test
    public void testGetCasesIncludeDeletedNotSet() throws Exception {
        when(userService.findAllBy(
            any(),
            any(),
            any()
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        ArgumentCaptor<SearchUsers> searchUsersCaptor = ArgumentCaptor.forClass(SearchUsers.class);
        verify(userService, times(1))
            .findAllBy(
                searchUsersCaptor.capture(),
                any(),
                any()
            );
        assertThat(searchUsersCaptor.getValue().getIncludeDeleted()).isNull();
    }

    @DisplayName("Should set include deleted param to false when set to false")
    @Test
    public void testGetCasesIncludeDeletedFalse() throws Exception {
        SearchUsers searchUsers = mock(SearchUsers.class);
        when(searchUsers.getIncludeDeleted()).thenReturn(false);
        when(userService.findAllBy(
            any(),
            any(),
            any()
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users")
                            .with(csrf())
                            .param("includeDeleted", "false")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        ArgumentCaptor<SearchUsers> searchUsersCaptor = ArgumentCaptor.forClass(SearchUsers.class);
        verify(userService, times(1))
            .findAllBy(searchUsersCaptor.capture(), any(), any());
        assertThat(searchUsersCaptor.getValue().getIncludeDeleted()).isFalse();
    }

    @DisplayName("Should set include deleted param to true when set to true")
    @Test
    public void testGetCasesIncludeDeletedTrue() throws Exception {
        when(userService.findAllBy(
            any(),
            any(),
            any()
        )).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/users")
                            .with(csrf())
                            .param("includeDeleted", "true")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        ArgumentCaptor<SearchUsers> searchUsersCaptor = ArgumentCaptor.forClass(SearchUsers.class);
        verify(userService, times(1))
            .findAllBy(
                searchUsersCaptor.capture(),
                any(),
                any()
            );

        assertThat(searchUsersCaptor.getValue().getIncludeDeleted()).isTrue();
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

    @DisplayName("Should prevent Level 1 users from assigning superuser access to anyone, or editing superusers")
    @Test
    void upsertUserLevel1CannotEditOrAssignSuperUsers() throws Exception {
        // Test 1: Level 1 cannot edit user with existing superuser access
        CreateUserDTO superUserToBeUpserted = HelperFactory.createUserWithAppAccess(superUserId);
        mockMvc.perform(put("/users/" + superUserToBeUpserted.getId())
                            .with(csrf())
                            .with(request -> {
                                getContext()
                                    .setAuthentication(level1RequesterAuth);
                                return request;
                            })
                            .content(OBJECT_MAPPER.writeValueAsString(superUserToBeUpserted))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message")
                           .value("Level 1 users cannot edit Super Users"));

        // Test 2: Level 1 user cannot uplift Level 1 user to superuser access
        // Upserted user is an existing Level 1 user
        CreateUserDTO level1UserToBeUpserted = HelperFactory.createUserWithAppAccess(level1UserId);
        when(userService.findById(level1UserId)).thenReturn(level1UserDTO);

        // Change the role of the upserted user to superuser in the request body
        level1UserToBeUpserted.getAppAccess().iterator().next()
            .setRoleId(mockSuperUserRole.getId());

        mockMvc.perform(put("/users/" + level1UserToBeUpserted.getId())
                            .with(csrf())
                            .with(request -> {
                                getContext()
                                    .setAuthentication(level1RequesterAuth);
                                return request;
                            })
                            .content(OBJECT_MAPPER.writeValueAsString(level1UserToBeUpserted))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.message")
                           .value("Level 1 users cannot uplift to superuser access"));
    }

    @DisplayName("Should allow super users to assign superuser access to anyone and edit superusers")
    @Test
    void upsertSuperUserCanEditOrAssignSuperUsers() throws Exception {
        // Set up
        UserAuthentication superUserAuth = getMockAuth(ROLE_SUPER_USER, superUserId);

        when(userService.upsert(any(CreateUserDTO.class))).thenReturn(UpsertResult.UPDATED);

        // Test 1: Super user can edit user with existing superuser access
        CreateUserDTO superUserToBeUpserted = HelperFactory.createUserWithAppAccess(superUserId);
        mockMvc.perform(put("/users/" + superUserToBeUpserted.getId())
                            .with(csrf())
                            .with(request -> {
                                getContext()
                                    .setAuthentication(superUserAuth);
                                return request;
                            })
                            .content(OBJECT_MAPPER.writeValueAsString(superUserToBeUpserted))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is2xxSuccessful());

        // Test 2: Super user can uplift Level 1 user to superuser access
        // Upserted user is an existing Level 1 user
        CreateUserDTO level1UserToBeUpserted = HelperFactory.createUserWithAppAccess(level1UserId);
        when(userService.findById(level1UserId)).thenReturn(level1UserDTO);

        // Change the role of the upserted user to superuser in the request body
        level1UserToBeUpserted.getAppAccess().iterator().next()
            .setRoleId(mockSuperUserRole.getId());

        mockMvc.perform(put("/users/" + level1UserToBeUpserted.getId())
                            .with(csrf())
                            .with(request -> {
                                getContext()
                                    .setAuthentication(superUserAuth);
                                return request;
                            })
                            .content(OBJECT_MAPPER.writeValueAsString(level1UserToBeUpserted))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is2xxSuccessful());
    }

    @DisplayName("Should allow ROLE_SUPER_USER to assign any role including ROLE_SUPER_USER with 201 response code")
    @Test
    void upsertUserSuperUserCanAssignSuperUser() throws Exception {
        var userId = UUID.randomUUID();

        var user = new CreateUserDTO();
        user.setId(userId);
        user.setFirstName("Example");
        user.setLastName("Person");
        user.setEmail("example@example.com");

        var superUserRoleId = UUID.randomUUID();
        var appAccess = new CreateAppAccessDTO();
        appAccess.setId(UUID.randomUUID());
        appAccess.setUserId(userId);
        appAccess.setCourtId(UUID.randomUUID());
        appAccess.setRoleId(superUserRoleId);
        appAccess.setDefaultCourt(true);

        user.setAppAccess(Set.of(appAccess));
        user.setPortalAccess(Set.of());

        when(userService.upsert(any(CreateUserDTO.class))).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put("/users/" + userId)
                                                 .with(csrf())
                                                 .with(request -> {
                                                     getContext().setAuthentication(mockSuperUserAuth);
                                                     return request;
                                                 })
                                                 .content(OBJECT_MAPPER.writeValueAsString(user))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location"))
            .isEqualTo(TEST_URL + "/users/" + userId);
    }

    @Test
    @DisplayName("Should be able to reset app access IDs for any user")
    void canResetAppAccessIDs() throws Exception {
        UUID randomUserId = UUID.randomUUID();
        mockMvc.perform(put("/users/reset-app-access-ids/" + randomUserId)
                                                 .with(csrf())
                                                 .with(request -> {
                                                     getContext().setAuthentication(mockSuperUserAuth);
                                                     return request;
                                                 })
                                                 .content(OBJECT_MAPPER.writeValueAsString(sampleUserToUpdate))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().string(""))
            .andReturn();

        ArgumentCaptor<UUID> captor = ArgumentCaptor.forClass(UUID.class);
        verify(userService, times(1)).resetAppAccessIdsForUserId(captor.capture());
        assertThat(captor.getValue()).isEqualTo(randomUserId);
    }
}
