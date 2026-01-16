package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.PropertyNamingStrategy;
import uk.gov.hmcts.reform.preapi.controllers.EditController;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.enums.UpsertResult;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static java.lang.String.format;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.preapi.controllers.EditController.CSV_FILE_TYPE;

@WebMvcTest(EditController.class)
@TestPropertySource(properties = {
    "editing.enable=true"
})
@AutoConfigureMockMvc(addFilters = false)
public class EditControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private EditRequestService editRequestService;

    @MockitoBean
    private UserAuthenticationService userAuthenticationService;

    @MockitoBean
    private ScheduledTaskRunner taskRunner;

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TEST_URL = "http://localhost";

    private MockMultipartFile validFile;

    @BeforeEach
    void beforeEach() {
        validFile = new MockMultipartFile("file", "test.csv", CSV_FILE_TYPE, "test content".getBytes());
    }

    @BeforeAll
    static void beforeAll() {
        OBJECT_MAPPER.setDateFormat(new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SS'Z'"));
        OBJECT_MAPPER.setPropertyNamingStrategy(new PropertyNamingStrategy.SnakeCaseStrategy());
    }

    @Test
    @DisplayName("Should return 200 when successfully created edit request from csv")
    void createEditFromCsvSuccess() throws Exception {
        var dto = new EditRequestDTO();
        dto.setId(UUID.randomUUID());
        var sourceId = UUID.randomUUID();

        when(editRequestService.upsert(sourceId, validFile)).thenReturn(dto);

        mockMvc.perform(multipart(TEST_URL + "/edits/from-csv/" + sourceId)
                            .file(validFile)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(dto.getId().toString()));

        verify(editRequestService, times(1)).upsert(eq(sourceId), isA(MultipartFile.class));
    }

    @Test
    @DisplayName("Should return 415 when attempting to create edit request with file that is not a csv")
    void createEditFromCsvNotCsv() throws Exception {
        var notACsv = new MockMultipartFile(
            "file",
            "test.txt",
            MediaType.TEXT_PLAIN.toString(),
            "file content".getBytes()
        );

        var sourceId = UUID.randomUUID();
        mockMvc.perform(multipart(TEST_URL + "/edits/from-csv/" + sourceId)
                            .file(notACsv)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("Unsupported media type: Only CSV files are supported"));

        verify(editRequestService, never()).upsert(any(), any());
    }

    @Test
    @DisplayName("Should return 415 when attempting to create edit request with file that is not a csv (has null type)")
    void createEditFromCsvNotCsvIsNullType() throws Exception {
        var notACsv = new MockMultipartFile(
            "file",
            "test.txt",
            null,
            "file content".getBytes()
        );

        var sourceId = UUID.randomUUID();
        mockMvc.perform(multipart(TEST_URL + "/edits/from-csv/" + sourceId)
                            .file(notACsv)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isUnsupportedMediaType())
            .andExpect(jsonPath("$.message").value("Unsupported media type: Only CSV files are supported"));

        verify(editRequestService, never()).upsert(any(), any());
    }

    @Test
    @DisplayName("Should return 400 when attempting to create edit request with empty file")
    void createdEditFromCsvEmpty() throws Exception {
        var emptyCsv = new MockMultipartFile("file", "test.txt", "text/csv", new byte[0]);

        var sourceId = UUID.randomUUID();
        mockMvc.perform(multipart(TEST_URL + "/edits/from-csv/" + sourceId)
                            .file(emptyCsv)
                            .contentType(MediaType.MULTIPART_FORM_DATA)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("File must not be empty"));

        verify(editRequestService, never()).upsert(any(), any());
    }

    @Test
    @DisplayName("Should return 200 and edit request dto when edit request exists")
    void getByIdSuccess() throws Exception {
        var dto = new EditRequestDTO();
        dto.setId(UUID.randomUUID());

        when(editRequestService.findById(dto.getId())).thenReturn(dto);

        mockMvc.perform(get(TEST_URL + "/edits/" + dto.getId())
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(dto.getId().toString()));
    }

    @Test
    @DisplayName("Should return 404 when edit request does not exist")
    void getByIdNotFound() throws Exception {
        var id = UUID.randomUUID();

        doThrow(new NotFoundException("Edit Request: " + id)).when(editRequestService).findById(id);

        mockMvc.perform(get(TEST_URL + "/edits/" + id)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.message").value("Not found: Edit Request: " + id));
    }

    @Test
    @DisplayName("Should get a list of edits with 200 response code")
    void getEdits() throws Exception {
        EditRequestDTO dto = new EditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstruction(EditInstructions.fromJson("{}"));

        RecordingDTO recordingDTO = new RecordingDTO();
        recordingDTO.setId(UUID.randomUUID());
        dto.setSourceRecording(recordingDTO);

        when(editRequestService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/edits")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpectAll(
                status().isOk(),
                jsonPath("$._embedded.editRequestDTOList").isNotEmpty(),
                jsonPath("$._embedded.editRequestDTOList[0].id").value(dto.getId().toString()),
                jsonPath("$._embedded.editRequestDTOList[0].status").value(dto.getStatus().toString()),
                jsonPath("$._embedded.editRequestDTOList[0].source_recording.id").value(recordingDTO.getId().toString())
            );

        verify(editRequestService, times(1)).findAll(any(), any());
    }

    @Test
    @DisplayName("Should get a list of edits with 400 response code when requesting out of range")
    void getEditsOutOfBounds() throws Exception {
        EditRequestDTO dto = new EditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setStatus(EditRequestStatus.PENDING);
        dto.setEditInstruction(EditInstructions.fromJson("{}"));

        RecordingDTO recordingDTO = new RecordingDTO();
        recordingDTO.setId(UUID.randomUUID());
        dto.setSourceRecording(recordingDTO);

        when(editRequestService.findAll(any(), any())).thenReturn(new PageImpl<>(List.of(dto)));

        mockMvc.perform(get("/edits")
                            .param("page", "2")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON_VALUE))
            .andExpectAll(
                status().is4xxClientError(),
                jsonPath("$.message").value("Requested page {2} is out of range. Max page is {1}")
            );

        verify(editRequestService, times(1)).findAll(any(), any());
    }

    @Test
    @DisplayName("Should return 201 when successfully created edit request")
    void upsertEditRequestCreated() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.DRAFT);

        when(editRequestService.upsert(any(CreateEditRequestDTO.class))).thenReturn(UpsertResult.CREATED);

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isCreated())
            .andExpect(header().string("Location", TEST_URL + "/edits/" + dto.getId()));

        verify(editRequestService, times(1)).upsert(any(CreateEditRequestDTO.class));
    }

    @Test
    @DisplayName("Should return 201 when successfully deleted edit request")
    void upsertEditRequestDeleted() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.DRAFT);

        when(editRequestService.upsert(any(CreateEditRequestDTO.class))).thenReturn(UpsertResult.CREATED);

        mockMvc.perform(delete(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isOk());

        verify(editRequestService, times(1)).delete(any(CreateEditRequestDTO.class));
    }

    @Test
    @DisplayName("Delete should return 400 when path and payload IDs do not match")
    void deleteEditRequestPathPayloadMismatch() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.DRAFT);
        var differentId = UUID.randomUUID();

        mockMvc.perform(delete(TEST_URL + "/edits/" + differentId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Path editRequestId does not match payload property deleteEditRequestDTO.id"));
    }


    @Test
    @DisplayName("Should return 204 when successfully updated edit request")
    void upsertEditRequestUpdated() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.DRAFT);

        when(editRequestService.upsert(any(CreateEditRequestDTO.class))).thenReturn(UpsertResult.UPDATED);

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isNoContent())
            .andExpect(header().string("Location", TEST_URL + "/edits/" + dto.getId()));

        verify(editRequestService, times(1)).upsert(any(CreateEditRequestDTO.class));
    }

    @Test
    @DisplayName("Should return 400 when path and payload IDs do not match")
    void upsertEditRequestPathPayloadMismatch() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.DRAFT);
        var differentId = UUID.randomUUID();

        mockMvc.perform(put(TEST_URL + "/edits/" + differentId)
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message")
                           .value("Path editRequestId does not match payload property createEditRequestDTO.id"));
    }

    @Test
    @DisplayName("Should return 400 when startOfCut is null")
    void validateStartOfCutIsNull() throws Exception {
        String anyIdWillDo = "3fa85f64-5717-4562-b3fc-2c963f66afa6";
        String editRequestJson = format("""
            {
              "edit_instructions": [
                {
                  "end_of_cut": "14:35:07",
                  "reason": "string"
                }
              ],
              "id": "%s",
              "source_recording_id": "%s",
              "status": "DRAFT"
            }
            """, anyIdWillDo, anyIdWillDo);

        var result = mockMvc.perform(put(TEST_URL + "/edits/" + anyIdWillDo)
                                         .with(csrf())
                                         .content(editRequestJson)
                                         .contentType(MediaType.APPLICATION_JSON_VALUE)
                                         .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andReturn();

        assertThat(result.getResponse().getContentAsString())
            .isEqualTo("{\"editInstructions[0].startOfCut\":\"must not be null\"}");
    }

    @Test
    @DisplayName("Should return 400 when sourceRecordingId is null")
    void validateSourceRecordingIdIsNull() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.DRAFT);

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.sourceRecordingId").value("must not be null"));
    }

    @Test
    @DisplayName("Should return 400 when status is null")
    void validateStatusIsNull() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.status").value("must not be null"));
    }

    @Test
    @DisplayName("Should return 400 when status is REJECTED and rejectionReason is null")
    void validateRejectedStatusWithoutRejectionReason() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.REJECTED);

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.rejectionReason").value("must have rejection reason when status is REJECTED"));
    }

    @Test
    @DisplayName("Should return 400 when status is SUBMITTED and jointlyAgreed is null")
    void validateSubmittedStatusWithoutJointlyAgreed() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.SUBMITTED);

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.jointlyAgreed").value("must have jointly agreed when status is SUBMITTED"));
    }

    @Test
    @DisplayName("Should return 400 when status is APPROVED and approvedAt is null")
    void validateApprovedStatusWithoutApprovedAt() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.APPROVED);
        dto.setApprovedBy("Someone");

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.approvedAt").value("must have approved at when status is APPROVED"));
    }

    @Test
    @DisplayName("Should return 400 when status is APPROVED and approvedBy is null")
    void validateApprovedStatusWithoutApprovedBy() throws Exception {
        var dto = new CreateEditRequestDTO();
        dto.setId(UUID.randomUUID());
        dto.setSourceRecordingId(UUID.randomUUID());
        dto.setEditInstructions(List.of(EditCutInstructionDTO.builder()
                                            .startOfCut("00:00:00")
                                            .endOfCut("00:00:01")
                                            .build()));
        dto.setStatus(EditRequestStatus.APPROVED);
        dto.setApprovedAt(Timestamp.from(Instant.now()));

        mockMvc.perform(put(TEST_URL + "/edits/" + dto.getId())
                            .with(csrf())
                            .content(OBJECT_MAPPER.writeValueAsString(dto))
                            .contentType(MediaType.APPLICATION_JSON_VALUE)
                            .accept(MediaType.APPLICATION_JSON_VALUE))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.approvedBy").value("must have approved by when status is APPROVED"));
    }

}
