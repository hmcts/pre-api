package uk.gov.hmcts.reform.preapi.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.restassured.response.Response;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.NullSource;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import uk.gov.hmcts.reform.preapi.controllers.params.TestingSupportRoles;
import uk.gov.hmcts.reform.preapi.dto.CreateEditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.EditCutInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.EditRequestDTO;
import uk.gov.hmcts.reform.preapi.dto.FfmpegEditInstructionDTO;
import uk.gov.hmcts.reform.preapi.dto.RecordingDTO;
import uk.gov.hmcts.reform.preapi.enums.EditRequestStatus;
import uk.gov.hmcts.reform.preapi.media.edit.EditInstructions;
import uk.gov.hmcts.reform.preapi.media.storage.AzureFinalStorageService;
import uk.gov.hmcts.reform.preapi.util.FunctionalTestBase;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

public class EditControllerFT extends FunctionalTestBase {
    private static final String EDIT_CSV_DIR = "src/functionalTest/resources/test/edit/";
    private static final String VALID_EDIT_CSV = EDIT_CSV_DIR + "edit_from_csv.csv";
    private static final String UNSAFE_EDIT_CSV = EDIT_CSV_DIR + "edit_from_csv_unsafe.csv";
    private static final String EDIT_ENDPOINT = "/edits";

    @MockitoBean
    private AzureFinalStorageService azureFinalStorageService;

    @Test
    @DisplayName("Should create an edit request from a csv")
    // This covers
    void editFromCsvSuccess() {
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

    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    @Test
    @DisplayName("Should not create an edit request with unsafe data in fields")
    void editRequestWithUnsafeData() throws JsonProcessingException {
        UUID editRequestId = UUID.randomUUID();
        CreateRecordingResponse recordingDetails = createRecording();

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

        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true).as(RecordingDTO.class);
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

    @Test
    @DisplayName("Should create a reencode-only edit request")
    void reencodeOnlyEditRequestSuccess() throws JsonProcessingException {
        UUID editRequestId = UUID.randomUUID();
        CreateRecordingResponse recordingDetails = createRecording();

        CreateEditRequestDTO editRequestDTO = new CreateEditRequestDTO();
        editRequestDTO.setSourceRecordingId(recordingDetails.recordingId());
        editRequestDTO.setStatus(EditRequestStatus.PENDING);
        editRequestDTO.setId(editRequestId);
        editRequestDTO.setForceReencode(true);

        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true).as(RecordingDTO.class);
        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());

        Response putResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + editRequestId,
            OBJECT_MAPPER.writeValueAsString(editRequestDTO),
            TestingSupportRoles.SUPER_USER
        );

        assertResponseCode(putResponse, 201);

        EditRequestDTO getResponse = doGetRequest(EDIT_ENDPOINT + "/" + editRequestId, TestingSupportRoles.SUPER_USER)
            .as(EditRequestDTO.class);

        assertThat(getResponse.getId()).isEqualTo(editRequestId);
        assertThat(getResponse.getEditInstruction().isForceReencode()).isTrue();
        assertThat(getResponse.getEditInstruction().getRequestedInstructions()).isEmpty();
        assertThat(getResponse.getEditInstruction().getFfmpegInstructions()).isEmpty();
        assertThat(getResponse.getEditInstruction().shouldSendNotifications()).isTrue();
    }

    @Test
    @DisplayName("Should create an edit request with notifications disabled")
    void editRequestWithNotificationsDisabledSuccess() throws JsonProcessingException {
        UUID editRequestId = UUID.randomUUID();
        CreateRecordingResponse recordingDetails = createRecording();

        CreateEditRequestDTO editRequestDTO = new CreateEditRequestDTO();
        editRequestDTO.setSourceRecordingId(recordingDetails.recordingId());
        editRequestDTO.setStatus(EditRequestStatus.PENDING);
        editRequestDTO.setId(editRequestId);
        editRequestDTO.setSendNotifications(false);
        editRequestDTO.setEditInstructions(List.of(EditCutInstructionDTO.builder()
            .startOfCut("00:00:00")
            .endOfCut("00:00:01")
            .build()));

        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true).as(RecordingDTO.class);
        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());

        Response putResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + editRequestId,
            OBJECT_MAPPER.writeValueAsString(editRequestDTO),
            TestingSupportRoles.SUPER_USER
        );

        assertResponseCode(putResponse, 201);

        EditRequestDTO getResponse = doGetRequest(EDIT_ENDPOINT + "/" + editRequestId, TestingSupportRoles.SUPER_USER)
            .as(EditRequestDTO.class);

        assertThat(getResponse.getId()).isEqualTo(editRequestId);
        assertThat(getResponse.getEditInstruction().shouldSendNotifications()).isFalse();
    }

    @Test
    @DisplayName("Should reject edit requests that contain cut instructions and force reencode")
    void editRequestWithCutsAndForceReencodeBadRequest() throws JsonProcessingException {
        UUID editRequestId = UUID.randomUUID();
        CreateRecordingResponse recordingDetails = createRecording();

        CreateEditRequestDTO editRequestDTO = new CreateEditRequestDTO();
        editRequestDTO.setSourceRecordingId(recordingDetails.recordingId());
        editRequestDTO.setStatus(EditRequestStatus.PENDING);
        editRequestDTO.setId(editRequestId);
        editRequestDTO.setForceReencode(true);
        editRequestDTO.setEditInstructions(List.of(EditCutInstructionDTO.builder()
            .startOfCut("00:00:00")
            .endOfCut("00:00:01")
            .build()));

        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true).as(RecordingDTO.class);
        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());

        Response putResponse = doPutRequest(
            EDIT_ENDPOINT + "/" + editRequestId,
            OBJECT_MAPPER.writeValueAsString(editRequestDTO),
            TestingSupportRoles.SUPER_USER
        );

        assertResponseCode(putResponse, 400);
    }

    @Test
    @DisplayName("S28-5018 Should apply cumulative layers of successive edit requests")
    // Notes: this is a regression test for the existing semi-automated process
    // Edit requests are created via the CSV endpoint
    // Need to work out why S28-5018 failed
    void editRequestsShouldBeCumulative() {
        CreateRecordingResponse recordingDetails = createRecording();
        RecordingDTO recordingDTO = assertRecordingExists(recordingDetails.recordingId(), true)
            .as(RecordingDTO.class);

        // This duration matches the original source recording for the failed edit
        Duration originalDuration = Duration.of(3690, ChronoUnit.SECONDS).plusMillis(100);
        recordingDTO.setDuration(originalDuration);

        when(azureFinalStorageService.getMp4FileName(recordingDetails.recordingId().toString()))
            .thenReturn(recordingDTO.getFilename());
        when(azureFinalStorageService.getRecordingDuration(recordingDetails.recordingId()))
            .thenReturn(recordingDTO.getDuration());

        // Submit first edit request
        String firstSetOfEditsFileName = EDIT_CSV_DIR + "S28-5018-first-set-of-edits.csv";

        EditRequestDTO firstEditPostResponse = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + recordingDetails.recordingId(),
            MULTIPART_HEADERS,
            firstSetOfEditsFileName,
            TestingSupportRoles.SUPER_USER
        ).as(EditRequestDTO.class);

        assertThat(firstEditPostResponse.getId()).isNotNull();
        assertThat(firstEditPostResponse.getSourceRecording().getId()).isEqualTo(recordingDetails.recordingId());
        assertThat(firstEditPostResponse.getStatus()).isEqualTo(EditRequestStatus.PENDING);

        EditInstructions instructions = firstEditPostResponse.getEditInstruction();
        assertThat(firstEditPostResponse.getEditInstruction()).isNotNull();

        List<EditCutInstructionDTO> requestedInstructions = instructions.getRequestedInstructions();
        assertThat(requestedInstructions).isNotEmpty();
        assertThat(requestedInstructions.size()).isEqualTo(2);

        assertThat(requestedInstructions.getFirst().getStartOfCut()).isEqualTo("00:00:00");
        assertThat(requestedInstructions.getFirst().getEndOfCut()).isEqualTo("00:06:32");
        assertThat(requestedInstructions.getFirst().getReason())
            .isEqualTo("technical issues in panning camera around the court");
        assertThat(requestedInstructions.getFirst().getStart()).isEqualTo(0);
        assertThat(requestedInstructions.getFirst().getEnd()).isEqualTo(392);

        assertThat(requestedInstructions.getLast().getStartOfCut()).isEqualTo("00:29:38");
        assertThat(requestedInstructions.getLast().getEndOfCut()).isEqualTo("00:31:26");
        assertThat(requestedInstructions.getLast().getReason()).isEqualTo("witness comfort break");
        assertThat(requestedInstructions.getLast().getStart()).isEqualTo(1778);
        assertThat(requestedInstructions.getLast().getEnd()).isEqualTo(1886);

        List<FfmpegEditInstructionDTO> ffmpegInstructions = instructions.getFfmpegInstructions();
        assertThat(ffmpegInstructions).isNotEmpty();
        assertThat(ffmpegInstructions.size()).isEqualTo(3);

        assertThat(ffmpegInstructions.getFirst().getStart()).isEqualTo(60);
        assertThat(ffmpegInstructions.getFirst().getEnd()).isEqualTo(61);

        assertThat(ffmpegInstructions.getLast().getStart()).isEqualTo(1778);
        assertThat(ffmpegInstructions.getLast().getEnd()).isEqualTo(1886);

        // TODO: Process first edit request and get back recording ID for V2
        Response processedFirstEdit = doPostRequest(
            "/testing-support/trigger-edit-request-processing/" + firstEditPostResponse.getId(),
            TestingSupportRoles.SUPER_USER).as(Response.class);

        // TODO: Check this - still in draft
        UUID version2RecordingId = processedFirstEdit.body().jsonPath().getUUID("recording");

        // TODO: Submit second edit request
        String secondSetOfEdits = EDIT_CSV_DIR + "S28-5018-second-set-of-edits.csv";
        EditRequestDTO secondEditPostResponse = doPostRequestWithMultipart(
            EDIT_ENDPOINT + "/from-csv/" + version2RecordingId,
            MULTIPART_HEADERS,
            secondSetOfEdits,
            TestingSupportRoles.SUPER_USER
        ).as(EditRequestDTO.class);

        // TODO: Check second edit request has been created

        // TODO: Process second edit request

        // TODO: Check second edited recording recording was successful
    }

    @Test
    @DisplayName("S28-5018 Another regression test to verify layers of edit requests")
    void editRequestsShouldBeProcessedWhenSuccessiveEditsOverlap(){
        // This is a very tricky set of data where edits were applied to Version 2, which had already been edited.
        // We had to manually calculate the edit instructions.
        // Need a test to capture this so that we can develop the code to implement it.
        // We don't need to fix it at this point, that could be a different ticket
        // can mark the test with a `@wip` tag and then exclude this tag from the ./gradlew functionalTest task

        // Version 1 edits
        // Start time of cut	End time of cut	    Total time removed
        //  00:00:00	        00:00:24	        00:00:24
        //  00:01:22	        00:01:52	        00:00:30
        //  00:09:00	        00:17:21	        00:08:21
        //  00:18:48	        00:21:05	        00:02:17
        //  00:24:12        	00:32:22	        00:08:10
        //  00:33:47	        00:34:29	        00:00:42
        //  00:34:36        	00:35:23	        00:00:47

        // Version 2 edits, requested against edited V2 recording
        // Start time of cut	End time of cut	    Total time removed
        // 00:06:13	            00:14:06	        00:07:53

        // Combined edits
        //  Which edit   	V1 start time of cut	End time of cut (V1)	Total time removed	Notes
        //  1           	00:00:00	            00:00:24	            00:00:24
        //  1           	00:01:22	            00:01:52	            00:00:30
        //  2              	00:07:07	            00:09:00	            00:01:53	still need 06:00 of 2nd edit
        //  1           	00:09:00	            00:17:21		        00:08:21
        //  2           	00:17:21	            00:18:48	            00:01:27	still need 04:33 of 2nd edit
        //  1           	00:18:48	            00:21:05            	00:02:17
        //  2           	00:21:05	            00:24:12	            00:03:07	still need 01:26 of 2nd edit
        //  1           	00:24:12	            00:32:22	            00:08:10
        //  2           	00:32:22	            00:33:47	            00:01:25	still need 00:00:01 of 2nd edit
        //  1           	00:33:47	            00:34:29	            00:00:42
        //  2           	00:34:29	            00:34:30	            00:00:01
        //  1           	00:34:36	            00:35:23	            00:00:47

    }

}
