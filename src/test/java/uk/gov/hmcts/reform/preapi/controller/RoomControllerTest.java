package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.RoomController;
import uk.gov.hmcts.reform.preapi.dto.RoomDTO;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RoomController.class)
@AutoConfigureMockMvc(addFilters = false)
public class RoomControllerTest {

    @Autowired
    private transient MockMvc mockMvc;
    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    @DisplayName("Should get a list of rooms with 200 response code")
    @Test
    void getRooms() throws Exception {
        var room = new RoomDTO();
        room.setId(UUID.randomUUID());
        room.setName("Example Room");

        mockMvc.perform(get("/rooms"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON));
    }
}
