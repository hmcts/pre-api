package uk.gov.hmcts.reform.preapi.controller;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MultipartFile;
import org.testcontainers.shaded.com.fasterxml.jackson.databind.ObjectMapper;
import uk.gov.hmcts.reform.preapi.controllers.EditController;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.exception.NotFoundException;
import uk.gov.hmcts.reform.preapi.security.service.UserAuthenticationService;
import uk.gov.hmcts.reform.preapi.services.EditRequestService;
import uk.gov.hmcts.reform.preapi.services.ScheduledTaskRunner;

import java.text.SimpleDateFormat;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static uk.gov.hmcts.reform.preapi.controllers.EditController.CSV_FILE_TYPE;

@WebMvcTest(EditController.class)
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
}
