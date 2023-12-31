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
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.CourtController;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.services.CourtService;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

    @DisplayName("Should create a court with 201 response code")
    @Test
    void createCourtCreated() throws Exception {
        var courtId = UUID.randomUUID();
        var mockCourt = new CreateCourtDTO();
        mockCourt.setId(courtId);

        when(courtService.upsert(mockCourt)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(getPath(courtId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(courtId)
        );
    }

    @DisplayName("Should update a court with 204 response code")
    @Test
    void updateCourtNoContent() throws Exception {
        var courtId = UUID.randomUUID();
        var mockCourt = new CreateCourtDTO();
        mockCourt.setId(courtId);

        when(courtService.upsert(mockCourt)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(getPath(courtId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(courtId)
        );
    }

    @DisplayName("Should fail to create/update a court with 400 response code id mismatch")
    @Test
    void createCourtIdMismatch() throws Exception {
        var courtId = UUID.randomUUID();
        var mockCourt = new CreateCourtDTO();
        mockCourt.setId(UUID.randomUUID());

        MvcResult response = mockMvc.perform(put(getPath(courtId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path courtId does not match payload property createCourtDTO.id\"}");
    }

    @DisplayName("Should fail to create/update a court with 404 response code when region does not exist")
    @Test
    void updateCourtRegionNotFound() throws Exception {
        var courtId = UUID.randomUUID();
        var regionId = UUID.randomUUID();
        var mockCourt = new CreateCourtDTO();
        mockCourt.setId(courtId);
        mockCourt.setRegions(List.of(regionId));

        doThrow(new NotFoundException("Region: " + regionId)).when(courtService).upsert(any());

        MvcResult response = mockMvc.perform(put(getPath(courtId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Region: " + regionId + "\"}");
    }

    @DisplayName("Should fail to create/update a court with 404 response code when room does not exist")
    @Test
    void updateCourtRoomNotFound() throws Exception {
        var courtId = UUID.randomUUID();
        var roomId = UUID.randomUUID();
        var mockCourt = new CreateCourtDTO();
        mockCourt.setId(courtId);
        mockCourt.setRooms(List.of(roomId));

        doThrow(new NotFoundException("Room: " + roomId)).when(courtService).upsert(any());

        MvcResult response = mockMvc.perform(put(getPath(courtId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Room: " + roomId + "\"}");
    }

    private String getPath(UUID id) {
        return "/courts/" + id;
    }
}
