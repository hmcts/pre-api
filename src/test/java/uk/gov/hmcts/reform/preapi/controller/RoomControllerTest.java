package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.RoomController;
import uk.gov.hmcts.reform.preapi.dto.RoomDTO;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RoomService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RoomControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private RoomService roleService;

    @MockBean
    private UserAuthenticationService userAuthenticationService;

    @MockBean
    private ScheduledTaskRunner taskRunner;

    @DisplayName("Should get a list of rooms with 200 response code")
    @Test
    void getRooms() throws Exception {
        var room = new RoomDTO();
        room.setId(UUID.randomUUID());
        room.setName("Example Room");

        when(roleService.getAllRooms(null)).thenReturn(List.of(room));

        mockMvc.perform(get("/rooms"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").value(room.getId().toString()))
            .andExpect(jsonPath("$[0].name").value(room.getName()));
    }
}
