package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class EditControllerFT extends FunctionalTestBase {
    private static final String VALID_EDIT_CSV = "src/functionalTest/resources/test/edit/edit_from_csv.csv";
    private static final String EDIT_ENDPOINT = "/edits";

    private static final Map<String, String> MULTIPART_HEADERS =
        Map.of("Content-Type", MediaType.MULTIPART_FORM_DATA_VALUE);

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

        assertThat(postResponse.getEditInstruction()).isNotNull();
        EditInstructions instructions = OBJECT_MAPPER.readValue(postResponse.getEditInstruction(),
                                                   new TypeReference<>() {});
        assertThat(instructions).isNotNull();

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
}
