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
import uk.gov.hmcts.reform.preapi.controllers.CourtController;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.CourtService;

import java.util.List;
import java.util.UUID;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CourtController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
public class CourtControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private CourtService courtService;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    @DisplayName("Should get court by id with 200 response code")
    @Test
    void getCourtByIdSuccess() throws Exception {
        var courtId = UUID.randomUUID();
        var mockCourt = new CourtDTO();
        mockCourt.setId(courtId);
        when(courtService.findById(courtId)).thenReturn(mockCourt);

        mockMvc.perform(get(TEST_URL + "/courts/" + courtId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(courtId.toString()));
    }

    @DisplayName("Should return 404 when trying to get non-existing court")
    @Test
    void getNonExistingCourtById() throws Exception {
        var courtId = UUID.randomUUID();
        doThrow(new NotFoundException("Court: " + courtId)).when(courtService).findById(courtId);


        mockMvc.perform(get("/courts/" + courtId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Court: " + courtId));
    }

    @DisplayName("Should get a list of court with 200 response code")
    @Test
    void getAllCourtsSuccess() throws Exception {
        UUID courtId = UUID.randomUUID();
        CourtDTO mockCourt = new CourtDTO();
        mockCourt.setId(courtId);
        List<CourtDTO> courtDTOList = List.of(mockCourt);
        when(courtService.findAllBy(null, null, null, null)).thenReturn(courtDTOList);

        mockMvc.perform(get("/courts"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$").isNotEmpty())
            .andExpect(jsonPath("$[0].id").value(courtId.toString()));
    }
}
