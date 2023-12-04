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
import uk.gov.hmcts.reform.preapi.controllers.CaseController;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.model.Case;
import uk.gov.hmcts.reform.preapi.repositories.CaseRepository;
import uk.gov.hmcts.reform.preapi.services.CaseService;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class CaseControllerTest {

    @Autowired
    private transient MockMvc mockMvc;

    @MockBean
    private CaseService caseService;

    @MockBean
    private CaseRepository caseRepository;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    private static final String CASES_ID_PATH = "/cases/{id}";


    @DisplayName("Should get case by ID with 200 response code")
    @Test
    void testGetCaseByIdSuccess() throws Exception {
        UUID caseId = UUID.randomUUID();
        Case mockCase = new Case();
        mockCase.setId(caseId);
        when(caseService.findById(caseId)).thenReturn(mockCase);

        mockMvc.perform(get(CASES_ID_PATH, caseId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(caseId.toString()));
    }

    @DisplayName("Should return 404 when trying to get non-existing case")
    @Test
    void testGetNonExistingCaseById() throws Exception {
        UUID caseId = UUID.randomUUID();
        when(caseService.findById(caseId)).thenReturn(null);

        mockMvc.perform(get(CASES_ID_PATH, caseId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Case: " + caseId));
    }

    @DisplayName("Should return 404 when trying to get a case that doesn't exist")
    @Test
    void testGetDeletedCaseById() throws Exception {
        UUID caseId = UUID.randomUUID();
        Case mockCase = new Case();
        mockCase.setId(caseId);
        mockCase.setDeletedAt(Timestamp.from(Instant.now()));
        when(caseService.findById(caseId)).thenReturn(mockCase);

        mockMvc.perform(get(CASES_ID_PATH, caseId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Case: " + caseId));
    }

    @DisplayName("Should get list of cases with 200 response code")
    @Test
    void testGetCases() throws Exception {
        String caseReference = "ABC123";
        UUID courtId = UUID.randomUUID();
        Case mockCase = new Case();
        mockCase.setId(UUID.randomUUID());
        List<Case> caseList = List.of(mockCase);
        when(caseService.searchBy(caseReference, courtId)).thenReturn(caseList);

        mockMvc.perform(get("/cases")
                            .param("reference", caseReference)
                            .param("courtId", courtId.toString()))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$[0].id").exists());
    }

    // TODO Search params

    @DisplayName("Should create case with 201 response code")
    @Test
    void testCreateCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        Case newCaseRequest = new Case();
        newCaseRequest.setId(caseId);

        when(caseService.findById(caseId)).thenReturn(newCaseRequest);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(newCaseRequest))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/cases/" + caseId);
    }

    @DisplayName("Should return 400 when creating case with path and payload mismatch")
    @Test
    void testCreateCasePathPayloadMismatch() throws Exception {
        UUID caseId = UUID.randomUUID();
        Case newCaseRequest = new Case();
        newCaseRequest.setId(UUID.randomUUID());

        mockMvc.perform(put(CASES_ID_PATH, caseId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newCaseRequest))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Path id does not match payload property newCaseRequest.id"));
    }

    @DisplayName("Should return 400 when creating case with court that does not exist")
    @Test
    void testCreateCaseCourtNotFound() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        Case newCaseRequest = new Case();
        newCaseRequest.setId(caseId);
        newCaseRequest.setCourtId(courtId);

        doThrow(new NotFoundException("Court: " + courtId)).when(caseService).create(newCaseRequest);


        mockMvc.perform(put(CASES_ID_PATH, caseId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newCaseRequest))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Court: " + courtId));
    }

    @DisplayName("Should return 409 when creating case with an id that is already in use")
    @Test
    void testCreateCaseAlreadyExists() throws Exception {
        UUID caseId = UUID.randomUUID();
        Case newCaseRequest = new Case();
        newCaseRequest.setId(caseId);

        doThrow(new ConflictException(caseId.toString())).when(caseService).create(any());

        mockMvc.perform(put(CASES_ID_PATH, caseId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newCaseRequest))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message")
                           .value("Put Conflict: " + caseId + " already exists"));
    }

    @DisplayName("Should delete case with 200 response code")
    @Test
    void testDeleteCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        doNothing().when(caseService).deleteById(caseId);

        mockMvc.perform(delete(CASES_ID_PATH, caseId)
                            .with(csrf()))
            .andExpect(status().isOk());
    }
}
