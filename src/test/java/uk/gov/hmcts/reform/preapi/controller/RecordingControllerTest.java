package uk.gov.hmcts.reform.preapi.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.dao.InvalidDataAccessApiUsageException;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import uk.gov.hmcts.reform.preapi.controllers.RecordingController;
import uk.gov.hmcts.reform.preapi.controllers.params.SearchRecordings;
import uk.gov.hmcts.reform.preapi.dto.CreateRecordingDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.exception.ResourceInDeletedStateException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.RecordingService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
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

@WebMvcTest(RecordingController.class)
@AutoConfigureMockMvc(addFilters = false)
@SuppressWarnings("PMD.AvoidDuplicateLiterals")
class RecordingControllerTest {
    @Autowired
    private transient MockMvc mockMvc;

    @MockitoBean
    private RecordingService recordingService;

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


    @DisplayName("Should get recording by ID with 200 response code")
    @Test
    void testGetRecordingByIdSuccess() throws Exception {
        UUID recordingId = UUID.randomUUID();
        var mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(recordingId);
        when(recordingService.findById(recordingId)).thenReturn(mockRecordingDTO);

        mockMvc.perform(get(getPath(recordingId)))
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$.id").value(recordingId.toString()));
    }

    @DisplayName("Should return 404 when trying to get non-existing recording")
    @Test
    void testGetNonExistingRecordingById() throws Exception {
        UUID recordingId = UUID.randomUUID();
        doThrow(new NotFoundException("RecordingDTO: " + recordingId))
            .when(recordingService).findById(recordingId);

        mockMvc.perform(get(getPath(recordingId)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: RecordingDTO: " + recordingId));
    }

    @DisplayName("Should get a list of recordings with 200 response code")
    @Test
    void testGetRecordingIdSuccess() throws Exception {
        UUID recordingId = UUID.randomUUID();
        var mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(recordingId);
        var recordingDTOList = List.of(mockRecordingDTO);
        when(recordingService.findAll(any(), eq(false), any()))
            .thenReturn(new PageImpl<>(recordingDTOList));

        mockMvc.perform(get("/recordings")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$._embedded.recordingDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.recordingDTOList[0].id").value(recordingId.toString()));

        verify(recordingService, times(1)).findAll(any(), eq(false), any());
    }

    @DisplayName("Should get a list of recordings with 200 response code when searching by started at date")
    @Test
    void testGetRecordingIdStartedAtSuccess() throws Exception {
        UUID recordingId = UUID.randomUUID();
        var mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(recordingId);
        var recordingDTOList = List.of(mockRecordingDTO);
        when(recordingService.findAll(any(), eq(false), any()))
            .thenReturn(new PageImpl<>(recordingDTOList));

        mockMvc.perform(get("/recordings")
                            .param("startedAt", "2024-01-01")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE)
            )
            .andExpect(status().isOk())
            .andExpect(content().contentType(MediaType.APPLICATION_JSON))
            .andExpect(jsonPath("$._embedded.recordingDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.recordingDTOList[0].id").value(recordingId.toString()));

        var params = new SearchRecordings();
        params.setStartedAtFrom(Timestamp.valueOf("2024-01-01 00:00:00"));

        verify(recordingService, times(1))
            .findAll(
                any(),
                eq(false),
                any()
            );
    }

    @DisplayName("Should create a recording with 201 response code")
    @Test
    void createRecordingCreated() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setFilename("example filename");
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setVersion(1);

        when(recordingService.upsert(recording)).thenReturn(UpsertResult.CREATED);

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(recordingId)
        );
    }

    @DisplayName("Should update a recording with 204 response code")
    @Test
    void updateRecordingUpdate() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setFilename("example filename");
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setVersion(1);

        when(recordingService.upsert(recording)).thenReturn(UpsertResult.UPDATED);

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andReturn();

        assertThat(response.getResponse().getContentAsString()).isEqualTo("");
        assertThat(
            response.getResponse().getHeaderValue("Location")).isEqualTo(TEST_URL + getPath(recordingId)
        );
    }

    @DisplayName("Should fail to create/update a recording with 400 response code recordingId mismatch")
    @Test
    void createRecordingIdMismatch() throws Exception {
        var recording = new CreateRecordingDTO();
        recording.setId(UUID.randomUUID());
        recording.setFilename("example filename");
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setVersion(1);

        MvcResult response = mockMvc.perform(put(getPath(UUID.randomUUID()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().is4xxClientError())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Path recordingId does not match payload property createRecordingDTO.id\"}");
    }

    @DisplayName("Should fail to update a recording with 400 response code when it has been marked as deleted")
    @Test
    void updateRecordingBadRequest() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setFilename("example filename");
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setVersion(1);

        doThrow(new ResourceInDeletedStateException("RecordingDTO", recordingId.toString()))
            .when(recordingService).upsert(any());

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Resource RecordingDTO("
                           + recordingId + ") is in a deleted state and cannot be updated\"}"
            );
    }

    @DisplayName("Should fail to create/update a recording with 404 response code when capture session does not exist")
    @Test
    void createRecordingCaptureSessionNotFound() throws Exception {
        var recordingId = UUID.randomUUID();
        var captureSessionId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setCaptureSessionId(captureSessionId);
        recording.setFilename("example filename");
        recording.setVersion(1);

        doThrow(new NotFoundException("Capture Session: " + captureSessionId))
            .when(recordingService)
            .upsert(any());

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Capture Session: " + captureSessionId + "\"}");
    }

    @DisplayName("Should fail to create/update a recording with 404 response code when parent recording does not exist")
    @Test
    void createRecordingParentRecordingNotFound() throws Exception {
        var recordingId = UUID.randomUUID();
        var parentRecordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setParentRecordingId(parentRecordingId);
        recording.setFilename("example filename");
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setVersion(1);

        doThrow(new NotFoundException("Recording: " + parentRecordingId))
            .when(recordingService)
            .upsert(any());

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"message\":\"Not found: Recording: " + parentRecordingId + "\"}");
    }

    @DisplayName("Should fail to create/update recording when id is null")
    @Test
    void createRecordingValidatorIdFailure() throws Exception {
        var recording = new CreateRecordingDTO();
        recording.setFilename("example filename");
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setVersion(1);

        MvcResult response = mockMvc.perform(put(getPath(UUID.randomUUID()))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"id\":\"id is required\"}");
    }

    @DisplayName("Should fail to create/update recording when capture session id is null")
    @Test
    void createRecordingValidatorCaptureSessionIdFailure() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setFilename("example filename");
        recording.setVersion(1);

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"captureSessionId\":\"capture_session_id is required\"}");
    }

    @DisplayName("Should fail to create/update recording when version is null")
    @Test
    void createRecordingValidatorVersionFailure() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setFilename("example filename");

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"version\":\"version is required\"}");
    }

    @DisplayName("Should fail to create/update recording when version less that 1")
    @Test
    void createRecordingValidatorVersionFailure2() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setFilename("example filename");
        recording.setVersion(0);

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"version\":\"must be greater than or equal to 1\"}");
    }

    @DisplayName("Should fail to create/update recording when filename is null")
    @Test
    void createRecordingValidatorFilenameFailure() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setVersion(1);

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"filename\":\"filename is required\"}");
    }

    @DisplayName("Should fail to create/update recording when edit instructions is not valid json")
    @Test
    void createRecordingValidatorEditInstructionsFailure() throws Exception {
        var recordingId = UUID.randomUUID();
        var recording = new CreateRecordingDTO();
        recording.setId(recordingId);
        recording.setCaptureSessionId(UUID.randomUUID());
        recording.setFilename("example filename");
        recording.setVersion(1);
        recording.setEditInstructions("invalid json {}");

        MvcResult response = mockMvc.perform(put(getPath(recordingId))
                                                 .with(csrf())
                                                 .content(OBJECT_MAPPER.writeValueAsString(recording))
                                                 .contentType(MediaType.APPLICATION_JSON_VALUE)
                                                 .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(response.getResponse().getContentAsString())
            .isEqualTo("{\"editInstructions\":\"invalid JSON format\"}");
    }

    @DisplayName("Should delete recording with 200 response code")
    @Test
    void testDeleteRecordingSuccess() throws Exception {
        UUID recordingId = UUID.randomUUID();
        doNothing().when(recordingService).deleteById(recordingId);

        mockMvc.perform(delete(getPath(recordingId))
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should return 404 when recording doesn't exist")
    @Test
    void testDeleteRecordingNotFound() throws Exception {
        UUID recordingId = UUID.randomUUID();
        doThrow(new NotFoundException("Recording: " + recordingId))
            .when(recordingService)
            .deleteById(recordingId);

        mockMvc.perform(delete(getPath(recordingId))
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Recording: " + recordingId));
    }

    @DisplayName("Should return 400 when recording id is not a uuid")
    @Test
    void testFindByIdBadRequest() throws Exception {
        mockMvc.perform(get("/recordings/12345678")
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Invalid UUID string: 12345678"));
    }

    @DisplayName("Should set include deleted param to false if not set")
    @Test
    public void testGetCasesIncludeDeletedNotSet() throws Exception {
        when(recordingService.findAll(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/recordings")
                            .with(csrf())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(recordingService, times(1)).findAll(any(), eq(false), any());
    }

    @DisplayName("Should set include deleted param to false when set to false")
    @Test
    public void testGetCasesIncludeDeletedFalse() throws Exception {
        when(recordingService.findAll(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/recordings")
                            .with(csrf())
                            .param("includeDeleted", "false")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(recordingService, times(1)).findAll(any(), eq(false), any());
    }

    @DisplayName("Should set include deleted param to true when set to true")
    @Test
    public void testGetCasesIncludeDeletedTrue() throws Exception {
        when(recordingService.findAll(any(), anyBoolean(), any())).thenReturn(new PageImpl<>(List.of()));

        mockMvc.perform(get("/recordings")
                            .with(csrf())
                            .param("includeDeleted", "true")
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andReturn();

        verify(recordingService, times(1)).findAll(any(), eq(true), any());
    }

    @Test
    @DisplayName("Should search by case open status = true")
    void getRecordingsCaseOpenTrue() throws Exception {
        var mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(UUID.randomUUID());
        when(recordingService.findAll(any(), anyBoolean(), any()))
            .thenReturn(new PageImpl<>(List.of(mockRecordingDTO)));

        mockMvc.perform(get("/recordings?caseOpen=true")
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.recordingDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.recordingDTOList[0].id").value(mockRecordingDTO.getId().toString()));

        var argCaptor = ArgumentCaptor.forClass(SearchRecordings.class);
        verify(recordingService, times(1)).findAll(argCaptor.capture(), eq(false), any());

        assertThat(argCaptor.getValue().getCaseOpen()).isTrue();
    }

    @Test
    @DisplayName("Should search by case open status = false")
    void getRecordingsCaseOpenFalse() throws Exception {
        var mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(UUID.randomUUID());
        when(recordingService.findAll(any(), anyBoolean(), any()))
            .thenReturn(new PageImpl<>(List.of(mockRecordingDTO)));

        mockMvc.perform(get("/recordings?caseOpen=false")
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.recordingDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.recordingDTOList[0].id").value(mockRecordingDTO.getId().toString()));

        var argCaptor = ArgumentCaptor.forClass(SearchRecordings.class);
        verify(recordingService, times(1)).findAll(argCaptor.capture(), eq(false), any());

        assertThat(argCaptor.getValue().getCaseOpen()).isFalse();
    }

    @Test
    @DisplayName("Should search by case open status = null")
    void getRecordingsCaseOpenNull() throws Exception {
        var mockRecordingDTO = new RecordingDTO();
        mockRecordingDTO.setId(UUID.randomUUID());
        when(recordingService.findAll(any(), anyBoolean(), any()))
            .thenReturn(new PageImpl<>(List.of(mockRecordingDTO)));

        mockMvc.perform(get("/recordings")
                            .with(csrf()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$._embedded.recordingDTOList").isNotEmpty())
            .andExpect(jsonPath("$._embedded.recordingDTOList[0].id").value(mockRecordingDTO.getId().toString()));

        var argCaptor = ArgumentCaptor.forClass(SearchRecordings.class);
        verify(recordingService, times(1)).findAll(argCaptor.capture(), eq(false), any());

        assertThat(argCaptor.getValue().getCaseOpen()).isNull();
    }

    @DisplayName("Should undelete a recording by id and return a 200 response")
    @Test
    void undeleteRecordingSuccess() throws Exception {
        var recordingId = UUID.randomUUID();
        doNothing().when(recordingService).undelete(recordingId);

        mockMvc.perform(post("/recordings/" + recordingId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isOk());
    }

    @DisplayName("Should undelete a recording by id and return a 404 response")
    @Test
    void undeleteRecordingNotFound() throws Exception {
        var recordingId = UUID.randomUUID();
        doThrow(
            new NotFoundException("Recording: " + recordingId)
        ).when(recordingService).undelete(recordingId);

        mockMvc.perform(post("/recordings/" + recordingId + "/undelete")
                            .with(csrf()))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message")
                           .value("Not found: Recording: " + recordingId));
    }

    @DisplayName("Should throw 400 error when attempting to sort with invalid parameter")
    @Test
    void getRecordingsInvalidSortParam() throws Exception {
        var mockError = mock(InvalidDataAccessApiUsageException.class);
        when(mockError.getMessage())
            .thenReturn("some error: Could not resolve attribute 'invalidParam' of 'Recording' [some sql]");
        doThrow(mockError)
            .when(recordingService)
            .findAll(any(), anyBoolean(), any());

        mockMvc.perform(get("/recordings?sort=invalidParam")
                            .with(csrf()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Invalid sort parameter 'invalidParam' for 'Recording'"));
    }


    private static String getPath(UUID recordingId) {
        return "/recordings/" + recordingId;
    }
}
