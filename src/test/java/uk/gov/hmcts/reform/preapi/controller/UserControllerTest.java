package uk.gov.hmcts.reform.preapi.controller;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.UserController;
import uk.gov.hmcts.reform.preapi.dto.UserDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.UserService;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
        UserDTO mockCourt = new UserDTO();
        mockCourt.setId(userId);
        List<UserDTO> userList = List.of(mockCourt);
        when(userService.findAllBy(null, null, null, null, null, null)).thenReturn(userList);

        mockMvc.perform(get("/users"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isNotEmpty())
            .andExpect(jsonPath("$[0].id").value(userId.toString()));
    }

    @DisplayName("Should return a 404 when searching by a court that doesn't exist")
    @Test
    void getUsersCourtNotFound() throws Exception {
        UUID courtId = UUID.randomUUID();
        doThrow(new NotFoundException("Court: " + courtId))
            .when(userService)
            .findAllBy(null, null, null, null, courtId, null);

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
            .findAllBy(null, null, null, null, null, roleId);

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
}
