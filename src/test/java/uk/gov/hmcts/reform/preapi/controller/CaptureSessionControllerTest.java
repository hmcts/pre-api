package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.preapi.controllers.CaptureSessionController;
import uk.gov.hmcts.reform.preapi.dto.CaptureSessionDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.CaptureSessionService;

import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaptureSessionController.class)
@AutoConfigureMockMvc(addFilters = false)
public class CaptureSessionControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private CaptureSessionService captureSessionService;
    private static final String TEST_URL = "http://localhost";

    @DisplayName("Should get capture session by id with 200 response code")
    @Test
    void getCaptureSessionByIdSuccess() throws Exception {
        var id = UUID.randomUUID();
        var model = new CaptureSessionDTO();
        model.setId(id);
        when(captureSessionService.findById(id)).thenReturn(model);

        mockMvc.perform(get(TEST_URL + "/capture-sessions/" + id))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(id.toString()));
    }

    @DisplayName("Should return 404 when trying to get a non-existing capture session")
    @Test
    void getCaptureSessionByIdNotFound() throws Exception {
        var id = UUID.randomUUID();
        doThrow(new NotFoundException("CaptureSession: " + id)).when(captureSessionService).findById(id);

        mockMvc.perform(get(TEST_URL + "/capture-sessions/" + id))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: CaptureSession: " + id));
    }
}
