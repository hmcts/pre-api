package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.CaseController;
import uk.gov.hmcts.reform.preapi.dto.CaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateCaseDTO;
import uk.gov.hmcts.reform.preapi.dto.CreateParticipantDTO;
import uk.gov.hmcts.reform.preapi.enums.CaseState;
import uk.gov.hmcts.reform.preapi.enums.ParticipantType;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.ConflictException;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.RecordingNotDeletedException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.CaseService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CaseController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class CaseControllerTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";
    private static final String CASES_ID_PATH = "/cases/{id}";

    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private CaseService caseService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    @BeforeAll
    static void setUp() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
    }

    @DisplayName("Should get case by ID with 200 response code")
    @Test
    void testGetCaseByIdSuccess() throws Exception {
        UUID caseId = UUID.randomUUID();
        CaseDTO mockCaseDTO = new CaseDTO();
        mockCaseDTO.setId(caseId);
        when(caseService.findById(caseId)).thenReturn(mockCaseDTO);

        mockMvc.perform(get(CASES_ID_PATH, caseId))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(caseId.toString()));
    }

    @DisplayName("Should return 404 when trying to get non-existing case")
    @Test
    void testGetNonExistingCaseById() throws Exception {
        UUID caseId = UUID.randomUUID();
        doThrow(new NotFoundException("Case: " + caseId)).when(caseService).findById(any());

        mockMvc.perform(get(CASES_ID_PATH, caseId))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Case: " + caseId));
    }

    @DisplayName("Should get list of cases with 200 response code")
    @Test
    void testGetCases() throws Exception {
        String caseReference = "ABC123";
        UUID courtId = UUID.randomUUID();
        CaseDTO mockCaseDTO = new CaseDTO();
        mockCaseDTO.setId(UUID.randomUUID());
        Page<CaseDTO> caseDTOList = new PageImpl<>(List.of(mockCaseDTO));
        when(caseService.searchBy(eq(caseReference), eq(courtId), eq(false), any())).thenReturn(caseDTOList);

        mockMvc.perform(get("/cases")
                            .param("reference", caseReference)
                            .param("courtId", courtId.toString())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$._embedded.caseDTOList[0].id").exists());

        verify(caseService, times(1)).searchBy(eq(caseReference), eq(courtId), eq(false), any());
    }

    @DisplayName("Should create case with 201 response code")
    @Test
    void testCreateCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference("TestCase1");
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );


        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/cases/" + caseId);
    }

    @DisplayName("Should fail create/update case with 400 error message when state = OPEN and closedAt not null")
    @Test
    void createStateOpenClosedAtNotNullBadRequest() throws Exception {
        var caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference("TestCase123");
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );
        caseDTO.setState(CaseState.OPEN);
        caseDTO.setClosedAt(Timestamp.from(Instant.now()));

        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"closedAt\":\"must be null when case state is OPEN\"}");
    }

    @DisplayName("Should fail create/update case with 400 error message when state = CLOSED and closedAt null")
    @Test
    void createStateClosedClosedAtNullBadRequest() throws Exception {
        var caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference("TestCase123");
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );
        caseDTO.setState(CaseState.CLOSED);
        caseDTO.setClosedAt(null);

        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"closedAt\":\"must not be null when case state is CLOSED\"}");
    }

    @DisplayName("Should fail create/update case with 400 error message when state = CLOSED and closedAt null")
    @Test
    void createStateClosedClosedAtFutureBadRequest() throws Exception {
        var caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference("TestCase123");
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );
        caseDTO.setState(CaseState.CLOSED);
        caseDTO.setClosedAt(Timestamp.from(Instant.now().plusSeconds(86400)));

        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"closedAt\":\"must not be in the future when case state is CLOSED\"}");
    }

    @DisplayName("Should fail create/update case with 400 error message when case reference is too short")
    @Test
    void testCreateCaseReferenceTooShortBadRequest() throws Exception {
        UUID caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference("TestCase");
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );

        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"reference\":\"size must be between 9 and 13\"}");
    }

    @DisplayName("Should fail create/update case with 400 error message when case reference is too long")
    @Test
    void testCreateCaseReferenceTooLongBadRequest() throws Exception {
        UUID caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference("TestCase123456");
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );

        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"reference\":\"size must be between 9 and 13\"}");
    }

    @DisplayName("Should fail create/update case with 400 error message when case reference is null")
    @Test
    void testCreateCaseReferenceNullRequest() throws Exception {
        UUID caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference(null);
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );

        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"reference\":\"must not be null\"}");
    }

    @DisplayName("Should update case with 204 response code")
    @Test
    void testUpdateCase() throws Exception {
        UUID caseId = UUID.randomUUID();
        var caseDTO = new CreateCaseDTO();
        caseDTO.setId(caseId);
        caseDTO.setReference("EXAMPLE123");
        caseDTO.setParticipants(Set.of(
                                    createParticipant(ParticipantType.WITNESS),
                                    createParticipant(ParticipantType.DEFENDANT)
                                )
        );

        when(caseService.upsert(caseDTO)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(caseDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + "/cases/" + caseId);
    }

    @DisplayName("Should return 400 when creating case with path and payload mismatch")
    @Test
    void testCreateCasePathPayloadMismatch() throws Exception {
        var newCaseRequestDTO = new CreateCaseDTO();
        newCaseRequestDTO.setId(UUID.randomUUID());
        newCaseRequestDTO.setReference("EXAMPLE123");
        newCaseRequestDTO.setParticipants(Set.of(
                                              createParticipant(ParticipantType.WITNESS),
                                              createParticipant(ParticipantType.DEFENDANT)
                                          )
        );

        mockMvc.perform(put(CASES_ID_PATH, UUID.randomUUID())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newCaseRequestDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Path id does not match payload property createCaseDTO.id"));
    }

    @DisplayName("Should return 400 when participants does not contain a witness")
    @Test
    void createCaseNoWitnessParticipantBadRequest() throws Exception {
        var caseId = UUID.randomUUID();
        var courtId = UUID.randomUUID();
        var newCaseRequestDTO = new CreateCaseDTO();
        newCaseRequestDTO.setId(caseId);
        newCaseRequestDTO.setCourtId(courtId);
        newCaseRequestDTO.setReference("EXAMPLE123");
        newCaseRequestDTO.setParticipants(Set.of(
            createParticipant(ParticipantType.DEFENDANT))
        );

        mockMvc.perform(put(CASES_ID_PATH, caseId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newCaseRequestDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.participants")
                           .value("Participants must consist of at least 1 defendant and 1 witness"));
    }

    @DisplayName("Should return 400 when participants does not contain a defendant")
    @Test
    void createCaseNoDefendantParticipantBadRequest() throws Exception {
        var caseId = UUID.randomUUID();
        var courtId = UUID.randomUUID();
        var newCaseRequestDTO = new CreateCaseDTO();
        newCaseRequestDTO.setId(caseId);
        newCaseRequestDTO.setCourtId(courtId);
        newCaseRequestDTO.setReference("EXAMPLE123");
        newCaseRequestDTO.setParticipants(Set.of(
            createParticipant(ParticipantType.WITNESS))
        );

        mockMvc.perform(put(CASES_ID_PATH, caseId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newCaseRequestDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.participants")
                           .value("Participants must consist of at least 1 defendant and 1 witness"));
    }

    @DisplayName("Should return 400 when creating case with court that does not exist")
    @Test
    void testCreateCaseCourtNotFound() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        var newCaseRequestDTO = new CreateCaseDTO();
        newCaseRequestDTO.setId(caseId);
        newCaseRequestDTO.setCourtId(courtId);
        newCaseRequestDTO.setReference("EXAMPLE123");
        newCaseRequestDTO.setParticipants(Set.of(
                                              createParticipant(ParticipantType.WITNESS),
                                              createParticipant(ParticipantType.DEFENDANT)
                                          )
        );

        doThrow(new NotFoundException("Court: " + courtId)).when(caseService).upsert(newCaseRequestDTO);


        mockMvc.perform(put(CASES_ID_PATH, caseId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(newCaseRequestDTO))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Court: " + courtId));
    }

    @DisplayName("Should return 400 when case has been marked as deleted")
    @Test
    void updateCaseBadRequest() throws Exception {
        UUID caseId = UUID.randomUUID();
        UUID courtId = UUID.randomUUID();
        var newCaseRequestDTO = new CreateCaseDTO();
        newCaseRequestDTO.setId(caseId);
        newCaseRequestDTO.setCourtId(courtId);
        newCaseRequestDTO.setReference("EXAMPLE123");
        newCaseRequestDTO.setParticipants(Set.of(
                                              createParticipant(ParticipantType.WITNESS),
                                              createParticipant(ParticipantType.DEFENDANT)
                                          )
        );


        doThrow(new ResourceInDeletedStateException("CaseDTO", caseId.toString()))
            .when(caseService).upsert(newCaseRequestDTO);

        MvcResult response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(newCaseRequestDTO))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Resource CaseDTO("
                           + caseId + ") is in a deleted state and cannot be updated\"}"
            );
    }

    @DisplayName("Should return 400 when case reference is already in use in the specified court")
    @Test
    void updateCaseReferenceCourtIdAlreadyExistBadRequest() throws Exception {
        var caseId = UUID.randomUUID();
        var courtId = UUID.randomUUID();
        var newCaseRequestDTO = new CreateCaseDTO();
        newCaseRequestDTO.setId(caseId);
        newCaseRequestDTO.setCourtId(courtId);
        newCaseRequestDTO.setReference("EXAMPLE123");
        newCaseRequestDTO.setParticipants(Set.of(
                                              createParticipant(ParticipantType.WITNESS),
                                              createParticipant(ParticipantType.DEFENDANT)
                                          )
        );

        doThrow(new ConflictException("Case reference is already in use for this court"))
            .when(caseService).upsert(newCaseRequestDTO);

        var response = mockMvc.perform(put(CASES_ID_PATH, caseId)
                                           .with(csrf())
                                           .content(OBJECT_MAPPER.writeValueAsString(newCaseRequestDTO))
                                           .contentType(MediaType.APPLICATION_JSON_VALUE)
                                           .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isConflict())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Conflict: Case reference is already in use for this court\"}");
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

    @DisplayName("Should return 400 when case id is not a uuid")
    @Test
    void testFindByIdBadRequest() throws Exception {
        mockMvc.perform(get("/cases/12345678")
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Invalid UUID string: 12345678"));
    }

    @DisplayName("Should return 400 when case has associated recordings that have not been deleted")
    @Test
    void deleteCaseRecordingNotDeleted() throws Exception {
        var caseId = UUID.randomUUID();
        doThrow(new RecordingNotDeletedException()).when(caseService).deleteById(caseId);
        mockMvc.perform(delete("/cases/" + caseId)
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Cannot delete because and associated recording has not been deleted."));

    }

    @DisplayName("Should set include deleted param to false if not set")
    @Test
    public void testGetCasesIncludeDeletedNotSet() throws Exception {
        when(caseService.searchBy(isNull(), isNull(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/cases")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(caseService, times(1)).searchBy(isNull(), isNull(), eq(false), any());
    }

    @DisplayName("Should set include deleted param to false when set to false")
    @Test
    public void testGetCasesIncludeDeletedFalse() throws Exception {
        when(caseService.searchBy(isNull(), isNull(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/cases")
                            .with(csrf())
                            .param("includeDeleted", "false")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(caseService, times(1)).searchBy(isNull(), isNull(), eq(false), any());
    }

    @DisplayName("Should set include deleted param to true when set to true")
    @Test
    public void testGetCasesIncludeDeletedTrue() throws Exception {
        when(caseService.searchBy(isNull(), isNull(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/cases")
                            .with(csrf())
                            .param("includeDeleted", "true")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(caseService, times(1)).searchBy(isNull(), isNull(), eq(true), any());
    }

    @DisplayName("Should undelete a case by id and return a 200 response")
    @Test
    void undeleteCaseSuccess() throws Exception {
        var caseId = UUID.randomUUID();
        doNothing().when(caseService).undelete(caseId);

        mockMvc.perform(post("/cases/" + caseId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should undelete a case by id and return a 404 response")
    @Test
    void undeleteCaseNotFound() throws Exception {
        var caseId = UUID.randomUUID();
        doThrow(
            new NotFoundException("Case: " + caseId)
        ).when(caseService).undelete(caseId);

        mockMvc.perform(post("/cases/" + caseId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Case: " + caseId));
    }

    @DisplayName("Should close pending cases and return a 204 response code")
    @Test
    void testClosePendingCases() throws Exception {
        mockMvc.perform(post("/cases/close-pending")
                            .with(csrf()))
            .andExpect(status().isNoContent());

        verify(caseService, times(1)).closePendingCases();
    }

    private CreateParticipantDTO createParticipant(ParticipantType type) {
        var dto = new CreateParticipantDTO();
        dto.setId(UUID.randomUUID());
        dto.setFirstName("Example");
        dto.setLastName("Person");
        dto.setParticipantType(type);
        return dto;
    }
}
