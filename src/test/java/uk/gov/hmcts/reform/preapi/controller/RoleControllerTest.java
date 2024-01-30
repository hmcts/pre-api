package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.RoleController;
import uk.gov.hmcts.reform.preapi.dto.RoleDTO;
import uk.gov.hmcts.reform.preapi.security.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RoleService;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoleController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RoleControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private RoleService roleService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @DisplayName("Should get a list of roles with 200 response code")
    @Test
    void getRoles() throws Exception {
        var mockRole = new RoleDTO();
        mockRole.setId(UUID.randomUUID());
        mockRole.setName("Example role");
        mockRole.setPermissions(Set.of());

        when(roleService.getAllRoles()).thenReturn(List.of(mockRole));

        mockMvc.perform(get("/roles"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value(mockRole.getId().toString()))
            .andExpect(jsonPath("$[0].name").value(mockRole.getName()));

    }
}
