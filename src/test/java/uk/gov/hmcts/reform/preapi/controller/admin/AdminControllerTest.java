package uk.gov.hmcts.reform.preapi.controller.admin;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.admin.AdminController;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;
import uk.gov.hmcts.reform.preapi.services.admin.AdminService;

import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AdminController.class)
@AutoConfigureMockMvc(addFilters = false)
public class AdminControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private AdminService adminService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @DisplayName("Should return 200 response code if UUID exists in relevant database tables")
    @ParameterizedTest
    @EnumSource(AdminService.UuidTableType.class)
    void uuidExistsInTable(AdminService.UuidTableType type) throws Exception {
        UUID givenUuid = UUID.randomUUID();
        when(adminService.findUuidType(givenUuid)).thenReturn(type);

        mockMvc.perform(get("/admin/{id}", givenUuid))
            .andExpect(status().isOk())
            .andExpect(content().string("Uuid relates to a " + type));
    }

    @DisplayName("Should return 404 response code if UUID does not exist in relevant database tables")
    @Test
    void uuidDoesNotExistInDatabase() throws Exception {
        UUID givenUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174abc");
        var expectedResponse = givenUuid + " does not exist in any relevant table";

        when(adminService.findUuidType(givenUuid))
            .thenThrow(new NotFoundException(givenUuid + " does not exist in any relevant table"));

        mockMvc.perform(get("/admin/{id}", givenUuid))
            .andExpect(status().isNotFound())
            .andExpect(content().string(expectedResponse));
    }

    @DisplayName("Throws IllegalArgumentException if database returns unexpected table name")
    @Test
    void databaseReturnsUnexpectedTableName() throws Exception {
        UUID givenUuid = UUID.fromString("123e4567-e89b-12d3-a456-426614174999");
        when(adminService.findUuidType(givenUuid))
            .thenThrow(new IllegalArgumentException());

        mockMvc.perform(get("/admin/{id}", givenUuid))
            .andExpect(status().isInternalServerError());
    }

    @DisplayName("Returns 400 response code if the UUID is in an invalid format")
    @Test
    void givenUuidInInvalidFormat() throws Exception {
        String invalidUuid = "invalid";

        mockMvc.perform(get("/admin/{id}", invalidUuid))
            .andExpect(status().isBadRequest())
            .andExpect(content().json("{\"message\":\"Invalid UUID string: invalid\"}"));
    }
}
