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
import uk.gov.hmcts.reform.preapi.controllers.CourtController;
import uk.gov.hmcts.reform.preapi.dto.CourtDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCourtDTO;
import uk.gov.hmcts.reform.preapi.enums.CourtType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CourtService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
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

    @MockitoBean
    private CourtService courtService;

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
        when(courtService.findAllBy(isNull(), isNull(), isNull(), isNull(), any()))
            .thenReturn(new PageImpl<>(courtDTOList));

        mockMvc.perform(get("/courts")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.courtDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.courtDTOList[0].id").value(courtId.toString()));
    }

    @DisplayName("Should create a court with 201 response code")
    @Test
    void createCourtCreated() throws Exception {
        var mockCourt = createMockCreateCourt();

        when(courtService.upsert(mockCourt)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(getPath(mockCourt.getId()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(mockCourt.getId())
        );
    }

    @DisplayName("Should update a court with 204 response code")
    @Test
    void updateCourtNoContent() throws Exception {
        var mockCourt = createMockCreateCourt();

        when(courtService.upsert(mockCourt)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(getPath(mockCourt.getId()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(mockCourt.getId())
        );
    }

    @DisplayName("Should fail to create/update a court with 400 response code id mismatch")
    @Test
    void createCourtIdMismatch() throws Exception {
        var mockCourt = createMockCreateCourt();

        MvcResult response = mockMvc.perform(put(getPath(UUID.randomUUID()))
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
        var mockCourt = createMockCreateCourt();

        doThrow(new NotFoundException("Region: " + mockCourt.getRegions().getFirst())).when(courtService).upsert(any());

        MvcResult response = mockMvc.perform(put(getPath(mockCourt.getId()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Region: " + mockCourt.getRegions().getFirst() + "\"}");
    }

    @DisplayName("Should fail to create/update a court with 404 response code when id is null")
    @Test
    void createCourtIdNullBadRequest() throws Exception {
        var mockCourt = createMockCreateCourt();
        mockCourt.setId(null);

        mockMvc.perform(put(getPath(UUID.randomUUID()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.id").value("id is required"));
    }

    @DisplayName("Should fail to create/update a court with 404 response code when name is null")
    @Test
    void createCourtNameNullBadRequest() throws Exception {
        var mockCourt = createMockCreateCourt();
        mockCourt.setName(null);

        mockMvc.perform(put(getPath(mockCourt.getId()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.name").value("name is required"));
    }

    @DisplayName("Should fail to create/update a court with 404 response code when court type is null")
    @Test
    void createCourtCourtTypeNullBadRequest() throws Exception {
        var mockCourt = createMockCreateCourt();
        mockCourt.setCourtType(null);

        mockMvc.perform(put(getPath(mockCourt.getId()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.courtType").value("court_type is required"));
    }

    @DisplayName("Should fail to create/update a court with 404 response code when location code is null")
    @Test
    void createCourtLocationCodeNullBadRequest() throws Exception {
        var mockCourt = createMockCreateCourt();
        mockCourt.setLocationCode(null);

        mockMvc.perform(put(getPath(mockCourt.getId()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.locationCode").value("location_code is required"));
    }

    @DisplayName("Should fail to create/update a court with 404 response code when regions is null")
    @Test
    void createCourtRegionsNullBadRequest() throws Exception {
        var mockCourt = createMockCreateCourt();
        mockCourt.setRegions(null);

        mockMvc.perform(put(getPath(mockCourt.getId()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.regions").value("regions is required and must contain at least 1"));
    }

    @DisplayName("Should fail to create/update a court with 404 response code when regions is empty")
    @Test
    void createCourtRegionsEmptyBadRequest() throws Exception {
        var mockCourt = createMockCreateCourt();
        mockCourt.setRegions(List.of());

        mockMvc.perform(put(getPath(mockCourt.getId()))
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(mockCourt))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.regions").value("must contain at least 1"));
    }

    @DisplayName("Should return 400 when court id is not a uuid")
    @Test
    void testFindByIdBadRequest() throws Exception {
        mockMvc.perform(get("/courts/12345678")
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Invalid UUID string: 12345678"));
    }

    private String getPath(UUID id) {
        return "/courts/" + id;
    }

    private CreateCourtDTO createMockCreateCourt() {
        var mockCourt = new CreateCourtDTO();
        mockCourt.setId(UUID.randomUUID());
        mockCourt.setCourtType(CourtType.CROWN);
        mockCourt.setName("Example court");
        mockCourt.setLocationCode("1234567890");
        mockCourt.setRegions(List.of(UUID.randomUUID()));
        return mockCourt;
    }
}
