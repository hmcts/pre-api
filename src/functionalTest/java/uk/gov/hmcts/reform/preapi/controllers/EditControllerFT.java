package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@Slf4j
public class EditControllerFT extends FunctionalTestBase {
    private static final String VALID_EDIT_CSV = "src/functionalTest/resources/test/edit/edit_from_csv.csv";
    private static final String UNSAFE_EDIT_CSV = "src/functionalTest/resources/test/edit/edit_from_csv_unsafe.csv";
    private static final String EDIT_ENDPOINT = "/edits";

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @Test
    @DisplayName("Should create an edit request from a csv")
    void editFromCsvSuccess() throws JsonProcessingException {
        CreateRecordingResponse recordingDetails = createRecording();
        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true).as(RecordingDTO.class);

        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());

        EditRequestDTO postResponse = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            TestingSupportRoles.SUPER_USER
        ).as(EditRequestDTO.class);

        assertThat(postResponse.getId()).isNotNull();
        assertThat(postResponse.getSourceRecording().getId()).isEqualTo(recordingDetails.recordingId());
        assertThat(postResponse.getStatus()).isEqualTo(EditRequestStatus.PENDING);

        var instructions = postResponse.getEditInstruction();
        assertThat(postResponse.getEditInstruction()).isNotNull();

        List<EditCutInstructionDTO> requestedInstructions = instructions.getRequestedInstructions();
        assertThat(requestedInstructions).isNotEmpty();
        assertThat(requestedInstructions.size()).isEqualTo(2);

        assertThat(requestedInstructions.getFirst().getStartOfCut()).isEqualTo("00:00:00");
        assertThat(requestedInstructions.getFirst().getEndOfCut()).isEqualTo("00:01:00");
        assertThat(requestedInstructions.getFirst().getReason()).isEqualTo("I don’t want this");
        assertThat(requestedInstructions.getFirst().getStart()).isEqualTo(0);
        assertThat(requestedInstructions.getFirst().getEnd()).isEqualTo(60);

        assertThat(requestedInstructions.getLast().getStartOfCut()).isEqualTo("00:01:01");
        assertThat(requestedInstructions.getLast().getEndOfCut()).isEqualTo("00:02:00");
        assertThat(requestedInstructions.getLast().getReason()).isEmpty();
        assertThat(requestedInstructions.getLast().getStart()).isEqualTo(61);
        assertThat(requestedInstructions.getLast().getEnd()).isEqualTo(120);

        List<FfmpegEditInstructionDTO> ffmpegInstructions = instructions.getFfmpegInstructions();
        assertThat(ffmpegInstructions).isNotEmpty();
        assertThat(ffmpegInstructions.size()).isEqualTo(2);

        assertThat(ffmpegInstructions.getFirst().getStart()).isEqualTo(60);
        assertThat(ffmpegInstructions.getFirst().getEnd()).isEqualTo(61);

        assertThat(ffmpegInstructions.getLast().getStart()).isEqualTo(120);
        assertThat(ffmpegInstructions.getLast().getEnd()).isEqualTo(180);
    }

    @NullSource
    @ParameterizedTest
    @EnumSource(value = TestingSupportRoles.class, names = "SUPER_USER", mode = EnumSource.Mode.EXCLUDE)
    @DisplayName("Should return forbidden for users attempting to use from csv endpoint with incorrect role")
    void editRequestFromCsvForbidden(TestingSupportRoles role) {
        var recordingDetails = createRecording();
        assertRecordingExists(recordingDetails.recordingId(), true);

        var response = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            VALID_EDIT_CSV,
            role
        );
        assertResponseCode(response, role == null ? 401 : 403);
    }

    @Test
    @DisplayName("Should not create an edit with a csv that has unsafe data in fields")
    void editRequestWithUnsafeDataCsv() throws JsonProcessingException {
        CreateRecordingResponse recordingDetails = createRecording();
        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true).as(RecordingDTO.class);

        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());

        Response postResponse = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            UNSAFE_EDIT_CSV,
            TestingSupportRoles.SUPER_USER
        );

        assertResponseCode(postResponse, 400);
    }

    @Test
    @DisplayName("Should not create an edit request with unsafe data in fields")
    void editRequestWithUnsafeData() throws JsonProcessingException {
        UUID editRequestId = UUID.randomUUID();
        CreateRecordingResponse recordingDetails = createRecording();
        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true).as(RecordingDTO.class);

        CreateEditRequestDTO editRequestDTO = new CreateEditRequestDTO();
        editRequestDTO.setSourceRecordingId(recordingDetails.recordingId());
        editRequestDTO.setStatus(EditRequestStatus.PENDING);
        editRequestDTO.setId(editRequestId);
        List<EditCutInstructionDTO> editCutInstructionDTOS = new ArrayList<>();
        EditCutInstructionDTO cutInstruction1 = new EditCutInstructionDTO();
        cutInstruction1.setStartOfCut("00:00:00");
        cutInstruction1.setEndOfCut("00:01:00");
        cutInstruction1.setReason("<script>alert('XSS')</script>Unsafe reason");
        editCutInstructionDTOS.add(cutInstruction1);
        editRequestDTO.setEditInstructions(editCutInstructionDTOS);

        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());

        Response postResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + editRequestId,
            OBJECT_MAPPER.writeValueAsString(editRequestDTO),
            TestingSupportRoles.SUPER_USER
        );

        assertResponseCode(postResponse, 400);
        assertThat(postResponse.getBody().asPrettyString())
            .contains("potentially malicious content");
    }
}
